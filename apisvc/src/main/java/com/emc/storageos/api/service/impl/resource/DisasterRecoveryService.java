/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.jsoup.helper.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.api.service.impl.resource.utils.InternalSiteServiceClient;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteError;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.uimodels.InitialSetup;
import com.emc.storageos.model.dr.DRNatCheckParam;
import com.emc.storageos.model.dr.DRNatCheckResponse;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.model.dr.SiteIdListParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteParam;
import com.emc.storageos.model.dr.SiteActive;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.model.dr.SiteUpdateParam;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.ExcludeLicenseCheck;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.SysUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.model.sys.ClusterInfo;

/**
 * APIs implementation to standby sites lifecycle management such as add-standby, remove-standby, failover, pause
 * resume replication etc.
 */
@Path("/site")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
        Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
public class DisasterRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryService.class);

    private static final String SHORTID_FMT = "standby%d";
    private static final int MAX_NUM_OF_STANDBY = 10;
    private static final String EVENT_SERVICE_TYPE = "DisasterRecovery";
    private static final String DR_OPERATION_LOCK = "droperation";
    private static final int LOCK_WAIT_TIME_SEC = 5;
    private static final int DEFAULT_GC_GRACE_PERIOD = 5 * 24 * 60 * 60; // 5 days;

    private InternalApiSignatureKeyGenerator apiSignatureGenerator;
    private SiteMapper siteMapper;
    private SysUtils sysUtils;
    private CoordinatorClient coordinator;
    private DbClient dbClient;
    private IPsecConfig ipsecConfig;
    private Properties _dbCommonInfo;
    private DrUtil drUtil;

    private InternalSiteServiceClient internalSiteServiceClient;

    @Autowired
    private AuditLogManager auditMgr;

    /**
     * Record audit log for DisasterRecoveryService
     *
     * @param auditType
     * @param operationalStatus
     * @param operationStage
     * @param descparams
     */
    protected void auditDisasterRecoveryOps(OperationTypeEnum auditType,
            String operationalStatus,
            String operationStage,
            Object... descparams) {
        auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus,
                operationStage,
                descparams);
    }

    public DisasterRecoveryService() {
        siteMapper = new SiteMapper();
    }

    /**
     * Attach one fresh install site to this acitve site as standby
     * Or attach a acitve site for the local standby site when it's first being added.
     * 
     * @param param site detail information
     * @return site response information
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public SiteRestRep addStandby(SiteAddParam param) {
        log.info("Retrieving standby site config from: {}", param.getVip());

        List<Site> existingSites = drUtil.listStandbySites();

        // parameter validation and precheck
        validateAddParam(param, existingSites);
        precheckStandbyVersion(param);

        ViPRCoreClient viprCoreClient;
        SiteConfigRestRep standbyConfig;
        try {
            viprCoreClient = createViPRCoreClient(param.getVip(), param.getUsername(), param.getPassword());
            standbyConfig = viprCoreClient.site().getStandbyConfig();
        } catch (Exception e) {
            log.error("Unexpected error when retrieving standby config", e);
            throw APIException.internalServerErrors.addStandbyPrecheckFailed("Cannot retrieve config from standby site");
        }

        String siteId = standbyConfig.getUuid();
        precheckForStandbyAttach(standbyConfig);

        InterProcessLock lock = getDROperationLock();

        try {
            Site standbySite = new Site();
            standbySite.setCreationTime((new Date()).getTime());
            standbySite.setName(param.getName());
            standbySite.setVdcShortId(drUtil.getLocalVdcShortId());
            standbySite.setVip(param.getVip());
            standbySite.getHostIPv4AddressMap().putAll(new StringMap(standbyConfig.getHostIPv4AddressMap()));
            standbySite.getHostIPv6AddressMap().putAll(new StringMap(standbyConfig.getHostIPv6AddressMap()));
            standbySite.setNodeCount(standbyConfig.getNodeCount());
            standbySite.setSecretKey(standbyConfig.getSecretKey());
            standbySite.setUuid(standbyConfig.getUuid());
            String shortId = generateShortId(drUtil.listSites());
            standbySite.setStandbyShortId(shortId);
            standbySite.setDescription(param.getDescription());
            standbySite.setState(SiteState.STANDBY_ADDING);
            if (log.isDebugEnabled()) {
                log.debug(standbySite.toString());
            }
            coordinator.addSite(standbyConfig.getUuid());
            log.info("Persist standby site to ZK {}", shortId);
            // coordinator.setTargetInfo(standbySite);
            coordinator.persistServiceConfiguration(standbySite.toConfiguration());

            // wake up syssvc to regenerate configurations
            drUtil.updateVdcTargetVersion(coordinator.getSiteId(), SiteInfo.DR_OP_ADD_STANDBY);
            for (Site site : existingSites) {
                drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.DR_OP_ADD_STANDBY);
            }

            // sync site related info with to be added standby site
            long dataRevision = System.currentTimeMillis();
            SiteConfigParam configParam = prepareSiteConfigParam(ipsecConfig.getPreSharedKey(), standbyConfig.getUuid(), dataRevision);
            viprCoreClient.site().syncSite(standbyConfig.getUuid(), configParam);

            drUtil.updateVdcTargetVersion(siteId, SiteInfo.DR_OP_CHANGE_DATA_REVISION, dataRevision);

            auditDisasterRecoveryOps(OperationTypeEnum.ADD_STANDBY, AuditLogManager.AUDITLOG_SUCCESS, null,
                    param.getVip(), param.getName());
            return siteMapper.map(standbySite);
        } catch (Exception e) {
            log.error("Internal error for updating coordinator on standby", e);
            auditDisasterRecoveryOps(OperationTypeEnum.ADD_STANDBY, AuditLogManager.AUDITLOG_FAILURE, null,
                    param.getVip(), param.getName());
            InternalServerErrorException addStandbyFailedException = APIException.internalServerErrors.addStandbyFailed(e.getMessage());
            setSiteError(siteId, addStandbyFailedException);
            throw addStandbyFailedException;
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when adding standby %s", siteId));
            }
        }
    }

    /**
     * Prepare all sites related info for synchronizing them from master to be added or resumed standby site
     *
     * @param ipsecKey The cluster ipsec key
     * @param targetStandbyUUID The uuid of the target standby
     * @param targetStandbyDataRevision The data revision of the target standby
     * @return SiteConfigParam all the sites configuration
     */
    private SiteConfigParam prepareSiteConfigParam(String ipsecKey, String targetStandbyUUID, long targetStandbyDataRevision) {
        log.info("Preparing to sync sites info among to be added/resumed standby site...");
        Site active = drUtil.getSiteFromLocalVdc(drUtil.getActiveSiteId());
        SiteConfigParam configParam = new SiteConfigParam();
        SiteParam activeSite = new SiteParam();
        siteMapper.map(active, activeSite);
        activeSite.setIpsecKey(ipsecKey);
        log.info("    active site info:{}", activeSite.toString());
        configParam.setActiveSite(activeSite);

        List<SiteParam> standbySites = new ArrayList<>();
        for (Site standby : drUtil.listStandbySites()) {
            SiteParam standbyParam = new SiteParam();
            siteMapper.map(standby, standbyParam);
            if (standby.getUuid().equals(targetStandbyUUID)) {
                log.info("Set data revision for site {} to {}", standby.getUuid(), targetStandbyDataRevision);
                standbyParam.setDataRevision(targetStandbyDataRevision);
            }
            standbySites.add(standbyParam);
            log.info("    standby site info:{}", standbyParam.toString());
        }
        configParam.setStandbySites(standbySites);

        return configParam;
    }

    /**
     * Initialize a to be added target standby
     * The current site will be demoted from active to standby during the process
     *
     * @param configParam
     * @return
     */
    @PUT
    @Path("/{uuid}/initstandby")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @ExcludeLicenseCheck
    public Response syncSites(SiteConfigParam configParam) {
        log.info("sync sites from acitve site");

        return initStandby(configParam);
    }

    /**
     * Initialize a to-be added/resumed target standby
     *  a) re-set all the latest site related info (persisted in ZK) in the target standby
     *  b) vdc properties would be changed accordingly
     *  c) the target standby reboot
     *  d) re-set zk/db data during the target standby reboot
     *  e) the target standby would connect with active and sync all the latest ZK&DB data.
     *
     * Scenarios:
     *  a) For adding standby site scenario (External API), the current site will be demoted from active to standby during the process
     *  b) For resuming standby site scenario (Internal API), the current site's original data will be cleaned by setting new data revision.
     *     It is now only used for resuming long paused (> 5 days) standby site
     * @param configParam
     * @return
     */
    @PUT
    @Path("/internal/initstandby")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response initStandby(SiteConfigParam configParam) {
        try {
            SiteParam activeSiteParam = configParam.getActiveSite();

            ipsecConfig.setPreSharedKey(activeSiteParam.getIpsecKey());

            coordinator.addSite(activeSiteParam.getUuid());
            coordinator.setActiveSite(activeSiteParam.getUuid());
            Site activeSite = new Site();
            siteMapper.map(activeSiteParam, activeSite);
            activeSite.setVdcShortId(drUtil.getLocalVdcShortId());
            coordinator.persistServiceConfiguration(activeSite.toConfiguration());
            
            Long dataRevision = null;
            // Add other standby sites
            for (SiteParam standby : configParam.getStandbySites()) {
                Site site = new Site();
                siteMapper.map(standby, site);
                site.setVdcShortId(drUtil.getLocalVdcShortId());
                coordinator.persistServiceConfiguration(site.toConfiguration());
                coordinator.addSite(standby.getUuid());
                if (standby.getUuid().equals(coordinator.getSiteId())) {
                    dataRevision = standby.getDataRevision();
                    log.info("Set data revision to {}", dataRevision);
                }
                log.info("Persist standby site {} to ZK", standby.getVip());
            }
            
            if (dataRevision == null) {
                throw new IllegalStateException("Illegal request on standby site. No data revision in request");
            }
            drUtil.updateVdcTargetVersion(coordinator.getSiteId(), SiteInfo.DR_OP_CHANGE_DATA_REVISION, dataRevision);
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Internal error for updating coordinator on standby", e);
            throw APIException.internalServerErrors.configStandbyFailed(e.getMessage());
        }
    }

    /**
     * Get all sites including standby and acitve
     * 
     * @return site list contains all sites with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN,
            Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public SiteList getSites() {
        log.info("Begin to list all standby sites of local VDC");
        SiteList standbyList = new SiteList();

        for (Site site : drUtil.listSites()) {
            standbyList.getSites().add(siteMapper.map(site));
        }
        return standbyList;
    }

    /**
     * Check if current site is acitve site
     * 
     * @return SiteActive true if current site is acitve else false 
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/active")
    public SiteActive checkIsActive() {
        log.info("Begin to check if site Active or Standby");
        SiteActive isActiveSite = new SiteActive();

        try {
            isActiveSite.setIsActive(drUtil.isActiveSite());
            return isActiveSite;
        } catch (Exception e) {
            log.error("Can't get site is Active or Standby");
            throw APIException.badRequests.siteIdNotFound();
        }
    }

    /**
     * Get specified site according site UUID
     * 
     * @param uuid site UUID
     * @return site response with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN,
            Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{uuid}")
    public SiteRestRep getSite(@PathParam("uuid") String uuid) {
        log.info("Begin to get standby site by uuid {}", uuid);

        try {
            Site site = drUtil.getSiteFromLocalVdc(uuid);
            return siteMapper.map(site);
        } catch (Exception e) {
            log.error("Can't find site with specified site ID {}", uuid);
            throw APIException.badRequests.siteIdNotFound();
        }
    }

    /**
     * Remove a standby. After successfully done, it stops data replication to this site
     * 
     * @param uuid standby site uuid
     * @return
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Path("/{uuid}")
    public Response remove(@PathParam("uuid") String uuid) {
        SiteIdListParam param = new SiteIdListParam();
        param.getIds().add(uuid);
        return remove(param);
    }

    /**
     * Remove multiple standby sites. After successfully done, it stops data replication to those sites
     * 
     * @param idList site uuid list to be removed
     * @return
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Path("/remove")
    public Response remove(SiteIdListParam idList) {
        List<String> siteIdList = idList.getIds();
        String siteIdStr = StringUtils.join(siteIdList, ",");
        log.info("Begin to remove standby site from local vdc by uuid: {}", siteIdStr);
        List<Site> toBeRemovedSites = new ArrayList<>();
        for (String siteId : siteIdList) {
            Site site;
            try {
                site = drUtil.getSiteFromLocalVdc(siteId);
            } catch (Exception ex) {
                log.error("Can't load site {} from ZK", siteId);
                throw APIException.badRequests.siteIdNotFound();
            }
            if (site.getState().equals(SiteState.ACTIVE)) {
                log.error("Unable to remove this site {}. It is acitve", siteId);
                throw APIException.badRequests.operationNotAllowedOnActiveSite();
            }
            toBeRemovedSites.add(site);
        }

        // Build a site names' string for more human-readable Exception error message
        StringBuilder siteNamesSb = new StringBuilder();
        for (Site site : toBeRemovedSites) {
            if (siteNamesSb.length() != 0) {
                siteNamesSb.append(", ");
            }
            siteNamesSb.append(site.getName());
        }
        String SiteNamesStr = siteNamesSb.toString();

        try {
            commonPrecheck(siteIdList);
        } catch (IllegalStateException e) {
            throw APIException.internalServerErrors.removeStandbyPrecheckFailed(SiteNamesStr, e.getMessage());
        }

        InterProcessLock lock = getDROperationLock();

        try {
            log.info("Removing sites");
            for (Site site : toBeRemovedSites) {
                site.setState(SiteState.STANDBY_REMOVING);
                coordinator.persistServiceConfiguration(site.toConfiguration());
            }
            log.info("Notify all sites for reconfig");
            for (Site standbySite : drUtil.listSites()) {
                drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_REMOVE_STANDBY);
            }
            auditDisasterRecoveryOps(OperationTypeEnum.REMOVE_STANDBY, AuditLogManager.AUDITLOG_SUCCESS, null, siteIdStr);
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Failed to remove site {}", siteIdStr, e);
            auditDisasterRecoveryOps(OperationTypeEnum.REMOVE_STANDBY, AuditLogManager.AUDITLOG_FAILURE, null, siteIdStr);
            throw APIException.internalServerErrors.removeStandbyFailed(SiteNamesStr, e.getMessage());
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when removing standby sites: %s", siteIdStr));
            }
        }
    }

    /**
     * Get standby site configuration
     * 
     * @return SiteConfigRestRep standby site configuration.
     */
    @GET
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN,
            Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/localconfig")
    public SiteConfigRestRep getStandbyConfig() {
        log.info("Begin to get standby config");
        String siteId = coordinator.getSiteId();
        SecretKey key = apiSignatureGenerator.getSignatureKey(SignatureKeyType.INTERVDC_API);

        Site site = drUtil.getSiteFromLocalVdc(siteId);
        SiteConfigRestRep siteConfigRestRep = new SiteConfigRestRep();
        siteConfigRestRep.setUuid(siteId);
        siteConfigRestRep.setVip(site.getVip());
        siteConfigRestRep.setSecretKey(new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8")));
        siteConfigRestRep.setHostIPv4AddressMap(site.getHostIPv4AddressMap());
        siteConfigRestRep.setHostIPv6AddressMap(site.getHostIPv6AddressMap());
        siteConfigRestRep.setDbSchemaVersion(coordinator.getCurrentDbSchemaVersion());
        siteConfigRestRep.setFreshInstallation(isFreshInstallation());
        siteConfigRestRep.setClusterStable(isClusterStable());
        siteConfigRestRep.setNodeCount(site.getNodeCount());
        siteConfigRestRep.setState(site.getState().toString());

        try {
            siteConfigRestRep.setSoftwareVersion(coordinator.getTargetInfo(RepositoryInfo.class)
                    .getCurrentVersion().toString());
        } catch (Exception e) {
            log.error("Fail to get software version {}", e);
        }

        log.info("Return result: {}", siteConfigRestRep);
        return siteConfigRestRep;
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Path("/natcheck")
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

    /**
     * Pause a standby site that is already sync'ed with the active
     * 
     * @param uuid site UUID
     * @return updated standby site representation
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Path("/{uuid}/pause")
    public Response pauseStandby(@PathParam("uuid") String uuid) {
        SiteIdListParam param = new SiteIdListParam();
        param.getIds().add(uuid);
        return pause(param);
    }

    /**
     * Pause data replication to multiple standby sites.
     *
     * @param idList site uuid list to be removed
     * @return
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Path("/pause")
    public Response pause(SiteIdListParam idList) {
        List<String> siteIdList = idList.getIds();
        String siteIdStr = StringUtils.join(siteIdList, ",");
        log.info("Begin to pause standby site from local vdc by uuid: {}", siteIdStr);
        List<Site> toBePausedSites = new ArrayList<>();
        for (String siteId : siteIdList) {
            Site site;
            try {
                site = drUtil.getSiteFromLocalVdc(siteId);
            } catch (Exception ex) {
                log.error("Can't load site {} from ZK", siteId);
                throw APIException.badRequests.siteIdNotFound();
            }
            SiteState state = site.getState();
            if (state.equals(SiteState.ACTIVE)) {
                log.error("Unable to pause this site {}. It is acitve", siteId);
                throw APIException.badRequests.operationNotAllowedOnActiveSite();
            }
            if (!state.equals(SiteState.STANDBY_SYNCED)) {
                log.error("Unable to pause this site {}. It is in state {}", siteId, state);
                throw APIException.badRequests.operationOnlyAllowedOnSyncedSite(siteId, state.toString());
            }
            toBePausedSites.add(site);
        }

        try {
            commonPrecheck(siteIdList);
        } catch (IllegalStateException e) {
            throw APIException.internalServerErrors.pauseStandbyPrecheckFailed(siteIdStr, e.getMessage());
        }

        InterProcessLock lock = getDROperationLock();

        // any error is not retry-able beyond this point.
        try {
            log.info("Pausing sites");
            for (Site site : toBePausedSites) {
                site.setState(SiteState.STANDBY_PAUSING);
                site.setPausedTime(System.currentTimeMillis());
                coordinator.persistServiceConfiguration(site.toConfiguration());
                // notify the to-be-paused sites before others.
                drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.DR_OP_PAUSE_STANDBY);
            }
            log.info("Notify all sites for reconfig");
            for (Site site : drUtil.listSites()) {
                if (toBePausedSites.contains(site)) { // Site#equals only compares the site uuid
                    // already notified
                    continue;
                }
                drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.DR_OP_PAUSE_STANDBY);
            }
            auditDisasterRecoveryOps(OperationTypeEnum.PAUSE_STANDBY, AuditLogManager.AUDITLOG_SUCCESS, null, siteIdStr);
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Failed to pause site {}", siteIdStr, e);
            auditDisasterRecoveryOps(OperationTypeEnum.PAUSE_STANDBY, AuditLogManager.AUDITLOG_FAILURE, null, siteIdStr);
            throw APIException.internalServerErrors.pauseStandbyFailed(siteIdStr, e.getMessage());
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when pausing standby site: %s", siteIdStr));
            }
        }
    }

    /**
     * Resume data replication for a paused standby site
     * 
     * @param uuid site UUID
     * @return updated standby site representation
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Path("/{uuid}/resume")
    public SiteRestRep resumeStandby(@PathParam("uuid") String uuid) {
        log.info("Begin to resume data sync to standby site identified by uuid: {}", uuid);
        Site standby = validateSiteConfig(uuid);
        if (!standby.getState().equals(SiteState.STANDBY_PAUSED)) {
            log.error("site {} is in state {}, should be STANDBY_PAUSED", uuid, standby.getState());
            throw APIException.badRequests.operationOnlyAllowedOnPausedSite(standby.getName(), standby.getState().toString());
        }

        try {
            commonPrecheck(uuid);
        } catch (IllegalStateException e) {
            throw APIException.internalServerErrors.resumeStandbyPrecheckFailed(uuid, e.getMessage());
        }

        InterProcessLock lock = getDROperationLock();

        try {
            standby.setState(SiteState.STANDBY_RESUMING);
            coordinator.persistServiceConfiguration(standby.toConfiguration());

            for (Site site : drUtil.listStandbySites()) {
                if (site.getUuid().equals(uuid)) {
                    int gcGracePeriod = DEFAULT_GC_GRACE_PERIOD;
                    String strVal = _dbCommonInfo.getProperty(DbClientImpl.DB_CASSANDRA_INDEX_GC_GRACE_PERIOD);
                    if (strVal != null) {
                        gcGracePeriod = Integer.parseInt(strVal);
                    }
                    if ((System.currentTimeMillis() - site.getPausedTime())/1000 >= gcGracePeriod) {
                        log.error("site {} has been paused for too long, we will re-init the target standby", uuid);

                        // init the to-be resumed standby site
                        long dataRevision = System.currentTimeMillis();
                        SiteConfigParam configParam = prepareSiteConfigParam(ipsecConfig.getPreSharedKey(), uuid, dataRevision);
                        internalSiteServiceClient = new InternalSiteServiceClient();
                        internalSiteServiceClient.setCoordinatorClient(coordinator);
                        internalSiteServiceClient.setServer(site.getVip());
                        internalSiteServiceClient.initStandby(configParam);

                        drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.DR_OP_CHANGE_DATA_REVISION, dataRevision);
                    } else {
                        drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.DR_OP_RESUME_STANDBY);
                    }
                } else {
                    drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.DR_OP_RESUME_STANDBY);
                }
            }

            // update the local(acitve) site last
            drUtil.updateVdcTargetVersion(coordinator.getSiteId(), SiteInfo.DR_OP_RESUME_STANDBY);

            auditDisasterRecoveryOps(OperationTypeEnum.RESUME_STANDBY, AuditLogManager.AUDITLOG_SUCCESS, null, uuid);

            return siteMapper.map(standby);
        } catch (Exception e) {
            log.error("Error resuming site {}", uuid, e);
            auditDisasterRecoveryOps(OperationTypeEnum.RESUME_STANDBY, AuditLogManager.AUDITLOG_FAILURE, null, uuid);
            InternalServerErrorException resumeStandbyFailedException =
                    APIException.internalServerErrors.resumeStandbyFailed(standby.getName(), e.getMessage());
            setSiteError(uuid, resumeStandbyFailedException);
            throw resumeStandbyFailedException;
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when resuming standby site: %s", uuid));
            }
        }
    }

    /**
     * Query the latest error message for specific standby site
     * 
     * @param uuid site UUID
     * @return site response with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN,
            Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{uuid}/error")
    public SiteErrorResponse getSiteError(@PathParam("uuid") String uuid) {
        log.info("Begin to get site error by uuid {}", uuid);

        try {
            Site standby = drUtil.getSiteFromLocalVdc(uuid);

            if (standby.getState().equals(SiteState.STANDBY_ERROR)) {
                return coordinator.getTargetInfo(uuid, SiteError.class).toResponse();
            }
        } catch (CoordinatorException e) {
            log.error("Can't find site {} from ZK", uuid);
            throw APIException.badRequests.siteIdNotFound();
        } catch (Exception e) {
            log.error("Find find site from ZK for UUID {} : {}" + uuid, e);
        }

        return SiteErrorResponse.noError();
    }

    /**
     * This API will do switchover to target new acitve site according passed in site UUID. After failover, old acitve site will
     * work as normal standby site and target site will be promoted to acitve. All site will update properties to trigger reconfig.
     * 
     * @param uuid target new acitve site UUID
     * @return return accepted response if operation is successful
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}/switchover")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public Response doSwitchover(@PathParam("uuid") String uuid) {
        log.info("Begin to switchover for standby UUID {}", uuid);

        precheckForSwitchover(uuid);

        String oldActiveUUID = drUtil.getActiveSiteId();

        InterProcessLock lock = getDROperationLock();

        Site newActiveSite = null;
        Site oldActiveSite = null;
        try {
            newActiveSite = drUtil.getSiteFromLocalVdc(uuid);

            // Set new UUID as active site ID
            coordinator.setActiveSite(uuid);

            // Set old active site's state, short id and key
            oldActiveSite = drUtil.getSiteFromLocalVdc(oldActiveUUID);
            if (StringUtils.isEmpty(oldActiveSite.getStandbyShortId())) {
                oldActiveSite.setStandbyShortId(newActiveSite.getVdcShortId());
            }
            oldActiveSite.setState(SiteState.ACTIVE_SWITCHING_OVER);
            coordinator.persistServiceConfiguration(oldActiveSite.toConfiguration());

            // set new acitve site to ZK
            newActiveSite.setState(SiteState.STANDBY_SWITCHING_OVER);
            coordinator.persistServiceConfiguration(newActiveSite.toConfiguration());

            // trigger reconfig
            for (Site eachSite : drUtil.listSites()) {
                drUtil.updateVdcTargetVersion(eachSite.getUuid(), SiteInfo.DR_OP_SWITCHOVER);
            }

            auditDisasterRecoveryOps(OperationTypeEnum.SWITCHOVER, AuditLogManager.AUDITLOG_SUCCESS, null, newActiveSite.getVip(),
                    newActiveSite.getName());
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error(String.format("Error happened when switchover from site %s to site %s", oldActiveUUID, uuid), e);
            auditDisasterRecoveryOps(OperationTypeEnum.SWITCHOVER, AuditLogManager.AUDITLOG_FAILURE, null, newActiveSite.getVip(),
                    newActiveSite.getName());
            throw APIException.internalServerErrors.switchoverFailed(oldActiveSite.getName(), newActiveSite.getName(), e.getMessage());
        } finally {
            try {
                lock.release();
            } catch (Exception ignore) {
                log.error(String.format("Lock release failed when switchover from %s to %s", oldActiveUUID, uuid));
            }
        }
    }

    /**
     * This API will do failover from standby site. This operation is only allowed when acitve site is down.
     * After failover, this standby site will be promoted to acitve site.
     * 
     * @param uuid target new acitve site UUID
     * @return return accepted response if operation is successful
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}/failover")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public Response doFailover(@PathParam("uuid") String uuid) {
        log.info("Begin to failover for standby UUID {}", uuid);

        precheckForFailoverLocally(uuid);

        Map<String, InternalSiteServiceClient> clientCacheMap = new HashMap<String, InternalSiteServiceClient>();
        Site currentSite = drUtil.getSiteFromLocalVdc(uuid);
        
        List<Site> allStandbySites = drUtil.listStandbySites();
        
        for (Site site : allStandbySites) {
            if (!site.getUuid().equals(uuid)) {
                InternalSiteServiceClient client = new InternalSiteServiceClient(site);
                client.setCoordinatorClient(coordinator);
                client.setKeyGenerator(apiSignatureGenerator);
                client.failoverPrecheck();
                clientCacheMap.put(site.getUuid(), client);
            }
        }
        
        try {

            // set state
            Site oldActiveSite = drUtil.getSiteFromLocalVdc(drUtil.getActiveSiteId());
            oldActiveSite.setState(SiteState.ACTIVE_FAILING_OVER);
            coordinator.persistServiceConfiguration(oldActiveSite.toConfiguration());

            currentSite.setState(SiteState.STANDBY_FAILING_OVER);
            coordinator.persistServiceConfiguration(currentSite.toConfiguration());

            // set new acitve uuid
            coordinator.setActiveSite(uuid);

            //reconfig other standby sites
            for (Site site : allStandbySites) {
                if (!site.getUuid().equals(uuid)) {
                    InternalSiteServiceClient client = clientCacheMap.get(site.getUuid());
                    client.failover(uuid);
                }
            }

            drUtil.updateVdcTargetVersion(uuid, SiteInfo.DR_OP_FAILOVER);

            auditDisasterRecoveryOps(OperationTypeEnum.FAILOVER, AuditLogManager.AUDITLOG_SUCCESS, null, uuid, currentSite.getVip(),
                    currentSite.getName());

            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Error happened when failover at site %s", uuid, e);
            auditDisasterRecoveryOps(OperationTypeEnum.FAILOVER, AuditLogManager.AUDITLOG_FAILURE, null, uuid, currentSite.getVip(),
                    currentSite.getName());
            throw APIException.internalServerErrors.failoverFailed(uuid, e.getMessage());
        }
    }
    
    /**
     * This is internal API to do precheck for failover
     * 
     * @return return response with error message and service code
     */
    @POST
    @Path("/internal/failoverprecheck")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteErrorResponse failoverPrecheck() {
        log.info("Precheck for failover internally");
        
        SiteErrorResponse response = new SiteErrorResponse();
        try {
            precheckForFailover();
        } catch (InternalServerErrorException e) {
            log.warn("Failed to precheck failover", e);
            response.setErrorMessage(e.getMessage());
            response.setServiceCode(e.getServiceCode().ordinal());
            return response;
        } catch (Exception e) {
            log.error("Failed to precheck failover", e);
            response.setErrorMessage(e.getMessage());
            return response;
        }
        
        return SiteErrorResponse.noError();
    }
    
    /**
     * This is internal API to do failover
     * 
     * @return return response with error message and service code
     */
    @POST
    @Path("/internal/failover")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response failover(@QueryParam("newActiveSiteUUid") String newActiveSiteUUID) {
        log.info("Begin to failover internally with newActiveSiteUUid {}", newActiveSiteUUID);
        
        Site currentSite = drUtil.getLocalSite();
        String uuid = currentSite.getUuid();
        
        try {
            //set state
            Site oldActiveSite = drUtil.getSiteFromLocalVdc(drUtil.getActiveSiteId());
            coordinator.removeServiceConfiguration(oldActiveSite.toConfiguration());
            
            Site newActiveSite = drUtil.getSiteFromLocalVdc(newActiveSiteUUID);
            newActiveSite.setState(SiteState.STANDBY_FAILING_OVER);
            coordinator.persistServiceConfiguration(newActiveSite.toString());
            
            coordinator.setActiveSite(newActiveSiteUUID);
            
            drUtil.updateVdcTargetVersion(currentSite.getUuid(), SiteInfo.DR_OP_FAILOVER);
            
            auditDisasterRecoveryOps(OperationTypeEnum.FAILOVER, AuditLogManager.AUDITLOG_SUCCESS, null, uuid, currentSite.getVip(), currentSite.getName());
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Error happened when failover at site %s", uuid, e);
            auditDisasterRecoveryOps(OperationTypeEnum.FAILOVER, AuditLogManager.AUDITLOG_FAILURE, null, uuid, currentSite.getVip(), currentSite.getName());
            throw APIException.internalServerErrors.failoverFailed(currentSite.getName(), e.getMessage());
        }
    }
    
    /**
     * Update site information. Only name and description can be updated.
     * @param uuid target site uuid
     * @param siteParam site information
     * @return
     */
    @PUT
    @Path("/{uuid}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public Response updateSite(@PathParam("uuid") String uuid, SiteUpdateParam siteParam) {
        log.info("Begin to update site information for {}", uuid);
        
        Site site = null;
        
        try {
            site = drUtil.getSiteFromLocalVdc(uuid);
        } catch (RetryableCoordinatorException e) {
            log.error("Can't find site with specified site UUID {}", uuid);
            throw APIException.badRequests.siteIdNotFound();
        }
        
        if (StringUtil.isBlank(siteParam.getName())) {
            throw APIException.internalServerErrors.updateSiteFailed(site.getName(), "Site name should not be empty.");
        }
        
        for (Site eachSite : drUtil.listSites()) {
            if (eachSite.getUuid().equals(uuid)) {
                continue;
            }
            
            if (eachSite.getName().equals(siteParam.getName())) {
                throw APIException.internalServerErrors.addStandbyPrecheckFailed("Duplicate site name");
            }
        }
        
        try {
            site.setName(siteParam.getName());
            site.setDescription(siteParam.getDescription());
            coordinator.persistServiceConfiguration(site.toConfiguration());
            
            auditDisasterRecoveryOps(OperationTypeEnum.UPDATE_SITE, AuditLogManager.AUDITLOG_SUCCESS, null, site.getVip(), site.getName());
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Error happened when update site %s", uuid, e);
            auditDisasterRecoveryOps(OperationTypeEnum.UPDATE_SITE, AuditLogManager.AUDITLOG_FAILURE, null, site.getVip(), site.getName());
            throw APIException.internalServerErrors.updateSiteFailed(site.getName(), e.getMessage());
        }
    }

    /**
     * Common precheck logic for DR operations.
     *
     * @param excludedSiteIds, site ids to exclude from the cluster state precheck
     */
    private void commonPrecheck(List<String> excludedSiteIds) {
        if (drUtil.isStandby()) {
            throw new IllegalStateException("Operation is allowed on acitve site only");
        }
        if (!isClusterStable()) {
            throw new IllegalStateException("Cluster is not stable");
        }

        for (Site site : drUtil.listStandbySites()) {
            if (excludedSiteIds.contains(site.getUuid())) {
                continue;
            }
            // don't check node state for paused sites.
            if (site.getState().equals(SiteState.STANDBY_PAUSED)) {
                continue;
            }
            int nodeCount = site.getNodeCount();
            ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid(), nodeCount);
            if (state != ClusterInfo.ClusterState.STABLE) {
                log.error("Site {} is not stable {}", site.getUuid(), state);
                throw new IllegalStateException(String.format("Site %s is not stable", site.getName()));
            }
        }
    }

    /**
     * Wrapper for commonPrecheck that takes a single site instead of a list
     *
     * @param excludedSiteId, site id to be excluded from the cluster state precheck, check all if set to null.
     */
    private void commonPrecheck(String excludedSiteId) {
        List<String> excludedSiteIds = new ArrayList<>();
        if (excludedSiteId != null) {
            excludedSiteIds.add(excludedSiteId);
        }
        commonPrecheck(excludedSiteIds);
    }

    private Site validateSiteConfig(String uuid) {
        if (!isClusterStable()) {
            log.error("Cluster is unstable");
            throw APIException.serviceUnavailable.clusterStateNotStable();
        }

        try {
            return drUtil.getSiteFromLocalVdc(uuid);
        } catch (CoordinatorException e) {
            log.error("Can't find site {} from ZK", uuid);
            throw APIException.badRequests.siteIdNotFound();
        }
    }

    /**
     * @return DR operation lock only when successfully acquired lock and there's no ongoing DR operation, throw Exception otherwise
     */
    private InterProcessLock getDROperationLock() {
        // Try to acquire lock, succeed or throw Exception
        InterProcessLock lock = coordinator.getLock(DR_OPERATION_LOCK);
        boolean acquired;
        try {
            acquired = lock.acquire(LOCK_WAIT_TIME_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            try {
                lock.release();
            } catch (Exception ex) {
                log.error("Fail to release DR operation lock", ex);
            }
            throw APIException.internalServerErrors.failToAcquireDROperationLock();
        }
        if (!acquired) {
            throw APIException.internalServerErrors.failToAcquireDROperationLock();
        }

        // Has successfully acquired lock
        // Check if there's ongoing DR operation, if there is, release lock and throw exception
        Site ongoingSite = null;
        List<Site> sites = drUtil.listSites();
        for (Site site : sites) {
            if (site.getState().isDROperationOngoing()) {
                ongoingSite = site;
                break;
            }
        }
        if (ongoingSite != null) {
            try {
                lock.release();
            } catch (Exception e) {
                log.error("Fail to release DR operation lock", e);
            }
            throw APIException.internalServerErrors.concurrentDROperationNotAllowed(ongoingSite.getName(), ongoingSite.getState()
                    .toString());
        }

        return lock;
    }

    /*
     * Internal method to check whether standby can be attached to current active site
     */
    protected void precheckForStandbyAttach(SiteConfigRestRep standby) {
        if (!isClusterStable()) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed("Current site is not stable");
        }

        if (!standby.isClusterStable()) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed("Remote site is not stable");
        }

        // standby should be refresh install
        if (!standby.isFreshInstallation()) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed("Standby is not a fresh installation");
        }

        // DB schema version should be same
        String currentDbSchemaVersion = coordinator.getCurrentDbSchemaVersion();
        String standbyDbSchemaVersion = standby.getDbSchemaVersion();
        if (!currentDbSchemaVersion.equalsIgnoreCase(standbyDbSchemaVersion)) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed(String.format(
                    "Standby db schema version %s is not same as acitve site %s",
                    standbyDbSchemaVersion, currentDbSchemaVersion));
        }

        // this site should not be standby site
        String activeId = drUtil.getActiveSiteId();
        if (activeId != null && !activeId.equals(coordinator.getSiteId())) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed("This site is also a standby site");
        }
        
        checkSupportedIPForAttachStandby(standby);
    }

    protected void checkSupportedIPForAttachStandby(SiteConfigRestRep standby) {
        Site site = drUtil.getLocalSite();
        
        //active has IPv4 and standby has no IPv4
        if (!isMapEmpty(site.getHostIPv4AddressMap()) && isMapEmpty(standby.getHostIPv4AddressMap())) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed("Unsupported network configuration. Active site has IPv4. Standby site should be IPv4 or dual stack ");
        }
        
        //active has only IPv6 and standby has no IPv6
        if (isMapEmpty(site.getHostIPv4AddressMap()) && isMapEmpty(standby.getHostIPv6AddressMap())) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed("Unsupported network configuration. Active site only has IPv6, Standby site should has IPv6 address");
        }
    }
    
    private boolean isMapEmpty(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return true;
        }
        
        return false;
    }

    protected void precheckStandbyVersion(SiteAddParam standby) {
        ViPRSystemClient viprSystemClient = createViPRSystemClient(standby.getVip(), standby.getUsername(), standby.getPassword());

        // software version should be matched
        SoftwareVersion currentSoftwareVersion;
        SoftwareVersion standbySoftwareVersion;
        try {
            currentSoftwareVersion = coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion();
            standbySoftwareVersion = new SoftwareVersion(viprSystemClient.upgrade().getTargetVersion().getTargetVersion());
        } catch (Exception e) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed(String.format("Fail to get software version %s",
                    e.getMessage()));
        }

        if (!isVersionMatchedForStandbyAttach(currentSoftwareVersion, standbySoftwareVersion)) {
            throw APIException.internalServerErrors.addStandbyPrecheckFailed(String.format(
                    "Standby site version %s is not equals to current version %s",
                    standbySoftwareVersion, currentSoftwareVersion));
        }
    }

    /*
     * Internal method to check whether failover from acitve to standby is allowed
     */
    protected void precheckForSwitchover(String standbyUuid) {
        Site standby = null;
        try {
            standby = drUtil.getSiteFromLocalVdc(standbyUuid);
        } catch (CoordinatorException e) {
            throw APIException.internalServerErrors.switchoverPrecheckFailed(standby.getUuid(),
                    "Standby uuid is not valid, can't find in ZK");
        }

        if (standbyUuid.equals(drUtil.getActiveSiteId())) {
            throw APIException.internalServerErrors.switchoverPrecheckFailed(standby.getName(), "Can't switchover to a acitve site");
        }

        if (!drUtil.isSiteUp(standbyUuid)) {
            throw APIException.internalServerErrors.switchoverPrecheckFailed(standby.getName(), "Standby site is not up");
        }

        if (standby.getState() != SiteState.STANDBY_SYNCED) {
            throw APIException.internalServerErrors.switchoverPrecheckFailed(standby.getName(), "Standby site is not fully synced");
        }

        List<Site> existingSites = drUtil.listSites();
        for (Site site : existingSites) {
            ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid(), site.getNodeCount());
            if (state != ClusterInfo.ClusterState.STABLE) {
                log.info("Site {} is not stable {}", site.getUuid(), state);
                throw APIException.internalServerErrors.switchoverPrecheckFailed(site.getName(),
                        String.format("Site %s is not stable", site.getName()));
            }
        }
    }

    /*
     * Internal method to check whether failover to standby is allowed
     */
    protected void precheckForFailoverLocally(String standbyUuid) {
        Site standby = drUtil.getLocalSite();

        // API should be only send to local site 
        if (!standby.getUuid().equals(standbyUuid)) {
            throw APIException.internalServerErrors.failoverPrecheckFailed(standby.getName(),
                    String.format("Failover can only be executed in local site. Local site uuid %s is not matched with uuid %s",
                            standby.getUuid(), standbyUuid));
        }
        
        precheckForFailover();
    }
    
    protected void precheckForFailover() {
        Site standby = drUtil.getLocalSite();
        String standbyUuid = standby.getUuid();
        
        // show be only standby
        if (drUtil.isActiveSite()) {
            throw APIException.internalServerErrors.failoverPrecheckFailed(standbyUuid, "Failover can't be executed in acitve site");
        }

        // should be SYNCED
        if (standby.getState() != SiteState.STANDBY_SYNCED) {
            throw APIException.internalServerErrors.failoverPrecheckFailed(standby.getName(), "Standby site is not fully synced");
        }

        // Current site is stable
        ClusterInfo.ClusterState state = coordinator.getControlNodesState(standbyUuid, standby.getNodeCount());
        if (state != ClusterInfo.ClusterState.STABLE) {
            log.info("Site {} is not stable {}", standby.getName(), state);
            throw APIException.internalServerErrors.failoverPrecheckFailed(standby.getName(),
                    String.format("Site %s is not stable", standby.getName()));
        }
        
        // this is standby site and NOT in ZK read-only or observer mode,
        // it means acitve is down and local ZK has been reconfig to participant
        CoordinatorClientInetAddressMap addrLookupMap = coordinator.getInetAddessLookupMap();
        String myNodeId = addrLookupMap.getNodeId();
        String coordinatorMode = drUtil.getLocalCoordinatorMode(myNodeId);
        log.info("Local coordinator mode is {}", coordinatorMode);
        if (DrUtil.ZOOKEEPER_MODE_OBSERVER.equals(coordinatorMode) || DrUtil.ZOOKEEPER_MODE_READONLY.equals(coordinatorMode)) {
            log.info("Acitve site is available now, can't do failover");
            throw APIException.internalServerErrors.failoverPrecheckFailed(standbyUuid, "Acitve site is available now, can't do failover");
        }
    }

    protected void validateAddParam(SiteAddParam param, List<Site> existingSites) {
        for (Site site : existingSites) {
            if (site.getName().equals(param.getName())) {
                throw APIException.internalServerErrors.addStandbyPrecheckFailed("Duplicate site name");
            }

            ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid(), site.getNodeCount());
            if (state != ClusterInfo.ClusterState.STABLE) {
                log.info("Site {} is not stable {}", site.getUuid(), state);
                throw APIException.internalServerErrors.addStandbyPrecheckFailed(String.format("Site %s is not stable", site.getName()));
            }
        }
    }

    private String generateShortId(List<Site> existingSites) throws Exception {
        Set<String> existingShortIds = new HashSet<String>();
        for (Site site : existingSites) {
            existingShortIds.add(site.getStandbyShortId());
        }

        for (int i = 1; i < MAX_NUM_OF_STANDBY; i++) {
            String id = String.format(SHORTID_FMT, i);
            if (!existingShortIds.contains(id)) {
                return id;
            }
        }
        throw new Exception("Failed to generate standby short id");
    }

    protected boolean isClusterStable() {
        return coordinator.getControlNodesState() == ClusterInfo.ClusterState.STABLE;
    }

    protected boolean isFreshInstallation() {
        Configuration setupConfig = coordinator.queryConfiguration(InitialSetup.CONFIG_KIND, InitialSetup.CONFIG_ID);

        boolean freshInstall = (setupConfig == null) || !Boolean.parseBoolean(setupConfig.getConfig(InitialSetup.COMPLETE));
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
        SoftwareVersion standbyVersionWildcard = new SoftwareVersion(versionString.substring(0, versionString.lastIndexOf(".")) + ".*");
        return currentSoftwareVersion.weakEquals(standbyVersionWildcard);
    }

    // encapsulate the create ViPRCoreClient operation for easy UT writing because need to mock ViPRCoreClient
    protected ViPRCoreClient createViPRCoreClient(String vip, String username, String password) {
        try {
            return new ViPRCoreClient(vip, true).withLogin(username, password);
        } catch (Exception e) {
            log.error(String.format("Fail to create vipr client, vip: %s, username: %s", vip, username), e);
            throw APIException.internalServerErrors.failToCreateViPRClient();
        }
    }

    // encapsulate the create ViPRSystemClient operation for easy UT writing because need to mock ViPRSystemClient
    protected ViPRSystemClient createViPRSystemClient(String vip, String username, String password) {
        try {
            return new ViPRSystemClient(vip, true).withLogin(username, password);
        } catch (Exception e) {
            log.error(String.format("Fail to create vipr client, vip: %s, username: %s", vip, username), e);
            throw APIException.internalServerErrors.failToCreateViPRClient();
        }
    }

    private void setSiteError(String siteId, InternalServerErrorException exception) {
        if (siteId == null || siteId.isEmpty())
            return;

        Site site = drUtil.getSiteFromLocalVdc(siteId);
        site.setState(SiteState.STANDBY_ERROR);
        coordinator.persistServiceConfiguration(siteId, site.toConfiguration());

        SiteError error = new SiteError(exception);
        coordinator.setTargetInfo(site.getUuid(), error);
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

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    public void setIpsecConfig(IPsecConfig ipsecConfig) {
        this.ipsecConfig = ipsecConfig;
    }

    // DBSVC config parameters
    public void setDbCommonInfo(Properties dbCommonInfo) {
        _dbCommonInfo = dbCommonInfo;
    }
}
