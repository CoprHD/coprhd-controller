/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.vdccontroller.impl;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.crypto.SecretKey;

import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.geomodel.VdcNatCheckParam;
import com.emc.storageos.geomodel.VdcNatCheckResponse;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.security.geo.GeoServiceHelper;
import com.emc.storageos.security.geo.GeoServiceJob;
import com.emc.storageos.security.geo.GeoServiceJob.JobType;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.exceptions.InvalidSoftwareVersionException;
import com.emc.storageos.db.client.model.VdcVersion;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.geomodel.VdcCertListParam;
import com.emc.storageos.geomodel.VdcConfig;
import com.emc.storageos.geomodel.VdcConfigSyncParam;
import com.emc.storageos.geomodel.VdcNodeCheckParam;
import com.emc.storageos.geomodel.VdcNodeCheckResponse;
import com.emc.storageos.geomodel.VdcPostCheckParam;
import com.emc.storageos.geomodel.VdcPreCheckResponse;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.GeoServiceClient;

/*
 * Detail implementation of vdc connect operation
 */
public class ConnectVdcTaskOp extends AbstractVdcTaskOp {

    private final static Logger log = LoggerFactory.getLogger(ConnectVdcTaskOp.class);

    private final static int NODE_CHECK_TIMEOUT = 60 * 1000; // one minute

    private final static SoftwareVersion netcheckMinVer = new SoftwareVersion("2.2.0.0.*");

    private InternalApiSignatureKeyGenerator apiSignatureGenerator;

    public ConnectVdcTaskOp(InternalDbClient dbClient, GeoClientCacheManager geoClientCache,
            VdcConfigHelper helper, Service serviceInfo, VirtualDataCenter vdc, String taskId,
            Properties vdcInfo, InternalApiSignatureKeyGenerator generator, KeyStore keystore, IPsecConfig ipsecConfig) {
        super(dbClient, geoClientCache, helper, serviceInfo, vdc, taskId, vdcInfo, keystore, ipsecConfig);
        this.apiSignatureGenerator = generator;
        this.operatedVdc = dbClient.queryObject(VirtualDataCenter.class,
                URI.create(vdcInfo.getProperty(GeoServiceJob.OPERATED_VDC_ID)));
        if (operatedVdc == null) {
            this.operatedVdcStatus = ConnectionStatus.CONNECTING;
        }
        else {
            this.operatedVdcStatus = operatedVdc.getConnectionStatus();
        }
    }

    private BasePermissionsHelper _permissionHelper;

    public void setBasePermissionHelper(BasePermissionsHelper _permissionHelper) {
        this._permissionHelper = _permissionHelper;
    }

    /**
     * Precheck if vdc connect is permitted, then sync the new vdc config to all sites
     */
    private void checkAndSync(InternalApiSignatureKeyGenerator apiSignatureGenerator, KeyStore keystore) {
        String shortId = vdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID);
        String vdcName = vdcInfo.getProperty(GeoServiceJob.VDC_NAME);
        lockHelper.acquire(shortId);
        log.info("Acquired global lock, go on with connect vdc");
        geoClientCache.clearCache();
        loadVdcInfo();

        // Check & verify connection status of my current vdc
        preSteps();

        // Have the certificate for the to be added vdc
        persistVdcCert(vdcName, vdcInfo.getProperty(GeoServiceJob.VDC_CERTIFICATE_CHAIN), true, shortId);

        // precheck
        VdcPreCheckResponse operatedVdcInfo = preCheck();

        // remove root's Tenant Roles or project ownerships in local vdc
        try {
            _permissionHelper.removeRootRoleAssignmentOnTenantAndProject();
        } catch (DatabaseException dbe) {
            throw GeoException.fatals.connectVdcRemoveRootRolesFailed(dbe);
        }

        String currentVdcIpsecKey = ipsecConfig.getPreSharedKeyFromZK();

        URI newVdcId = URIUtil.uri(vdcInfo.getProperty(GeoServiceJob.OPERATED_VDC_ID));
        GeoServiceHelper.backupOperationVdc(dbClient, JobType.VDC_CONNECT_JOB, newVdcId, null);
        VirtualDataCenter newVdc = GeoServiceHelper.prepareVirtualDataCenter(newVdcId, VirtualDataCenter.ConnectionStatus.CONNECTING,
                VirtualDataCenter.GeoReplicationStatus.REP_NONE, vdcInfo);
        dbClient.createObject(newVdc);
        helper.createVdcConfigInZk(mergeVdcInfo(operatedVdcInfo), currentVdcIpsecKey);
        
