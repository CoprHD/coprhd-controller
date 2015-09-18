/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.dr.*;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.ExcludeLicenseCheck;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.util.SysUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.client.ViPRCoreClient;
import static com.emc.storageos.coordinator.client.model.Constants.TARGET_INFO;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.COMPLETE;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.CONFIG_ID;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.CONFIG_KIND;

@Path("/site")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class DisasterRecoveryService extends TaggedResource {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryService.class);
    private InternalApiSignatureKeyGenerator apiSignatureGenerator;
    private SiteMapper siteMapper;
    private SysUtils sysUtils;
    
    public DisasterRecoveryService() {
        siteMapper = new SiteMapper();
    }

    /**
     * Attach one fresh install site to this primary as standby
     * Or attach a primary site for the local standby site when it's first being added.
     * @param param site detail information
     * @return site response information
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteRestRep addStandby(SiteAddParam param) {
        log.info("Retrieving standby site config from: {}", param.getVip());
        ViPRCoreClient viprClient = new ViPRCoreClient(param.getVip(), true).withLogin(param.getUser(),
                param.getPassword());
        SiteConfigRestRep standbyConfig = viprClient.site().getStandbyConfig();

        precheckForStandbyAttach(standbyConfig);

        VirtualDataCenter vdc = queryLocalVDC();

        Site standbySite = new Site(URIUtil.createId(Site.class));
        standbySite.setName(param.getName());
        standbySite.setVip(param.getVip());
        standbySite.setVdc(vdc.getId());
        standbySite.getHostIPv4AddressMap().putAll(new StringMap(standbyConfig.getHostIPv4AddressMap()));
        standbySite.getHostIPv6AddressMap().putAll(new StringMap(standbyConfig.getHostIPv6AddressMap()));
        standbySite.setSecretKey(standbyConfig.getSecretKey());
        standbySite.setUuid(standbyConfig.getUuid());

        if (log.isDebugEnabled()) {
            log.debug(standbySite.toString());
        }

        log.info("Persist standby site to DB");
        _dbClient.createObject(standbySite);

        try {
            _coordinator.addSite(standbyConfig.getUuid());
        } catch (Exception e) {
            //FIXME: throw custom API exception here
            throw new IllegalStateException(e);
        }

        updateVdcTargetVersion();

        log.info("Updating the primary site info to site: {}", standbyConfig.getUuid());
        SiteSyncParam primarySite = new SiteSyncParam();
        primarySite.setHostIPv4AddressMap(new StringMap(vdc.getHostIPv4AddressesMap()));
        primarySite.setHostIPv6AddressMap(new StringMap(vdc.getHostIPv6AddressesMap()));
        primarySite.setName(param.getName()); // this is the name for the standby site
        primarySite.setSecretKey(vdc.getSecretKey());
        primarySite.setUuid(_coordinator.getSiteId());
        primarySite.setVip(vdc.getApiEndpoint());

        viprClient.site().syncSite(primarySite);

        return siteMapper.map(standbySite);
    }

    /**
     * Sync all the site information from the primary site to the new standby
     * @param param
     * @return
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response SyncSites(SiteSyncParam param) {
        VirtualDataCenter vdc = queryLocalVDC();

        // this is the new standby site demoted from the current site
        Site standbySite = new Site(URIUtil.createId(Site.class));
        standbySite.setUuid(_coordinator.getSiteId());
        standbySite.setName(param.getName());
        standbySite.setVip(vdc.getApiEndpoint());
        standbySite.setVdc(vdc.getId());
        standbySite.getHostIPv4AddressMap().putAll(new StringMap(vdc.getHostIPv4AddressesMap()));
        standbySite.getHostIPv6AddressMap().putAll(new StringMap(vdc.getHostIPv6AddressesMap()));
        standbySite.setSecretKey(vdc.getSecretKey());

        log.info("Persist standby site to DB");
        _dbClient.createObject(standbySite);

        // update the primary site
        vdc.setApiEndpoint(param.getVip());
        vdc.getHostIPv4AddressesMap().putAll(new StringMap(param.getHostIPv4AddressMap()));
        vdc.getHostIPv6AddressesMap().putAll(new StringMap(param.getHostIPv6AddressMap()));
        vdc.setSecretKey(param.getSecretKey());

        int hostCount = param.getHostIPv4AddressMap().size();
        if (param.getHostIPv6AddressMap().size() > hostCount) {
            hostCount = param.getHostIPv6AddressMap().size();
        }
        vdc.setHostCount(hostCount);

        log.info("Persist primary site to DB");
        _dbClient.updateAndReindexObject(vdc);

        try {
            _coordinator.addSite(param.getUuid());
            _coordinator.setPrimarySite(param.getUuid());
        } catch (Exception e) {
            //FIXME: throw custom API exception here
            throw new IllegalStateException(e);
        }

        updateVdcTargetVersion();
        
        return Response.ok().build();
    }

    /**
     * Get all sites including standby and primary
     * @return site list contains all sites with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteList getAllStandby() {
        log.info("Begin to list all standby sites of local VDC");
        SiteList standbyList = new SiteList();
        
        VirtualDataCenter vdc = queryLocalVDC();
        URIQueryResultList standbySiteIds = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVirtualDataCenterSiteConstraint(vdc.getId()),
                standbySiteIds);

        for (URI siteId : standbySiteIds) {
            Site standby = _dbClient.queryObject(Site.class, siteId);
            standbyList.getSites().add(siteMapper.map(standby));
        }
        
        return standbyList;
    }
    
    /**
     * Get specified site according site UUID
     * @param uuid site UUID
     * @return site response with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}")
    public SiteRestRep getStandby(@PathParam("uuid") String uuid) {
        log.info("Begin to get standby site by uuid {}", uuid);
        
        List<URI> ids = _dbClient.queryByType(Site.class, true);

        Iterator<Site> sites = _dbClient.queryIterativeObjects(Site.class, ids);
        while (sites.hasNext()) {
            Site standby = sites.next();
            if (standby.getUuid().equals(uuid)) {
                return siteMapper.map(standby);
            }
        }
        
        log.info("Can't find site with specified site ID {}", uuid);
        return null;
    }

    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}")
    public SiteRestRep removeStandby(@PathParam("uuid") String uuid) {
        log.info("Begin to remove standby site from local vdc by uuid: {}", uuid);
        
        List<URI> ids = _dbClient.queryByType(Site.class, true);

        Iterator<Site> sites = _dbClient.queryIterativeObjects(Site.class, ids);
        while (sites.hasNext()) {
            Site standby = sites.next();
            if (standby.getUuid().equals(uuid)) {
                log.info("Find standby site in local VDC and remove it");
                _dbClient.markForDeletion(standby);
                updateVdcTargetVersion();
                return siteMapper.map(standby);
            }
        }
        
        return null;
    }
    
    /**
     * Get standby site configuration
     * 
     * @return SiteRestRep standby site configuration.
     */
    @GET
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/standby/config")
    public SiteConfigRestRep getStandbyConfig() {
        log.info("Begin to get standby config");
        String siteId = _coordinator.getSiteId();
        SiteState siteState = _coordinator.getSiteState();
        VirtualDataCenter vdc = queryLocalVDC();
        SecretKey key = apiSignatureGenerator.getSignatureKey(SignatureKeyType.INTERVDC_API);
        
        Site localSite = new Site();
        localSite.setUuid(siteId);
        localSite.setVip(vdc.getApiEndpoint());
        localSite.setSecretKey(new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8")));
        localSite.getHostIPv4AddressMap().putAll(vdc.getHostIPv4AddressesMap());
        localSite.getHostIPv6AddressMap().putAll(vdc.getHostIPv6AddressesMap());
        
        SiteConfigRestRep siteConfigRestRep = new SiteConfigRestRep(); 
        siteMapper.map(localSite, siteConfigRestRep);
        
        siteConfigRestRep.setDbSchemaVersion(_coordinator.getCurrentDbSchemaVersion());
        siteConfigRestRep.setFreshInstallation(isFreshInstallation());
        siteConfigRestRep.setState(siteState.name());
        
        try {
            siteConfigRestRep.setSoftwareVersion(_coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion().toString());
        } catch (Exception e) {
            log.error("Fail to get software version {}", e);
        }
        
        log.info("Return result: {}", siteConfigRestRep);
        return siteConfigRestRep;
    }

    // TODO: replace the implementation with CoordinatorClientExt#setTargetInfo after the APIs get moved to syssvc
    private void updateVdcTargetVersion() {
        ConfigurationImpl cfg = new ConfigurationImpl();
        String vdcTargetVersion = String.valueOf(System.currentTimeMillis());
        cfg.setId(SiteInfo.CONFIG_ID);
        cfg.setKind(SiteInfo.CONFIG_KIND);
        cfg.setConfig(TARGET_INFO, vdcTargetVersion);
        _coordinator.persistServiceConfiguration(cfg);
        log.info("VDC target version updated to {}", vdcTargetVersion);
    }
    
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/standby/natcheck")
    @ExcludeLicenseCheck
    public DRNatCheckResponse checkIfBehindNat(DRNatCheckParam checkParam, @HeaderParam("X-Forwarded-For") String clientIp) {
        if (checkParam == null) {
            log.error("checkParam is null, X-Forwarded-For is {}", clientIp);
            throw APIException.internalServerErrors.invalidNatCheckCall("(null)", clientIp);
        }

        String ipv4Str = checkParam.getIPv4Address();
        String ipv6Str = checkParam.getIPv6Address();
        log.info(String.format("Performing NAT check, client address connecting to VIP: %s. Client reports its IPv4 = %s, IPv6 = %s",
                clientIp, ipv4Str, ipv6Str));

        boolean isBehindNat = false;
        try {
            isBehindNat = sysUtils.checkIfBehindNat(ipv4Str, ipv6Str, clientIp);
        } catch (Exception e) {
            log.error("Fail to check NAT {}", e);
            throw APIException.internalServerErrors.invalidNatCheckCall(e.getMessage(), clientIp);
        }

        DRNatCheckResponse resp = new DRNatCheckResponse();
        resp.setSeenIp(clientIp);
        resp.setBehindNAT(isBehindNat);

        return resp;
    }
    
    @Override
    protected Site queryResource(URI id) {
        ArgValidator.checkUri(id);
        Site standby = _dbClient.queryObject(Site.class, id);
        ArgValidator.checkEntityNotNull(standby, id, isIdEmbeddedInURL(id));
        return standby;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    private Set<String> getStandbyIds(Set<String> siteUUIds) {
        Set<String> standbyIds = new HashSet<String>();
        String primarySiteId = _coordinator.getPrimarySiteId();

        for (String siteId : siteUUIds){
            if (siteId != null && !siteId.equals(primarySiteId)) {
                standbyIds.add(siteId);
            }
        }
        return standbyIds;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.SITE;
    }
    
    /*
     * Internal method to check whether standby can be attached to current primary site
     */
    protected void precheckForStandbyAttach(SiteConfigRestRep standby) {
        try {
            //standby should be refresh install
            if (!standby.isFreshInstallation()) {
                throw new Exception("Standby is not a fresh installation");
            }
            
            //DB schema version should be same
            String currentDbSchemaVersion = _coordinator.getCurrentDbSchemaVersion();
            String standbyDbSchemaVersion = standby.getDbSchemaVersion();
            if (!currentDbSchemaVersion.equalsIgnoreCase(standbyDbSchemaVersion)) {
                throw new Exception(String.format("Standby db schema version %s is not same as primary %s", standbyDbSchemaVersion, currentDbSchemaVersion));
            }
            
            //software version should be matched
            SoftwareVersion currentSoftwareVersion;
            SoftwareVersion standbySoftwareVersion;
            try {
                currentSoftwareVersion = _coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion();
                standbySoftwareVersion = new SoftwareVersion(standby.getSoftwareVersion());
            } catch (Exception e) {
                throw new Exception(String.format("Fail to get software version %s", e.getMessage()));
            }
            
            if (!isVersionMatchedForStandbyAttach(currentSoftwareVersion,standbySoftwareVersion)) {
                throw new Exception(String.format("Standby site version %s is not equals to current version %s", standbySoftwareVersion, currentSoftwareVersion));
            }
            
            //this site should not be standby site
            String primaryID = _coordinator.getPrimarySiteId();
            if (primaryID != null && !primaryID.equals(_coordinator.getSiteId())) {
                throw new Exception("This site is also a standby site");
            }
        } catch (Exception e) {
            log.error("Standby information can't pass pre-check {}", e.getMessage());
            throw APIException.internalServerErrors.addStandbyPrecheckFailed(e.getMessage());
        }
        
    }

    protected boolean isFreshInstallation() {
        Configuration setupConfig = _coordinator.queryConfiguration(CONFIG_KIND, CONFIG_ID);
        
        boolean freshInstall = (setupConfig == null) || Boolean.parseBoolean(setupConfig.getConfig(COMPLETE)) == false;
        log.info("Fresh installation {}", freshInstall);
        
        boolean hasDataInDB = _dbClient.hasUsefulData();
        log.info("Has useful data in DB {}", hasDataInDB);
        
        return freshInstall && !hasDataInDB;
    }
    
    protected boolean isVersionMatchedForStandbyAttach(SoftwareVersion currentSoftwareVersion, SoftwareVersion standbySoftwareVersion) {
        if (currentSoftwareVersion == null || standbySoftwareVersion == null) {
            return false;
        }
        
        String versionString = standbySoftwareVersion.toString();
        SoftwareVersion standbyVersionWildcard = new SoftwareVersion(versionString.substring(0, versionString.lastIndexOf("."))+".*");
        return currentSoftwareVersion.weakEquals(standbyVersionWildcard);
    }
    
    // encapsulate the get local VDC operation for easy UT writing because VDCUtil.getLocalVdc is static method
    protected VirtualDataCenter queryLocalVDC() {
        return VdcUtil.getLocalVdc();
    }

    public InternalApiSignatureKeyGenerator getApiSignatureGenerator() {
        return apiSignatureGenerator;
    }

    public void setApiSignatureGenerator(InternalApiSignatureKeyGenerator apiSignatureGenerator) {
        this.apiSignatureGenerator = apiSignatureGenerator;
    }
    
    public void setSiteMapper(SiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }

    public void setSysUtils(SysUtils sysUtils) {
        this.sysUtils = sysUtils;
    }
}
