/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.service.impl.resource;

import java.net.InetAddress;
import java.net.URI;
import java.util.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.ipsec.IPsecConfig;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.geo.service.impl.GeoBackgroundTasks;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.geo.vdccontroller.impl.InternalDbClient;
import com.emc.storageos.geomodel.VdcCertListParam;
import com.emc.storageos.geomodel.VdcConfig;
import com.emc.storageos.geomodel.VdcConfigSyncParam;
import com.emc.storageos.geomodel.VdcNatCheckParam;
import com.emc.storageos.geomodel.VdcNatCheckResponse;
import com.emc.storageos.geomodel.VdcNodeCheckParam;
import com.emc.storageos.geomodel.VdcNodeCheckResponse;
import com.emc.storageos.geomodel.VdcPostCheckParam;
import com.emc.storageos.geomodel.VdcPreCheckParam;
import com.emc.storageos.geomodel.VdcPreCheckParam2;
import com.emc.storageos.geomodel.VdcPreCheckResponse;
import com.emc.storageos.geomodel.VdcPreCheckResponse2;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.services.util.Strings;
import com.emc.storageos.services.util.SysUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.google.common.net.InetAddresses;

import static com.emc.storageos.coordinator.client.model.Constants.IPSEC_KEY;
import static com.emc.storageos.coordinator.client.model.Constants.VDC_CONFIG_VERSION;

@Path(GeoServiceClient.VDCCONFIG_URI)
public class VdcConfigService {
    private static final Logger log = LoggerFactory.getLogger(VdcConfigService.class);

    public static final String DataServiceName = "blobsvc";
    public static final String DataServiceVersion = "1";

    @Autowired
    private GeoBackgroundTasks geoBackgroundTasks;

    @Autowired
    private InternalDbClient dbClient;

    @Autowired
    private CoordinatorClient coordinator;

    @Autowired
    private VdcConfigHelper helper;

    private Service service;
    
    private SysUtils sysUtils;

    private IPsecConfig ipsecConfig;
    public void setIpsecConfig(IPsecConfig ipsecConfig) {
        this.ipsecConfig = ipsecConfig;
    }

    public void setService(Service service) {
        this.service = service;
    }