        // we should use uuid as cert name in trust store, but before we persist new vdc info
        // into db, we use vdc name as cert name, after we persist new vdc into db, persist uuid
        // as cert name and remove the one which use vdc name as cert name.
        persistVdcCert(newVdc.getId().toString(), newVdc.getCertificateChain(), true, shortId);
        removeVdcCert(vdcName, shortId);
        // add new remote VDC to the list of VDC to sync
        toBeSyncedVdc.add(newVdc);
        allVdc.add(newVdc);
        connectedVdc.add(newVdc);
        VdcUtil.invalidateVdcUrnCache();

        // Now set "operatedVdc as the newly created VDC
        operatedVdc = newVdc;

        // generate the cert chain to be synced
        VdcCertListParam certListParam = genCertListParam(VdcCertListParam.CMD_ADD_CERT);

        // from now on, vdc status will be marked as CONNECT_FAILED for any failure
        failedVdcStatus = ConnectionStatus.CONNECT_FAILED;

        // sync the new certificate to all connected sites
        syncCerts(VdcCertListParam.CMD_ADD_CERT, certListParam);

        VdcConfigSyncParam mergedVdcInfo = configMerge(operatedVdcInfo, currentVdcIpsecKey);
        if (mergedVdcInfo == null) {
            log.error("merge the vdc config of all sites failed");
            throw GeoException.fatals.mergeConfigFail();
        }

        // from this point on, any errors will not be retryable and requires manual
        // recovery

