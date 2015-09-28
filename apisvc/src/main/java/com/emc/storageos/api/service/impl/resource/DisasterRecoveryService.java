/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.db.client.model.uimodels.InitialSetup.COMPLETE;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.CONFIG_ID;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.CONFIG_KIND;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.dr.DRNatCheckParam;
import com.emc.storageos.model.dr.DRNatCheckResponse;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteParam;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.ExcludeLicenseCheck;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.util.SysUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.sys.ClusterInfo;

/**
 * APIs implementation to standby sites lifecycle management such as add-standby, remove-standby, failover, pause
 * resume replication etc. 
 */

@Path("/site")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class DisasterRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryService.class);
    
    private static final String SHORTID_FMT="standby%d";
    private static final int MAX_NUM_OF_STANDBY = 10;

    private InternalApiSignatureKeyGenerator apiSignatureGenerator;
    private SiteMapper siteMapper;
    private SysUtils sysUtils;
    private CoordinatorClient coordinator;
    private DbClient dbClient;
    
    public DisasterRecoveryService() {
        siteMapper = new SiteMapper();
    }

    /**
     * Attach one fresh install site to this primary as standby
     * Or attach a primary site for the local standby site when it's first being added.
     * 
     * @param param site detail information
     * @return site response information
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteRestRep addStandby(SiteAddParam param) {
        log.info("Retrieving standby site config from: {}", param.getVip());
        try {
            VirtualDataCenter vdc = queryLocalVDC();
            List<Site> existingSites = new ArrayList<Site>();
            for (String siteUUID : vdc.getSiteUUIDs()) {
                Site site = coordinator.getTargetInfo(Site.class, siteUUID, Site.CONFIG_KIND);
                existingSites.add(site);
            }
            
            validateAddParam(param, existingSites);

            ViPRCoreClient viprClient = new ViPRCoreClient(param.getVip(), true).withLogin(param.getUsername(),
                    param.getPassword());

            SiteConfigRestRep standbyConfig = viprClient.site().getStandbyConfig();
            precheckForStandbyAttach(standbyConfig);
    
            Site standbySite = new Site();
            standbySite.setCreationTime((new Date()).getTime());
            standbySite.setName(param.getName());
            standbySite.setVdc(vdc.getId());
            standbySite.setVip(param.getVip());
            standbySite.getHostIPv4AddressMap().putAll(new StringMap(standbyConfig.getHostIPv4AddressMap()));
            standbySite.getHostIPv6AddressMap().putAll(new StringMap(standbyConfig.getHostIPv6AddressMap()));
            standbySite.setSecretKey(standbyConfig.getSecretKey());
            standbySite.setUuid(standbyConfig.getUuid());
            String shortId = generateShortId(existingSites);
            standbySite.setStandbyShortId(shortId);
            standbySite.setDescription(param.getDescription());
    
            if (log.isDebugEnabled()) {
                log.debug(standbySite.toString());
            }
            
            vdc.getSiteUUIDs().add(standbySite.getUuid());
            dbClient.persistObject(vdc);
    
            coordinator.addSite(standbyConfig.getUuid());
            
            log.info("Persist standby site to ZK {}", shortId);
            coordinator.setTargetInfo(standbySite);
            
            updateVdcTargetVersion(SiteInfo.RECONFIG_RESTART);
    
            log.info("Updating the primary site info to site: {}", standbyConfig.getUuid());
            SiteConfigParam configParam = new SiteConfigParam();
            SiteParam primarySite = new SiteParam();
            primarySite.setHostIPv4AddressMap(new StringMap(vdc.getHostIPv4AddressesMap()));
            primarySite.setHostIPv6AddressMap(new StringMap(vdc.getHostIPv6AddressesMap()));
            primarySite.setName(param.getName()); // this is the name for the standby site
            primarySite.setSecretKey(vdc.getSecretKey());
            primarySite.setUuid(coordinator.getSiteId());
            primarySite.setVip(vdc.getApiEndpoint());
            configParam.setPrimarySite(primarySite);
            
            List<SiteParam> standbySites = new ArrayList<SiteParam>();
            for (String siteUUID : vdc.getSiteUUIDs()) {
                Site standby = coordinator.getTargetInfo(Site.class, siteUUID, Site.CONFIG_KIND);
                SiteParam standbyParam = new SiteParam();
                siteMapper.map(standby, standbyParam);
                standbySites.add(standbyParam);
            }
            configParam.setStandbySites(standbySites);
            viprClient.site().syncSite(configParam);
            return siteMapper.map(standbySite);
        } catch (Exception e) {
            log.error("Internal error for updating coordinator on standby", e);
            throw APIException.internalServerErrors.addStandbyFailed(e.getMessage());
        }
        
    }

    /**
     * Sync all the site information from the primary site to the new standby
     * The current site will be demoted from primary to standby during the process
     * @param param
     * @return
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ExcludeLicenseCheck
    public Response syncSites(SiteConfigParam configParam) {
        try {
            // Recreate the primary site
            VirtualDataCenter exisingVdc = queryLocalVDC();
            String currentShortId = exisingVdc.getShortId();
            dbClient.markForDeletion(exisingVdc);
            
            SiteParam primary = configParam.getPrimarySite();
            URI vdcId = URIUtil.createId(VirtualDataCenter.class);
            VirtualDataCenter vdc = new VirtualDataCenter();
            vdc.setId(vdcId);
            vdc.setApiEndpoint(primary.getVip());
            vdc.getHostIPv4AddressesMap().putAll(new StringMap(primary.getHostIPv4AddressMap()));
            vdc.getHostIPv6AddressesMap().putAll(new StringMap(primary.getHostIPv6AddressMap()));
            vdc.setSecretKey(primary.getSecretKey());
            vdc.setLocal(true);
            vdc.setShortId(currentShortId);
            int hostCount = primary.getHostIPv4AddressMap().size();
            if (primary.getHostIPv6AddressMap().size() > hostCount) {
                hostCount = primary.getHostIPv6AddressMap().size();
            }
            vdc.setHostCount(hostCount);
            log.info("Persist primary site to DB");
            dbClient.createObject(vdc);
            coordinator.addSite(primary.getUuid());
            coordinator.setPrimarySite(primary.getUuid());
            
            // Add other standby sites
            for (SiteParam standby : configParam.getStandbySites()) {
                Site site = new Site();
                site.setCreationTime((new Date()).getTime());
                siteMapper.map(standby, site);
                site.setVdc(vdcId);
                coordinator.setTargetInfo(site);
                coordinator.addSite(standby.getUuid());
                log.info("Persist standby site {} to DB", standby.getVip());
            }
            
            updateVdcTargetVersionAndDataRevision(SiteInfo.UPDATE_DATA_REVISION);
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Internal error for updating coordinator on standby", e);
            throw APIException.internalServerErrors.configStandbyFailed(e.getMessage());
        }
    }

    /**
     * Get all sites including standby and primary
     * @return site list contains all sites with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteList getSites() {
        log.info("Begin to list all standby sites of local VDC");
        SiteList standbyList = new SiteList();

        VirtualDataCenter vdc = queryLocalVDC();
        for (String uuid : vdc.getSiteUUIDs()) {
            try {
                Site standby = coordinator.getTargetInfo(Site.class, uuid, Site.CONFIG_KIND);
                standbyList.getSites().add(siteMapper.map(standby));
            } catch (Exception e) {
                log.error("Find find site from ZK for UUID {}, {}", uuid, e);
            }
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
    public SiteRestRep getSite(@PathParam("uuid") String uuid) {
        log.info("Begin to get standby site by uuid {}", uuid);
        
        try {
            Site standby = coordinator.getTargetInfo(Site.class, uuid, Site.CONFIG_KIND);
            return siteMapper.map(standby);
        } catch (Exception e) {
            log.error("Find find site from ZK for UUID {}, {}", uuid, e);
        }
        
        log.info("Can't find site with specified site ID {}", uuid);
        return null;
    }

    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}")
    public SiteRestRep removeStandby(@PathParam("uuid") String uuid) {
        log.info("Begin to remove standby site from local vdc by uuid: {}", uuid);
        
        try {
            Site standby = coordinator.getTargetInfo(Site.class, uuid, Site.CONFIG_KIND);
            if (standby != null) {
                log.info("Find standby site in local VDC and remove it");
                
                VirtualDataCenter vdc = queryLocalVDC();
                vdc.getSiteUUIDs().remove(uuid);
                dbClient.persistObject(vdc);
                
                updateVdcTargetVersion(SiteInfo.RECONFIG_RESTART);
                return siteMapper.map(standby);
            }
        } catch (Exception e) {
            log.error("Find find site from ZK for UUID {}, {}", uuid, e);
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
        String siteId = coordinator.getSiteId();
        SiteState siteState = coordinator.getSiteState();
        VirtualDataCenter vdc = queryLocalVDC();
        SecretKey key = apiSignatureGenerator.getSignatureKey(SignatureKeyType.INTERVDC_API);
        
        SiteConfigRestRep siteConfigRestRep = new SiteConfigRestRep();
        siteConfigRestRep.setUuid(siteId);
        siteConfigRestRep.setVip(vdc.getApiEndpoint());
        siteConfigRestRep.setSecretKey(new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8")));
        siteConfigRestRep.setHostIPv4AddressMap(vdc.getHostIPv4AddressesMap());
        siteConfigRestRep.setHostIPv6AddressMap(vdc.getHostIPv6AddressesMap());
        siteConfigRestRep.setDbSchemaVersion(coordinator.getCurrentDbSchemaVersion());
        siteConfigRestRep.setFreshInstallation(isFreshInstallation());
        siteConfigRestRep.setState(siteState.name());
        siteConfigRestRep.setClusterStable(isClusterStable());
        
        try {
            siteConfigRestRep.setSoftwareVersion(coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion().toString());
        } catch (Exception e) {
            log.error("Fail to get software version {}", e);
        }

        log.info("Return result: {}", siteConfigRestRep);
        return siteConfigRestRep;
    }
    
    // TODO: replace the implementation with CoordinatorClientExt#setTargetInfo after the APIs get moved to syssvc
    private void updateVdcTargetVersion(String action) throws Exception {
        SiteInfo siteInfo;
        SiteInfo currentSiteInfo = coordinator.getTargetInfo(SiteInfo.class);
        if (currentSiteInfo != null) {
            siteInfo = new SiteInfo(System.currentTimeMillis(), action, currentSiteInfo.getTargetDataRevision());
        } else {
            siteInfo = new SiteInfo(System.currentTimeMillis(), action);
        }
        coordinator.setTargetInfo(siteInfo);
        log.info("VDC target version updated to {}, action required: {}", siteInfo.getVdcConfigVersion(), action);
        //TODO update SiteInfo for all other standby sites
    }
    
    private void updateVdcTargetVersionAndDataRevision(String action) throws Exception {
        int ver = 1;
        SiteInfo siteInfo = coordinator.getTargetInfo(SiteInfo.class);
        if (siteInfo != null) {
            if (!siteInfo.isNullTargetDataRevision()) {
                String currentDataRevision = siteInfo.getTargetDataRevision();
                ver = Integer.valueOf(currentDataRevision);
            }
        }
        String targetDataRevision = String.valueOf(++ver);
        siteInfo = new SiteInfo(System.currentTimeMillis(), action, targetDataRevision);
        coordinator.setTargetInfo(siteInfo);
        log.info("VDC target version updated to {}, revision {}", 
                siteInfo.getVdcConfigVersion(),  targetDataRevision);
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
    
    /*
     * Internal method to check whether standby can be attached to current primary site
     */
    protected void precheckForStandbyAttach(SiteConfigRestRep standby) {
        try {
            if (!isClusterStable()) {
                throw new Exception("Current site is not stable");
            }

            if (!standby.isClusterStable()) {
                throw new Exception("Remote site is not stable");
            }

            //standby should be refresh install
            if (!standby.isFreshInstallation()) {
                throw new Exception("Standby is not a fresh installation");
            }
            
            //DB schema version should be same
            String currentDbSchemaVersion = coordinator.getCurrentDbSchemaVersion();
            String standbyDbSchemaVersion = standby.getDbSchemaVersion();
            if (!currentDbSchemaVersion.equalsIgnoreCase(standbyDbSchemaVersion)) {
                throw new Exception(String.format("Standby db schema version %s is not same as primary %s", standbyDbSchemaVersion, currentDbSchemaVersion));
            }
            
            //software version should be matched
            SoftwareVersion currentSoftwareVersion;
            SoftwareVersion standbySoftwareVersion;
            try {
                currentSoftwareVersion = coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion();
                standbySoftwareVersion = new SoftwareVersion(standby.getSoftwareVersion());
            } catch (Exception e) {
                throw new Exception(String.format("Fail to get software version %s", e.getMessage()));
            }
            
            if (!isVersionMatchedForStandbyAttach(currentSoftwareVersion,standbySoftwareVersion)) {
                throw new Exception(String.format("Standby site version %s is not equals to current version %s", standbySoftwareVersion, currentSoftwareVersion));
            }
            
            //this site should not be standby site
            String primaryID = coordinator.getPrimarySiteId();
            if (primaryID != null && !primaryID.equals(coordinator.getSiteId())) {
                throw new Exception("This site is also a standby site");
            }
            
            
        } catch (Exception e) {
            log.error("Standby information can't pass pre-check {}", e.getMessage());
            throw APIException.internalServerErrors.addStandbyPrecheckFailed(e.getMessage());
        }
    }
    
    private void validateAddParam(SiteAddParam param, List<Site> existingSites) {
        for (Site site : existingSites) {
            if (site.getName().equals(param.getName())) {
                throw APIException.internalServerErrors.addStandbyPrecheckFailed("Duplicate site name");
            }

            int ipv4Count = site.getHostIPv4AddressMap().size();
            int ipv6Count = site.getHostIPv6AddressMap().size();
            int nodeCount = ipv4Count > 0? ipv4Count : ipv6Count;
            ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid(), nodeCount);
            if (state != ClusterInfo.ClusterState.STABLE) {
                log.info("Site {} is not stable {}", site.getUuid(), state);
                throw APIException.internalServerErrors.addStandbyPrecheckFailed(String.format("Site %s is not stable %s", site.getName(), state));
            }
        }
    }
    
    private String generateShortId(List<Site> existingSites) throws Exception{
        Set<String> existingShortIds = new HashSet<String>();
        for (Site site : existingSites) {
            existingShortIds.add(site.getStandbyShortId());
        }
        
        for (int i = 1; i < MAX_NUM_OF_STANDBY; i ++) {
            String id = String.format(SHORTID_FMT, i);
            if (!existingShortIds.contains(id)) {
                return id;
            }
        }
        throw new Exception("Failed to generate standby short id");
    }

    private boolean isClusterStable() {
        return coordinator.getControlNodesState() == ClusterInfo.ClusterState.STABLE;
    }
    
    protected boolean isFreshInstallation() {
        Configuration setupConfig = coordinator.queryConfiguration(CONFIG_KIND, CONFIG_ID);
        
        boolean freshInstall = (setupConfig == null) || Boolean.parseBoolean(setupConfig.getConfig(COMPLETE)) == false;
        log.info("Fresh installation {}", freshInstall);
        
        boolean hasDataInDB = dbClient.hasUsefulData();
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
        this.dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }
}