    /**
     * Retrieve the vdc config info of the current site, return such info with precheck.
     * 1. For adding a new vdc, the current vdc should be in ISOLATED status and is a fresh installation.
     * 2. For updating an existing vdc, the current vdc should be in CONNECTED status.
     * 
     * @param checkParam
     * 
     * @return VirtualDataCenterResponse
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/precheck")
    public VdcPreCheckResponse precheckVdcConfig(VdcPreCheckParam checkParam) {
        log.info("Start vdc config precheck for {} ...", checkParam.getConfigChangeType());

        if (service.getId().endsWith("standalone")) {
            throw GeoException.fatals.remoteVDCWrongStandaloneInstall();
        }

        log.info("Loading local vdc config ...");
        VirtualDataCenter vdc = VdcUtil.getLocalVdc();
        Boolean isFresher = checkParam.getFresher();
        if (isFresher != null && isFresher) {
            // check if VDC is a fresh installation, in ISOLATED status
            if (VirtualDataCenter.ConnectionStatus.ISOLATED != vdc.getConnectionStatus()) {
                throw GeoException.fatals.remoteFreshVDCWrongStatus(vdc.getId());
            }
        } else {
            // check if VDC is in CONNECTED status
            // check if VDC is in CONNECTED status- remove, add; update will skip-CTRL3549
            if (checkParam.getConfigChangeType().equals(VdcConfig.ConfigChangeType.CONNECT_VDC.toString()) ||
                    (checkParam.getConfigChangeType().equals(VdcConfig.ConfigChangeType.REMOVE_VDC.toString()))) {
                if (vdc.getConnectionStatus() != VirtualDataCenter.ConnectionStatus.CONNECTED) {
                    throw GeoException.fatals.remoteVDCWrongOperationStatus(vdc.getId(), checkParam.getConfigChangeType());
                }
            }
        }

        boolean hasData = false;
        if (isFresher) {
            hasData = dbClient.hasUsefulData();
        }

        hasData |= hasDataService();

        log.info("Checking software version ...");
        SoftwareVersion remoteSoftVer = null;

        try {
            remoteSoftVer = new SoftwareVersion(checkParam.getSoftwareVersion());
            log.info("Software version of remote vdc: {}", remoteSoftVer);
        } catch (Exception e) {
            log.info("Cannot get software version from checkParam, the version of remote vdc is lower than v2.3 with exception {}",
                    e.getMessage());
        }

        SoftwareVersion localSoftVer;
        try {
            localSoftVer = coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion();
        } catch (Exception ex) {
            throw GeoException.fatals.remoteVDCFailedToGetVersion(vdc.getId());
        }

        return toVirtualDataCenterResponse(vdc, hasData, remoteSoftVer, localSoftVer);
    }

    /**
     * @return true if there are data services
     */
    private boolean hasDataService() {
        try {
            List<Service> services = coordinator.locateAllServices(DataServiceName, DataServiceVersion, null, null);

            if (services.isEmpty()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * Do more precheck
     * For disconnecting a vdc, check if there is a VDC that is under disconnecting
     * If yes, return the VDC under disconnecting, otherwise set the VDC (given by parameter)
     * status to DISCONNECTING
     * 
     * @param checkParam
     * 
     * @return VdcPreCheckResponse
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/precheck2")
    public VdcPreCheckResponse2 precheckVdcConfig(VdcPreCheckParam2 checkParam) {
        log.info("Start vdc config precheck2 for {} ...", checkParam.getConfigChangeType());

        if (service.getId().endsWith("standalone")) {
            throw GeoException.fatals.remoteVDCWrongStandaloneInstall();
        }

        VdcConfig.ConfigChangeType type = checkParam.getConfigChangeType();

        VirtualDataCenter vdc = null;

        VdcPreCheckResponse2 resp2 = new VdcPreCheckResponse2();
        resp2.setCompatible(true);

        // BZ
        // TODO Need to use a different method to update info on lock on a remote system.
        // Need to use a different field (not connection status of VDC object) as a locking mechanism)
        boolean precheckFailed = checkParam.isPrecheckFailed();
        switch (type) {
            case DISCONNECT_VDC:
                log.info("Precheck2 for disconnect ops");
                vdc = helper.getDisconnectingVdc();
                if (checkParam.getIsAllNotReachable()) {
                    URI targetVdcId = checkParam.getVdcIds().get(0);
                    log.info("Precheck2 to check the disconnect vdc {} is reachable", targetVdcId);
                    VirtualDataCenter targetVdc = dbClient.queryObject(VirtualDataCenter.class, targetVdcId);

                    resp2.setIsAllNodesNotReachable(!helper.areNodesReachable(getLocalVdc().getShortId(),
                            targetVdc.getHostIPv4AddressesMap(), targetVdc.getHostIPv6AddressesMap(), checkParam.getIsAllNotReachable()));
                    break;
                }
                if (precheckFailed) {
                    log.info("Precheck2 to update reconnect precheck fail status");
                    String vdcState = checkParam.getDefaultVdcState();
                    if (StringUtils.isNotEmpty(vdcState)) {
                        vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.valueOf(vdcState));
                        dbClient.updateAndReindexObject(vdc);
                    }
                    break;
                }

                if (vdc == null) {
                    // no DISCONNECTING_VDC
                    log.info("Precheck2: there is no disconnecting vdc");
                    URI srcVdcId = checkParam.getVdcIds().get(1);
                    VirtualDataCenter srcVdc = dbClient.queryObject(VirtualDataCenter.class, srcVdcId);
                    if (srcVdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.DISCONNECTED) {
                        resp2.setCompatible(false);
                        break;
                    }

                    // BZ
                    // TODO need to use a different field to set locks on concurrent VDC operation
                    URI id = checkParam.getVdcIds().get(0);
                    vdc = dbClient.queryObject(VirtualDataCenter.class, id);
                    vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.DISCONNECTING);
                    dbClient.updateAndReindexObject(vdc);
                } else {
                    resp2 = toVirtualDataCenterResponse2(vdc, true, null);
                }

                break;
            case RECONNECT_VDC:
                log.info("Precheck2 for reconnect ops checkParam={}", checkParam);
                List<String> blackList = checkParam.getBlackList();
                List<String> whiteList = checkParam.getWhiteList();
                log.info("Precheck2 to check if two vdc disconnect each other");
                resp2.setCompatible(true);
                if (isDisconnectedEachOther(blackList, whiteList)) {
                    log.info("Precheck2: two vdc have disconnected each other");
                    resp2.setCompatible(false);
                    break;
                }
                if (precheckFailed) {
                    log.info("Precheck2 to update reconnect precheck fail status");
                    URI targetVdcId = checkParam.getVdcIds().get(0);
                    log.info("Precheck2 to check the disconnect vdc {} is reachable", targetVdcId);
                    VirtualDataCenter targetVdc = dbClient.queryObject(VirtualDataCenter.class, targetVdcId);
                    String vdcState = checkParam.getDefaultVdcState();
                    if (StringUtils.isNotEmpty(vdcState)) {
                        targetVdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.valueOf(vdcState));
                        dbClient.updateAndReindexObject(targetVdc);
                    }
                    break;
                }

                break;
        }

        log.info("Precheck2 done, resp is {}", resp2.toString());
        return resp2;
    }