        try {
            configSync(mergedVdcInfo);
        } catch (GeoException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Failed to sync vdc config to all sites e=", e);
            throw GeoException.fatals.syncConfigFail(e);
        }
        // do not release the global lock here; lock is released during post processing
    }

    private void removeVdcCert(String vdcName, String shortId) {
        try {
            // remove the operated vdc cert with vdcName
            keystore.deleteEntry(vdcName);
        } catch (KeyStoreException e) {
            log.error("failed to delete the cert of {}.", vdcName);
            throw GeoException.fatals.connectVdcSyncCertFail(shortId, e);
        }
    }

    private void persistVdcCert(String vdcName, String certStr, boolean certchain, String shortId) {
        try {
            // add the target vdc cert into trust-store of local vdc
            helper.persistVdcCert(keystore, vdcName, certStr, certchain);
        } catch (APIException e) {
            log.error("failed to persist the new cert of {}.", vdcName);
            throw GeoException.fatals.connectVdcSyncCertFail(shortId, e);
        }
    }

    /**
     * PreSteps to be executed before initial connect vdc operation
     */
    private void preSteps() {
        // Check & verify connection status of my current vdc
        ConnectionStatus status = myVdc.getConnectionStatus();
        switch (status) {
            case ISOLATED:
                // current status is isolated, not connected with others, generate the inter-vdc secure key of my current vdc
                SecretKey key = apiSignatureGenerator.getSignatureKey(SignatureKeyType.INTERVDC_API);
                myVdc.setSecretKey(new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8")));
                dbClient.updateAndReindexObject(myVdc);
                break;
            case CONNECTED:
                // at least 2 connected record
                // getAllConnectedVdc
                if (getAllVdc().size() < 2) {
                    String errMsg = "incorrect connected vdc count";
                    log.error(errMsg);
                    throw GeoException.fatals.connectVdcPrecheckFail(myVdcId, errMsg);
                }
                // verify my secure key still valid
                break;
            default:
                String errMsg = "Unexpected local vdc connection status";
                log.error(errMsg);
                throw GeoException.fatals.connectVdcPrecheckFail(myVdcId, errMsg);
        }

    }

    /**
     * The vdc config has been synced to all sites, post check if all sites connected as expected
     */
    private void postSteps() {

        // from now on, vdc status will be marked as CONNECT_FAILED for any failure
        failedVdcStatus = ConnectionStatus.CONNECT_FAILED;
        geoClientCache.clearCache();
        loadVdcInfo();

        try {
            postCheck();
        } catch (Exception e) {
            log.error("wait for all sites db stable failed e=", e);
            throw GeoException.fatals.connectVdcPostCheckFail(e);
        }

        statusUpdate();

        // release the lock
        // lock is released in error handling code if an exception is thrown before we get here
        lockHelper.release(operatedVdc.getShortId());
    }

    /**
     * Check whether geo could accept the new vdc or not
     */
    private VdcPreCheckResponse preCheck() {
        log.info("Starting precheck on vdc connect ...");

        // Step 0: Get remote vdc version before send preCheck, since we modify the preCheckParam
        // avoid to send preCheck from v2.3 or higher to v2.2 v2.1, v2.0
        if (!isRemoteVdcVersionCompatible(vdcInfo)) {
            throw GeoException.fatals.connectVdcPrecheckFail(myVdcId, "Software version from remote vdc is lower than v2.3.");
        }
        
        log.info("Send vdc precheck to remote vdc");
        // step 1: 2 way communication to verify if link should be permitted
        VdcPreCheckResponse vdcResp = sendVdcPrecheckRequest(vdcInfo, true);

        log.info("Check VIP of remote vdc is used as the ApiEndpoint");
        // verify if node IP address is used as the ApiEndpoint
        String virtualIP = vdcInfo.getProperty(GeoServiceJob.VDC_API_ENDPOINT);
        if (!InetAddresses.isInetAddress(virtualIP)) {
            // FQDN used
            log.info("FQDN or hostname used: {}", virtualIP);
            try {
                virtualIP = InetAddress.getByName(vdcInfo.getProperty(GeoServiceJob.VDC_API_ENDPOINT)).getHostAddress();
                vdcInfo.setProperty(GeoServiceJob.VDC_API_ENDPOINT, virtualIP); // replace with real IP
                log.info("virtual ip of new vdc {}", virtualIP);
            } catch (UnknownHostException e) {
                throw GeoException.fatals.invalidFQDNEndPoint(vdcInfo.getProperty(GeoServiceJob.VDC_NAME), virtualIP);
            }
        }

        if (vdcResp.getHostIPv4AddressesMap().containsValue(virtualIP) || vdcResp.getHostIPv6AddressesMap().containsValue(virtualIP)) {
            throw GeoException.fatals.wrongIPSpecification(vdcInfo.getProperty(GeoServiceJob.VDC_NAME));
        }

        log.info("Check vdc stable");
        // check if the cluster is stable
        if (!vdcResp.isClusterStable()) {
            throw GeoException.fatals.unstableVdcFailure(vdcInfo.getProperty(GeoServiceJob.VDC_NAME));
        }
        URI unstable = checkAllVdcStable(false, false);
        if (unstable != null) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, unstable);
            String vdcName = (vdc != null) ? vdc.getLabel() : "";
            throw GeoException.fatals.unstableVdcFailure(vdcName);
        }

        log.info(
                "vdc config retrieved: vip={}, IPv4Addresses={}, IPv6Addresses={} isHasData={}",
                new Object[] { vdcResp.getApiEndpoint(), vdcResp.getHostIPv4AddressesMap(), vdcResp.getHostIPv6AddressesMap(),
                        vdcResp.isHasData() });
        if (vdcResp.isHasData()) {
            throw GeoException.fatals.remoteVDCContainData();
        }

        // verify the software version compatibility
        if (!isGeoCompatible(vdcResp)) {
            throw GeoException.fatals.remoteVDCInLowerVersion();
        }

        if (hasTripleVdcVersionsInFederation(vdcResp)) {
            throw GeoException.fatals.hasTripleVDCVersionsInFederation();
        }

        if (!isCompatibleVersion(vdcResp)) {
            throw GeoException.fatals.remoteVDCIncompatibleVersion();
        }

        if (!checkNodeConnectivity(vdcResp)) {
            throw GeoException.fatals.failedToCheckConnectivity(errMsg);
        }

        return vdcResp;
    }

    private String checkNetworkTopology(VdcPreCheckResponse vdcBeingAdded) {
        SoftwareVersion remoteVer = new SoftwareVersion(vdcBeingAdded.getSoftwareVersion());

        if (remoteVer.compareTo(netcheckMinVer) >= 0) {
            String nodeId = this.dbClient.getCoordinatorClient().getPropertyInfo().getProperty("node_id");

            log.info("Retrieving IP addresses of local node: {}, and let remote VDC {} check if we're behind a NAT",
                    nodeId, vdcBeingAdded.getShortId());

            DualInetAddress inetAddress = this.dbClient.getCoordinatorClient().getInetAddessLookupMap().getDualInetAddress();
            String ipv4 = inetAddress.getInet4();
            String ipv6 = inetAddress.getInet6();

            log.info("Got local node's IP addresses, IPv4 = {}, IPv6 = {}", ipv4, ipv6);

            VdcNatCheckParam checkParam = new VdcNatCheckParam();
            checkParam.setIPv4Address(ipv4);
            checkParam.setIPv6Address(ipv6);
            VdcNatCheckResponse resp = geoClientCache.getGeoClient(vdcInfo).vdcNatCheck(checkParam);
            if (resp.isBehindNAT()) {
                return String
                        .format("The remote VDC %s seen this node's IP is %s, which is different from what we think: %s or %s, we may behind a NAT",
                                vdcBeingAdded.getShortId(), resp.getSeenIp(), ipv4, ipv6);
            }
        } else {
            log.info("Remote VDC is of version {}, lower than {}, NAT check skipped.", remoteVer, netcheckMinVer);
        }

        return null;
    }

    private boolean checkNodeConnectivity(VdcPreCheckResponse vdcBeingAdded) {

        // check to make sure the current vdc can connect to all nodes on the vdc being added
        if (!helper.areNodesReachable(vdcBeingAdded.getShortId(), vdcBeingAdded.getHostIPv4AddressesMap(),
                vdcBeingAdded.getHostIPv6AddressesMap(), false)) {
            errMsg = String.format("Current vdc, %s, cannot reach all nodes of vdc being added", myVdc.getLabel());
            log.error(errMsg);
            return false;
        }

        // check to make sure the vdc being added can connect to the current and all other vdc's
        log.info("sending node check request to vdc: {}", vdcInfo.getProperty(GeoServiceJob.OPERATED_VDC_ID));
        VdcNodeCheckResponse vdcResp = sendVdcNodeCheckRequest(vdcInfo, connectedVdc);

        if (!vdcResp.isNodesReachable()) {
            errMsg = "vdc to be added cannot connect to all nodes of at least one of the vdcs in the federation";
            log.error(errMsg);
            return false;
        }

        // check to make sure the vdc being added can connect to the current and all other vdc's
        List<VdcConfig> vdcConfigs = new ArrayList<VdcConfig>();
        vdcConfigs.add(mergeVdcInfo(vdcBeingAdded));
        for (VirtualDataCenter other : connectedVdc) {
            if (other.getShortId().equals(myVdc.getShortId())) {
                continue;
            }
            log.info("sending node check request to vdc: {}", other.getShortId());
            vdcResp = sendVdcNodeCheckRequest(GeoServiceHelper.getVDCInfo(other), vdcConfigs);
            if (!vdcResp.isNodesReachable()) {
                errMsg = String.format("vdc, %s, cannot connect to all nodes of the vdc being added", other.getLabel());
                log.error(errMsg);
                return false;
            }
        }

        errMsg = checkNetworkTopology(vdcBeingAdded);
        if (errMsg != null && errMsg.length() > 0) {
            return false;
        }

        return true;
    }

    /**
     * Send node check request to target vdc.
     * 
     * @param vdcProp vdc to send msg to
     * @param vdcsToCheck list of vdc's with nodes to check
     * @return
     * @throws Exception
     */
    private VdcNodeCheckResponse sendVdcNodeCheckRequest(Properties vdcProp, Collection<VirtualDataCenter> vdcsToCheck) {
        List<VdcConfig> virtualDataCenters = new ArrayList<VdcConfig>();
        for (VirtualDataCenter vdc : vdcsToCheck) {
            VdcConfig vdcConfig = new VdcConfig();
            Site activeSite = drUtil.getActiveSite(vdc.getShortId());
            log.info("Active site {}", activeSite.getHostIPv4AddressMap());
            log.info("Active site for vdc {}", vdc.getShortId());
            
            vdcConfig.setId(vdc.getId());
            vdcConfig.setShortId(vdc.getShortId());
            if (activeSite.getHostIPv4AddressMap() != null && !activeSite.getHostIPv4AddressMap().isEmpty()) {
                HashMap<String, String> ipv4AddrMap = new HashMap<String, String>(activeSite.getHostIPv4AddressMap());
                vdcConfig.setHostIPv4AddressesMap(ipv4AddrMap);
            } else if (activeSite.getHostIPv6AddressMap() != null && !activeSite.getHostIPv6AddressMap().isEmpty()) {
                HashMap<String, String> ipv6AddrMap = new HashMap<String, String>(activeSite.getHostIPv6AddressMap());
                vdcConfig.setHostIPv6AddressesMap(ipv6AddrMap);
            } else {
                throw GeoException.fatals
                        .cannotPerformOperation(vdc.getId().toString(), " no nodes were found on VirtualDataCenter object");
            }
            virtualDataCenters.add(vdcConfig);
        }
        return sendVdcNodeCheckRequest(vdcProp, virtualDataCenters);
    }

    /**
     * @param remoteVdcInfo
     * @param virtualDataCenters
     * @return
     */
    private VdcNodeCheckResponse sendVdcNodeCheckRequest(Properties remoteVdcInfo, List<VdcConfig> virtualDataCenters) {
        log.info("sending {} vdcs to {} to be checked", virtualDataCenters.size(), remoteVdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID));
        VdcNodeCheckParam param = new VdcNodeCheckParam();
        param.setVirtualDataCenters(virtualDataCenters);
        try {
            GeoServiceClient client = helper.resetGeoClientCacheTimeout(null, remoteVdcInfo, NODE_CHECK_TIMEOUT);
            return client.vdcNodeCheck(param);
        } finally {
            // clear the client cache to reset timeouts that were altered above
            geoClientCache.clearCache();
        }
    }

    private VdcConfigSyncParam configMerge(VdcPreCheckResponse operatedVdcInfo, String ipsecKey) {
        // step 2: merge the vdc config info of all sites, as the initiator, we should has all current vdc config info
        VdcConfigSyncParam vdcConfigList = new VdcConfigSyncParam();
        vdcConfigList.setVdcConfigVersion(DrUtil.newVdcConfigVersion());
        List<VdcConfig> list = vdcConfigList.getVirtualDataCenters();

        for (VirtualDataCenter vdc : getAllVdc()) {
            if (vdc.getShortId().equals(vdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID))) {
                continue;
            }
            log.info("add {} to the merged vdc config", vdc.getShortId());
            VdcConfig vdcConfig = helper.toConfigParam(vdc);
            list.add(vdcConfig);
        }
        VdcConfig operatedConfig = mergeVdcInfo(operatedVdcInfo);
        list.add(operatedConfig);
        vdcConfigList.setIpsecKey(ipsecKey);
        return vdcConfigList;
    }

    private void configSync(VdcConfigSyncParam mergedVdcInfo) throws Exception {
        // step 3: sync merged vdc config info to all sites in which property change triggered, all conf files updated after reboot.
        // the vdc to be connected will reset the db and update the network strategy when startup.

        // loop all current connected VDCs with latest vdc config info, shall be moved into geoclient
        // geoclient shall responsible to retry all retryable errors, we have no need retry here

        log.info("sync vdc config to all sites, total vdc entries {}", mergedVdcInfo.getVirtualDataCenters().size());

        List<VirtualDataCenter> vdcList = getToBeSyncedVdc();
        for (VirtualDataCenter vdc : vdcList) {
            log.info("Loop vdc {}:{} to sync the latest vdc config info", vdc.getShortId(), vdc.getApiEndpoint());
            if (vdc.getApiEndpoint() != null) {
                URI vdcId = operatedVdc.getId();
                if (vdc.getId().equals(vdcId)) {  // vdc UUID assigned
                    mergedVdcInfo.setAssignedVdcId(vdcId.toString());
                    mergedVdcInfo.setGeoEncryptionKey(helper.getGeoEncryptionKey());
                } else {
                    mergedVdcInfo.setAssignedVdcId(null);
                    mergedVdcInfo.setGeoEncryptionKey(null);
                }
                mergedVdcInfo.setConfigChangeType(changeType().toString());
                geoClientCache.getGeoClient(vdc.getShortId()).syncVdcConfig(mergedVdcInfo, vdc.getLabel());
                log.info("Sync vdc info succeed");
            } else {
                log.error("Fatal error: try to sync with a vdc without endpoint");
            }
        }

        helper.addVdcToCassandraStrategyOptions(mergedVdcInfo.getVirtualDataCenters(), operatedVdc, false);

        // notify local vdc to apply the new vdc config info
        helper.syncVdcConfig(mergedVdcInfo.getVirtualDataCenters(), null,
                mergedVdcInfo.getVdcConfigVersion(), mergedVdcInfo.getIpsecKey());

        // update the current progress of connect vdc. the cluster would reboot later.
        updateOpStatus(ConnectionStatus.CONNECTING_SYNCED);
        
        helper.triggerVdcConfigUpdate(mergedVdcInfo.getVdcConfigVersion(), SiteInfo.NONE);
    }

    private void postCheck() {
        log.info("vdc config info already synced to all connected vdc, start post check");
        if (myVdc == null) {
            getMyVdcId();
        }

        // step 4: wait for the gossip status
        dbClient.waitAllSitesDbStable();

        // reload operated vdc from db
        operatedVdc = dbClient.queryObject(VirtualDataCenter.class, operatedVdc.getId());
        Site activeSite = drUtil.getActiveSite(operatedVdc.getShortId());
        // check if network strategy updated successfully
        dbClient.waitDbRingRebuildDone(operatedVdc.getShortId(), activeSite.getNodeCount());
    }

    private void statusUpdate() {
        // step 5: update vdc connection status if post check succeed at each site

        List<VirtualDataCenter> vdcList = getToBeSyncedVdc();

        // fill the check param
        VdcPostCheckParam checkParam = new VdcPostCheckParam();
        checkParam.setConfigChangeType(changeType().toString());
        checkParam.setRootTenantId(helper.getVdcRootTenantId());

        // all connected vdc
        List<URI> vdcIds = new ArrayList<>();
        for (VirtualDataCenter vdc : vdcList) {
            vdcIds.add(vdc.getId());
        }
        vdcIds.add(myVdc.getId());
        checkParam.setVdcList(vdcIds);

        log.info("status update to {}", checkParam.getVdcList());

        sendPostCheckMsg(vdcList, checkParam);
    }

    private VdcConfig mergeVdcInfo(VdcPreCheckResponse vdcResp) {
        log.info("add to be added vdc {} to the merged vdc config", vdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID));
        VdcConfig vdcConfig = helper.toConfigParam(vdcInfo);

        // Following items should be set according to remote site
        log.info("get from remote {} {}", vdcResp.getHostCount(), vdcResp.getShortId());
        vdcConfig.setHostCount(vdcResp.getHostCount());
        vdcConfig.setHostIPv4AddressesMap(vdcResp.getHostIPv4AddressesMap());
        vdcConfig.setHostIPv6AddressesMap(vdcResp.getHostIPv6AddressesMap());
        if (operatedVdc == null) {
            vdcConfig.setConnectionStatus(VirtualDataCenter.ConnectionStatus.CONNECTING.toString());
            vdcConfig.setRepStatus(VirtualDataCenter.GeoReplicationStatus.REP_NONE.toString());
        }
        else {
            vdcConfig.setConnectionStatus(operatedVdc.getConnectionStatus().toString());
            vdcConfig.setRepStatus(operatedVdc.getRepStatus().toString());
        }

        Date addDate = new Date();
        vdcConfig.setVersion(addDate.getTime()); // notify the vdc to pick up the latest info
        vdcConfig.setActiveSiteId(vdcResp.getActiveSiteId());
        return vdcConfig;
    }

    /**
     * Check to be added vdc whether in compatible version
     */
    private boolean isCompatibleVersion(VdcPreCheckResponse vdcResp) {
        final SoftwareVersion version;
        String shortId = vdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID);
        try {
            log.info("software version of vdc {} is {}", shortId, vdcResp.getSoftwareVersion());
            version = new SoftwareVersion(vdcResp.getSoftwareVersion());
        } catch (InvalidSoftwareVersionException e) {
            log.error("software version of vdc {} is incorrect", shortId);
            return false;
        }

        SoftwareVersion myVersion = null;
        try {
            myVersion = dbClient.getCoordinatorClient().getTargetInfo(RepositoryInfo.class).getCurrentVersion();
        } catch (Exception e) {
            String errMsg = "Not able to get the software version of current vdc";
            log.error(errMsg, e);
            throw GeoException.fatals.connectVdcPrecheckFail(shortId, errMsg);
        }
        log.info("Software version of current vdc: {}", myVersion.toString());

        if (myVersion.compareTo(version) == 0) {
            log.info("software version equals, pass the version check");
        } else if (myVersion.compareTo(version) < 0) {
            log.info("to be added vdc has larger version");
            if (!vdcResp.getCompatible()) {
                log.error("vdc to be added has larger version but incompatible with current vdc");
                return false;
            }
        } else {
            log.info("to be added vdc has smaller version");
            if (!helper.isCompatibleVersion(version)) {
                log.error("vdc to be added has smaller version but incompatible with current vdc");
                return false;
            }
        }
        return true;
    }

    private boolean isGeoCompatible(VdcPreCheckResponse vdcResp) {
        String expectVersion = VdcUtil.getDbSchemaVersion(vdcResp.getSoftwareVersion());
        if (expectVersion == null) {
            return false;
        }
        log.info("Compare Vdc version: {}", expectVersion);
        String minimalVdcVersion = VdcUtil.getMinimalVdcVersion();
        boolean isGeoCompatible = VdcUtil.VdcVersionComparator.compare(expectVersion, minimalVdcVersion) >= 0;
        if (!isGeoCompatible) {
            log.error("The vdc version of the vdc to be added must be not less than current federation.");
        }
        return isGeoCompatible;
    }

    /**
     * Check if we'll have 3 vdc versions after adding the vdc with given version
     * 
     * @param vdcResp
     * @return true if there are 3 vdc versions
     */
    private boolean hasTripleVdcVersionsInFederation(VdcPreCheckResponse vdcResp) {
        Set<String> allVersions = new HashSet<>();
        allVersions.add(VdcUtil.getDbSchemaVersion(vdcResp.getSoftwareVersion()));
        List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
        List<URI> vdcVersionIds = dbClient.queryByType(VdcVersion.class, true);
        List<VdcVersion> vdcVersions = dbClient.queryObject(VdcVersion.class,
                vdcVersionIds);
        Map<URI, VdcVersion> vdcIdVdcVersionMap = new HashMap<>();
        for (VdcVersion vdcVersion : vdcVersions) {
            vdcIdVdcVersionMap.put(vdcVersion.getVdcId(), vdcVersion);
        }

        for (URI vdcId : vdcIds) {
            if (vdcIdVdcVersionMap.containsKey(vdcId)) {
                String schemaVersion = vdcIdVdcVersionMap.get(vdcId)
                        .getVersion();
                log.info("Get vdc version {} on {}", schemaVersion, vdcId);
                allVersions.add(schemaVersion);
            } else {
                log.info(
                        "Can not get vdc version on {}, will use default version instead",
                        vdcId);
                allVersions.add(DbConfigConstants.DEFAULT_VDC_DB_VERSION);
            }
        }

        log.info("Current vdc versions in federation {}", allVersions);
        boolean hasTriple = allVersions.size() > 2;
        if (hasTriple) {
            log.error("Not allowed to have three different vdc versions in a federation.");
        }
        return hasTriple;
    }

    @Override
    protected void process() {
        String errMsg;
        log.info("The operatedVdcStatus={}", operatedVdcStatus);
        switch (operatedVdcStatus) {
            case CONNECT_FAILED:
                errMsg = String.format("Adding vdc operation failed already on %s, skip all other steps", operatedVdc.getId());
                log.error(errMsg);
                throw GeoException.fatals.connectVdcInvalidStatus(errMsg);
            case CONNECTING:
                checkAndSync(apiSignatureGenerator, keystore);
            case CONNECTING_SYNCED:
                // vdc config info already synced to all connected vdc, check if connect succeed
                postSteps();
                break;
            default:
                errMsg = "Vdc to be added in unexpected status, skip all other steps";
                log.error(errMsg);
                log.info("target vdc status: {}", operatedVdcStatus);
                throw GeoException.fatals.connectVdcInvalidStatus(errMsg);
        }
    }

    @Override
    public VdcConfig.ConfigChangeType changeType() {
        return VdcConfig.ConfigChangeType.CONNECT_VDC;
    }
}
