/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.coordinator.client.model.Constants.TARGET_INFO;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.COMPLETE;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.CONFIG_ID;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.CONFIG_KIND;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.dr.DRNatCheckParam;
import com.emc.storageos.model.dr.DRNatCheckResponse;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.model.dr.SiteSyncParam;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.ExcludeLicenseCheck;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.util.SysUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@Path("/site")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class DisasterRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryService.class);
    private InternalApiSignatureKeyGenerator apiSignatureGenerator;
    private SiteMapper siteMapper;
    private SysUtils sysUtils;
    private CoordinatorClient _coordinator;
    private DbClient _dbClient;
    
    public DisasterRecoveryService() {
        siteMapper = new SiteMapper();
    }

    /**
     * Attach one fresh install site to this primary as standby
     * @param param site detail information
     * @return site response information
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteRestRep addStandby(SiteAddParam param) {
        log.info("Begin to add standby site {}", param);
        // TODO call standby site and fetch standby site information
        // Mock up of standby site information
        SiteSyncParam standbyInfo = new SiteSyncParam();
        standbyInfo.setName(param.getName());
        standbyInfo.setVip(param.getVip());
        precheckForStandbyAttach(standbyInfo);
        
        VirtualDataCenter vdc = queryLocalVDC();
        
        Site standbySite = new Site();
        siteMapper.map(standbyInfo, standbySite);

        if (log.isDebugEnabled()) {
            log.debug(standbySite.toString());
        }
        
        vdc.getSiteUUIDs().add(standbySite.getUuid());
        _dbClient.persistObject(vdc);

        log.info("Persist standby site to ZK");
        _coordinator.persistObject(String.format("%1$s/%2$s", ZkPath.SITES, standbySite.getUuid()), standbySite);

        updateVdcTargetVersion();

        return siteMapper.map(standbySite);
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
        
        for (String uuid : vdc.getSiteUUIDs()) {
            Site standby = _coordinator.queryObject(Site.class, String.format("%1$s/%2$s", ZkPath.SITES, uuid));
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
        
        Site standby = _coordinator.queryObject(Site.class, String.format("%1$s/%2$s", ZkPath.SITES, uuid));
        
        log.info("Can't find site with specified site ID {}", uuid);
        return siteMapper.map(standby);
    }

    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}")
    public SiteRestRep removeStandby(@PathParam("uuid") String uuid) {
        log.info("Begin to remove standby site from local vdc by uuid: {}", uuid);
        
        Site standby = _coordinator.queryObject(Site.class, String.format("%1$s/%2$s", ZkPath.SITES, uuid));

        if (standby != null) {
            log.info("Find standby site in local VDC and remove it");
            
            VirtualDataCenter vdc = queryLocalVDC();
            vdc.getSiteUUIDs().remove(uuid);
            _dbClient.persistObject(vdc);
            
            _coordinator.persistObject(String.format("%1$s/%2$s", ZkPath.SITES, uuid), null);
            
            updateVdcTargetVersion();
            return siteMapper.map(standby);
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
    
    /**
     * Add primary site
     * 
     * @param param primary site configuration
     * @return SiteRestRep primary site information
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/internal/standby/config")
    public SiteRestRep addPrimary(SiteSyncParam param) {
        log.info("Begin to add primary site {}", param);

        Site primarySite = toSite(param);
        
        VirtualDataCenter vdc = queryLocalVDC();
        vdc.getSiteUUIDs().add(primarySite.getUuid());
        _dbClient.persistObject(vdc);

        log.info("Persist standby site to ZK");
        _coordinator.persistObject(String.format("%1$s/%2$s", ZkPath.SITES, primarySite.getUuid()), primarySite);

        updateVdcTargetVersion();

        return siteMapper.map(primarySite);
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

    private Site toSite(SiteSyncParam param) {
        Site site = new Site();
        site.setUuid(param.getUuid());
        site.setName(param.getName());
        site.setVip(param.getVip());
        site.setSecretKey(param.getSecretKey());
        site.getHostIPv4AddressMap().putAll(new StringMap(param.getHostIPv4AddressMap()));
        site.getHostIPv6AddressMap().putAll(new StringMap(param.getHostIPv6AddressMap()));
        return site;
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
    
    /*
     * Internal method to check whether standby can be attached to current primary site
     */
    protected void precheckForStandbyAttach(SiteSyncParam standby) {
        try {
            //standby should be refresh install
            if (standby.isFreshInstallation() == false) {
                throw new Exception("Standby is not refresh installation");
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
    
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }
}