    private boolean isDisconnectedEachOther(List<String> blackList, List<String> whiteList) {
        VirtualDataCenter myVdc = getLocalVdc();
        Collection<String> addresses = myVdc.queryHostIPAddressesMap().values();
        log.info("local vdc IP addresses:{}", addresses);

        boolean found = false;
        for (String addr : addresses) {
            if (blackList.contains(addr)) {
                log.info("The addr {} is in the blackList {}", addr, blackList);
                found = true;
                break;
            }
        }

        if (found == false) {
            return false; // not disconnected each other
        }

        Collection<List<String>> localBlackLists = dbClient.getBlacklist().values();
        log.info("The localBackLists={}", localBlackLists);
        List<String> localBlackList = new ArrayList();
        for (List<String> list : localBlackLists) {
            localBlackList = list;
        }

        return localBlackList.containsAll(whiteList);
    }

    private VirtualDataCenter getLocalVdc() {
        List<URI> ids = dbClient.queryByType(VirtualDataCenter.class, true);

        for (URI id : ids) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, id);
            if (vdc.getLocal() == true) {
                return vdc;
            }
        }

        throw new RuntimeException("Failed to find local vdc");
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response syncVdcConfig(VdcConfigSyncParam param) {
        log.info("Acquired gobal lock, vdc {}...", param.getVirtualDataCenters().size());

        VdcConfig.ConfigChangeType type = VdcConfig.ConfigChangeType.valueOf(param.getConfigChangeType());
        log.info("Current config change type is {}", type);

        switch (type) {
            case DISCONNECT_VDC:
                String disconntecedvdcId = param.getAssignedVdcId();
                try {
                    disconnectVdc(disconntecedvdcId);
                } catch (Exception e) {
                    throw GeoException.fatals.disconnectRemoteSyncFailed(disconntecedvdcId, e.getMessage());
                }
                break;
            case RECONNECT_VDC:
                String reconnectVdcId = param.getAssignedVdcId();
                try {
                    VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
                    // If the local vdc is the one need to be reconnected back, trigger a node repair for geodb
                    if (localVdc.getId().toString().equals(reconnectVdcId)) {
                        log.info("Perform sync vdc config operation and node repair for the reconnected vdc {}", reconnectVdcId);
                        VirtualDataCenter reconnVdc = dbClient.queryObject(VirtualDataCenter.class, new URI(reconnectVdcId));

                        // Update operated local db, make sure all the vdcs in operated vdc'd db have the same status with others.
                        log.info("Reconnect ops update local db for operatedVdc");
                        updateVdcStatusInDB(param.getVirtualDataCenters());
                        log.info("Reconnect ops update local db done");

                        // Clean blacklist for reconnected vdc
                        log.info("Reconnect ops to clean blacklist for reconnected vdc.");
                        updateBlackListForReconnectedVdc();
                        log.info("Reconnect ops: new blacklist is {}", dbClient.getBlacklist());

                        helper.syncVdcConfig(param.getVirtualDataCenters(), null);

                        log.info("Current strategy options is {}", dbClient.getGeoStrategyOptions());
                        log.info("Current schema version for Geo is {}", dbClient.getGeoSchemaVersions());

                        geoBackgroundTasks.startGeodbNodeRepair();

                    } else {
                        reconnectVdc(reconnectVdcId);
                    }
                } catch (Exception e) {
                    throw GeoException.fatals.reconnectRemoteSyncFailed(reconnectVdcId, e.getMessage());
                }
                break;
            default:
                Iterator<URI> srcVdcIdIter = dbClient.queryByType(VirtualDataCenter.class, true).iterator();

                String assignedVdcId = param.getAssignedVdcId();
                String geoEncryptionKey = param.getGeoEncryptionKey();

                // for add-vdc
                if (assignedVdcId != null && geoEncryptionKey != null) {
                    log.info("This vdc will be added to a geo system.");
                    if (!srcVdcIdIter.hasNext()) {
                        throw GeoException.fatals.connectVDCLocalMultipleVDC(assignedVdcId);
                    }
                    URI srcVdcId = srcVdcIdIter.next();
                    // Delete the local vdc record
                    VirtualDataCenter existingVdc = dbClient.queryObject(VirtualDataCenter.class,
                            srcVdcId);
                    dbClient.markForDeletion(existingVdc);
                    log.info("The existing vdc {} has been removed. The current vdc id will be {}.",
                            srcVdcId, assignedVdcId);

                    helper.setGeoEncryptionKey(geoEncryptionKey);
                    log.info("geo encryption key has been updated");
                    helper.resetStaleLocalObjects();

                    dbClient.stopClusterGossiping();
                } else if (assignedVdcId == null && geoEncryptionKey == null) {
                    log.info("Sync'ing new vdc info to existing geo system.");
                } else {
                    throw GeoException.fatals.remoteVDCGeoEncryptionMissing();
                }

                helper.syncVdcConfig(param.getVirtualDataCenters(), assignedVdcId);

                if (isRemoveOp(param)) {
                    log.info("Disable grossip to avoid schema version disagreement errors");
                    dbClient.stopClusterGossiping();
                }

                break;
        }
        return Response.ok().build();
    }

    private void updateVdcStatusInDB(List<VdcConfig> vdcConfigs) {
        List<URI> vdcIdIter = new ArrayList<>();
        List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
        for (URI id : vdcIds) {
            vdcIdIter.add(id);
        }
        for (VdcConfig vdcConfig : vdcConfigs) {
            log.info("current config's id is {}", vdcConfig.getId());
            if (vdcIdIter.contains(vdcConfig.getId())) {
                VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcConfig.getId());
                vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.valueOf(vdcConfig.getConnectionStatus()));
                dbClient.updateAndReindexObject(vdc);
            }
        }
    }

    private void updateBlackListForReconnectedVdc() {
        List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
        dbClient.clearBlackList();
        log.info("After clear, get current black list {}", dbClient.getBlacklist());
        for (URI vdcId : vdcIds) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
            if (vdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.DISCONNECTED) {
                log.info("Add vdc {} with status {} to blacklist", vdc.getId(), vdc.getConnectionStatus());
                dbClient.addVdcNodesToBlacklist(vdc);
            }
        }
    }

    private void disconnectVdc(String vdcId) throws Exception {
        URI id = new URI(vdcId);
        VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, id);
        vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.DISCONNECTED);
        dbClient.updateAndReindexObject(vdc);

        dbClient.addVdcNodesToBlacklist(vdc);
    }

    private void reconnectVdc(String vdcId) throws Exception {
        URI id = new URI(vdcId);
        VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, id);
        vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.CONNECTED);
        dbClient.updateAndReindexObject(vdc);

        dbClient.removeVdcNodesFromBlacklist(vdc);
    }

    private boolean isRemoveOp(VdcConfigSyncParam param) {
        return VdcConfig.ConfigChangeType.REMOVE_VDC.toString().equals(param.getConfigChangeType());
    }

    private static InetAddress parseInetAddress(String addrStr) {
        if (addrStr == null || addrStr.isEmpty()) {
            return null;
        }

        try {
            return InetAddresses.forString(addrStr);
        } catch (IllegalArgumentException e) {
            log.error(String.format("Failed to parse Inet address string: %s", addrStr), e);
            return null;
        }
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/natcheck")
    public VdcNatCheckResponse checkIfBehindNat(VdcNatCheckParam checkParam, @HeaderParam("X-Forwarded-For") String clientIp) {
        if (checkParam == null) {
            log.error("checkParam is null, X-Forwarded-For is {}", clientIp);
            throw GeoException.fatals.invalidNatCheckCall("(null)", clientIp);
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
            throw GeoException.fatals.invalidNatCheckCall(e.getMessage(), clientIp);
        }

        VdcNatCheckResponse resp = new VdcNatCheckResponse();
        resp.setSeenIp(clientIp);
        resp.setBehindNAT(isBehindNat);

        return resp;
    }

    /**
     * Post check after sync vdc config.
     * Verify if connect vdc finished successfully, if so, update the connection status.
     * 
     * @param checkParam List of parameters to be checked.
     * 
     * @return if check pass
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/postcheck")
    public Response verifyVdcConfig(VdcPostCheckParam checkParam) {
        log.info("Post check enter: {}", checkParam.getVdcList());
        helper.syncVdcConfigPostSteps(checkParam);
        return Response.ok().build();
    }

    /**
     * check to see if the individual nodes of one vdc are visible from another
     * 
     * @param checkParam
     * @return
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/nodecheck")
    public VdcNodeCheckResponse checkNodeConnections(VdcNodeCheckParam checkParam, @HeaderParam("X-Forwarded-For") String clientIp) {
        List<VdcConfig> vdcList = checkParam.getVirtualDataCenters();

        log.info("checking nodes for vdcs {} ...", getVdcIds(vdcList));

        if (service.getId().endsWith("standalone")) {
            throw GeoException.fatals.remoteVDCWrongStandaloneInstall();
        }

        ArgValidator.checkFieldNotEmpty(vdcList, "vdc");
        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        if (localVdc == null) {
            throw GeoException.fatals.failedToFindLocalVDC();
        }

        return toVdcNodeCheckResponse(localVdc, helper.areNodesReachable(vdcList, false));
    }

    /**
     * @param configs
     * @return
     */
    private String getVdcIds(List<VdcConfig> configs) {
        List<String> vdcShortIds = new ArrayList<String>();
        for (VdcConfig config : configs) {
            vdcShortIds.add(config.getShortId());
        }
        return StringUtils.join(vdcShortIds, ",");
    }

    private String getVdcIps(List<VdcConfig> configs) {
        StringBuilder builder = new StringBuilder();
        for (VdcConfig config : configs) {
            List<String> ips = new ArrayList<>();
            ips.addAll(config.getHostIPv4AddressesMap().values());
            ips.addAll(config.getHostIPv6AddressesMap().values());

            builder.append('{');
            builder.append(StringUtils.join(ips, ","));
            builder.append("}");
        }

        return builder.toString();
    }

    /**
     * @param from
     * @param areNodesReachable
     * @return
     */
    private VdcNodeCheckResponse toVdcNodeCheckResponse(VirtualDataCenter from, boolean areNodesReachable) {
        VdcNodeCheckResponse to = new VdcNodeCheckResponse();

        to.setId(from.getId());
        to.setShortId(from.getShortId());
        to.setNodesReachable(areNodesReachable);

        return to;
    }

    private VdcPreCheckResponse toVirtualDataCenterResponse(VirtualDataCenter from, boolean hasData, SoftwareVersion remoteSoftVer,
            SoftwareVersion localSoftVer) {
        if (from == null) {
            return null;
        }
        VdcPreCheckResponse to = new VdcPreCheckResponse();

        to.setId(from.getId());
        to.setConnectionStatus(from.getConnectionStatus().name());
        to.setVersion(from.getVersion());
        to.setShortId(from.getShortId());
        to.setHostCount(from.getHostCount());

        to.setHostIPv4AddressesMap(new StringMap(from.getHostIPv4AddressesMap()));
        to.setHostIPv6AddressesMap(new StringMap(from.getHostIPv6AddressesMap()));

        to.setName(from.getLabel());
        to.setDescription(from.getDescription());
        to.setApiEndpoint(from.getApiEndpoint());
        to.setSecretKey(from.getSecretKey());
        to.setHasData(hasData);
        to.setSoftwareVersion(localSoftVer.toString());
        boolean compatible = false;
        if (remoteSoftVer != null) {
            compatible = helper.isCompatibleVersion(remoteSoftVer);
        }
        to.setCompatible(compatible);
        boolean clusterStable = isClusterStable();
        to.setClusterStable(clusterStable);
        log.info("current cluster stable {}", clusterStable);

        return to;
    }

    private VdcPreCheckResponse2 toVirtualDataCenterResponse2(VirtualDataCenter from, boolean hasData, SoftwareVersion softVer) {
        if (from == null) {
            return null;
        }
        VdcPreCheckResponse2 to = new VdcPreCheckResponse2();

        to.setId(from.getId());
        to.setCompatible(helper.isCompatibleVersion(softVer));
        boolean clusterStable = isClusterStable();
        to.setClusterStable(clusterStable);
        log.info("current cluster stable {}", clusterStable);

        return to;
    }

    /**
     * Check to see if the local cluster is stable
     * 
     * @return true if state is STABLE
     */
    private boolean isClusterStable() {
        return helper.isClusterStable();
    }

    /**
     * Sync all VDCs' certs into the local VDC.
     * 
     * @param vdcCertListParam vdc certs list parameter
     * 
     * @return
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/certs")
    public Response syncCert(VdcCertListParam vdcCertListParam) {
        log.info("Syncing vdc certs: {}", vdcCertListParam);
        helper.syncVdcCerts(vdcCertListParam);
        return Response.ok().build();
    }

    /**
     * Check if the current VDC is stable or not
     * 
     * @return "true" if the current VDC is stable, "false" otherwise
     */
    @GET
    @Produces({ MediaType.TEXT_PLAIN })
    @Path("/stablecheck")
    public String checkVdcStable() {
        return String.valueOf(isClusterStable());
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/ipsec-properties")
    public PropertyInfoRestRep getIpsecProperties() throws Exception {
        log.info("getting ipsec properties.");
        Map<String, String> ipsecProps = new HashMap();
        ipsecProps.put(IPSEC_KEY, ipsecConfig.getPreSharedKey());

        SiteInfo siteInfo = coordinator.getTargetInfo(SiteInfo.class);
        String vdcConfigVersion = String.valueOf(siteInfo.getVdcConfigVersion());
        ipsecProps.put(VDC_CONFIG_VERSION, vdcConfigVersion);
        log.info("ipsec key: " + ipsecConfig.getPreSharedKey()
                + ", vdc config version: " + vdcConfigVersion);

        return new PropertyInfoRestRep(ipsecProps);
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/resetblacklist")
    public Response resetBlackListForVdc(@QueryParam("vdc_short_id") String vdcShortId) {
        try {
            log.info("Reset blacklist for {}", vdcShortId);
            URI vdcId = VdcUtil.getVdcUrn(vdcShortId);
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
            dbClient.removeVdcNodesFromBlacklist(vdc);
            return Response.ok().build();
        } catch (InternalException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Reset blacklist vdc error", ex);
            throw GeoException.fatals.reconnectVdcIncompatible();
        }
    }

    public void setSysUtils(SysUtils sysUtils) {
        this.sysUtils = sysUtils;
    }
}
