/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.vdccontroller.impl;

import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.security.geo.exceptions.FatalGeoException;
import com.emc.storageos.security.geo.GeoServiceJob;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.geomodel.VdcConfig;
import com.emc.storageos.geomodel.VdcCertListParam;
import com.emc.storageos.geomodel.VdcPreCheckResponse2;
import com.emc.storageos.geomodel.VdcPreCheckParam2;
import com.emc.storageos.geomodel.VdcNodeCheckResponse;
import com.emc.storageos.geomodel.VdcPostCheckParam;
import com.emc.storageos.geomodel.VdcCertParam;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.security.geo.GeoServiceHelper;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.geomodel.VdcConfigSyncParam;
import com.emc.storageos.geomodel.VdcPreCheckParam;
import com.emc.storageos.geomodel.VdcPreCheckResponse;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/*
 * Detail implementation of vdc connect operation
 */
public abstract class AbstractVdcTaskOp {

    private final static Logger log = LoggerFactory.getLogger(AbstractVdcTaskOp.class);

    protected final static int DEFAULT_NODE_CHECK_TIMEOUT = 30 * 1000; // 30 seconds

    protected VirtualDataCenter operatedVdc;
    protected VirtualDataCenter.ConnectionStatus operatedVdcStatus;
    protected VirtualDataCenter.ConnectionStatus failedVdcStatus;
    protected VirtualDataCenter myVdc;
    protected String operateTaskId;
    protected String myVdcId;
    protected List<VirtualDataCenter> connectedVdc = new ArrayList<>();
    protected List<VirtualDataCenter> allVdc = new ArrayList<>();
    protected List<VirtualDataCenter> toBeSyncedVdc = new ArrayList<>();
    protected Properties vdcInfo;

    protected InternalDbClient dbClient;
    protected GeoClientCacheManager geoClientCache;
    protected VdcConfigHelper helper;
    protected VdcOperationLockHelper lockHelper;
    protected DrUtil drUtil;
    
    protected KeyStore keystore;
    protected IPsecConfig ipsecConfig;

    protected String errMsg;
    
    private final static String VIPR_INVALID_VERSION_PREFIX = "vipr-2.0";

    protected final static SoftwareVersion vdcVersionCheckMinVer = new SoftwareVersion("2.3.0.0.*");

    // TODO we have so many constructor arguments here. refactor it later
    protected AbstractVdcTaskOp(InternalDbClient dbClient, GeoClientCacheManager geoClientCache,
            VdcConfigHelper helper, Service serviceInfo, VirtualDataCenter vdc,
            String taskId, Properties vdcInfo, KeyStore keystore, IPsecConfig ipsecConfig) {

        this.dbClient = dbClient;
        this.geoClientCache = geoClientCache;
        this.helper = helper;
        operatedVdc = vdc;
        operateTaskId = taskId;
        operatedVdcStatus = vdc.getConnectionStatus();
        failedVdcStatus = getDefaultPrecheckFailedStatus();
        this.keystore = keystore;
        this.ipsecConfig = ipsecConfig;
        if (vdcInfo == null) {
            vdcInfo = GeoServiceHelper.getVDCInfo(operatedVdc);
        }
        this.vdcInfo = vdcInfo;
        drUtil = new DrUtil(dbClient.getCoordinatorClient());
    }

    public void setLockHelper(VdcOperationLockHelper lockHelper) {
        this.lockHelper = lockHelper;
    }

    protected void loadVdcInfo() {
        toBeSyncedVdc.clear();
        allVdc.clear();
        connectedVdc.clear();
        List<URI> vdcIdIter = dbClient.queryByType(VirtualDataCenter.class, true);

        boolean isolated = false;
        List<VirtualDataCenter> allOtherVdc = new ArrayList<>();
        for (URI vdcId : vdcIdIter) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
            if (vdc.getLocal()) {
                myVdc = vdc;
                myVdcId = myVdc.getId().toString();
                // self connected
                connectedVdc.add(myVdc);
                allVdc.add(myVdc);
                if ((myVdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.ISOLATED) ||
                        (myVdc.getRepStatus() == VirtualDataCenter.GeoReplicationStatus.REP_NONE)) {
                    isolated = true;
                }
            } else {
                allOtherVdc.add(vdc);
            }
        }

        if (!isolated) {
            for (VirtualDataCenter vdc : allOtherVdc) {
                if (vdc.getRepStatus() != VirtualDataCenter.GeoReplicationStatus.REP_NONE) {
                    allVdc.add(vdc);
                    if (vdc.getConnectionStatus() != VirtualDataCenter.ConnectionStatus.DISCONNECTED) {
                        connectedVdc.add(vdc);
                        toBeSyncedVdc.add(vdc);
                    }
                }
            }
        }

        if (operatedVdc != null && !operatedVdc.getId().equals(myVdc.getId()) && !doesContainOperatedVdc()) {
            toBeSyncedVdc.add(operatedVdc); // toBeSyncedVdc does not include local
        }

        log.info("toBeSyncedVdc:{} connectedVdc: {}", toBeSyncedVdc, connectedVdc);
    }

    /*
     * use for set up toBeSyncedVdc only
     */
    private boolean doesContainOperatedVdc() {
        for (VirtualDataCenter vdc : toBeSyncedVdc) {
            if (vdc.getId().equals(operatedVdc.getId())) {
                return true;
            }
        }

        return false;
    }

    protected String getMyVdcId() {
        if (myVdcId == null) {
            loadVdcInfo();
        }
        return myVdcId;
    }

    protected List<VirtualDataCenter> getAllVdc() {
        if (myVdcId == null) {
            loadVdcInfo();
        }
        return allVdc;
    }

    protected List<VirtualDataCenter> getToBeSyncedVdc() {
        if (myVdcId == null) {
            loadVdcInfo();
        }
        return toBeSyncedVdc;
    }

    protected void updateOpStatus(VirtualDataCenter.ConnectionStatus status) {
        operatedVdc.setConnectionStatus(status);
        dbClient.updateAndReindexObject(operatedVdc);
    }

    protected void completeVdcAsyncTask(Operation.Status status, ServiceCoded coded) {
        try {
            List<Task> tasks = TaskUtils.findTasksForRequestId(dbClient, operateTaskId);
            URI vdcId = tasks.get(0).getResource().getURI();
            switch (status) {
                case error:
                    // mark the task as failed
                    dbClient.error(VirtualDataCenter.class, vdcId, operateTaskId, coded);
                    break;
                default:
                    dbClient.ready(VirtualDataCenter.class, vdcId, operateTaskId);
            }
            // TODO: add audit log here
            log.info("Done vdc Op {}, with Status: {}", operateTaskId, status.name());
        } catch (Exception e) {
            log.error("Failed updating status, for task " + operateTaskId, e);
        }
    }

    protected VdcCertListParam genCertOperationParam(String cmd) {
        Certificate cert = null;
        VdcCertListParam certsParam = new VdcCertListParam();
        certsParam.setCmd(cmd);
        certsParam.setTargetVdcId(operatedVdc.getId().toString());
        Certificate[] chain = null;
        try {
            String certChainOfOpeartedVdc = operatedVdc.getCertificateChain();
            if (certChainOfOpeartedVdc == null) {
                log.info("certChain for reconnected vdc is null, will find certificate in Keystore");
                cert = keystore.getCertificate(operatedVdc.getId().toString());
            } else {
                chain = KeyCertificatePairGenerator.getCertificateChainFromString(certChainOfOpeartedVdc);
                cert = chain[0];
            }
            certsParam.setTargetVdcCert(KeyCertificatePairGenerator.getCertificateAsString(cert));
        } catch (KeyStoreException ex) {
            log.error("Failed to get key from the keyStore at VDC " + operatedVdc.getLabel());
            throw GeoException.fatals.keyStoreFailure(operatedVdc.getLabel(), ex);
        } catch (CertificateException ex) {
            log.error("Failed to get proper certificate on VDC " + operatedVdc.getLabel());
            throw GeoException.fatals.connectVdcSyncCertFail(operatedVdc.getLabel(), ex);
        }

        return certsParam;
    }

    protected void syncCerts(String cmd, VdcCertListParam certListParam) {
        // loop all VDCs with latest vdcs certs info
        // geoclient shall responsible to retry all retryable errors, we have no need retry here
        log.info("syncing vdcs certs to all sites ...");
        List<VirtualDataCenter> vdcList = getToBeSyncedVdc();
        for (VirtualDataCenter vdc : vdcList) {
            log.info("Loop {}:{} to sync the latest vdc cert info", vdc.getShortId(), vdc.getApiEndpoint());
            syncCertForSingleVdc(certListParam, vdc);
        }

        log.info("Finished sync vdc certs to all sites.");
    }

    protected void syncCertForSingleVdc(VdcCertListParam certListParam, VirtualDataCenter vdc) {
        if (myVdcId.equals(vdc.getId().toString())) {
            log.info("Skip syncing cert to the local vdc {}: already done.", vdc.getShortId());
            return;
        }

        if (vdc.getApiEndpoint() != null) {
            geoClientCache.getGeoClient(vdc.getShortId()).syncVdcCerts(certListParam, vdc.getLabel());
            log.info("Sync vdc certs info succeed");
        } else {
            log.error("Fatal error: try to sync certs with a vdc without endpoint");
        }
    }

    /**
     * Build parameter for SyncVdcConfig call
     * 
     * @param vdcList
     * @return
     */
    protected VdcConfigSyncParam buildConfigParam(List<VirtualDataCenter> vdcList) {
        VdcConfigSyncParam syncParam = new VdcConfigSyncParam();

        syncParam.setVdcConfigVersion(DrUtil.newVdcConfigVersion());
        syncParam.setIpsecKey(ipsecConfig.getPreSharedKeyFromZK());

        for (VirtualDataCenter vdc : vdcList) {
            syncParam.getVirtualDataCenters().add(helper.toConfigParam(vdc));
        }

        syncParam.setConfigChangeType(changeType().toString());

        return syncParam;
    }

    public void handle() {
        ServiceCoded coded = null;
        try {
            process();
            success();
        } catch (GeoException e) {
            coded = ServiceError.buildServiceError(e.getServiceCode(), e.getMessage());
            fail(coded);
            throw e;
        } catch (Exception e) {
            log.error("Vdc task failed e=", e);
            String err = "An unexpected error happens:" + e;
            coded = ServiceError.buildServiceError(ServiceCode.GEOSVC_INTERNAL_ERROR, err);
            fail(coded);
            throw e;
        }
    }

    // process the task
    protected abstract void process();

    // changeType
    public abstract VdcConfig.ConfigChangeType changeType();

    // the callback if the task success
    protected void success() {
        // update task status
        log.info("Task {}:{} success", this.getClass().getName(), operateTaskId);
        completeVdcAsyncTask(Operation.Status.ready, null);
    }

    // the callback if the task failed
    protected void fail(ServiceCoded coded) {
        try {
            log.error("Task {}:{} failed {}", new Object[] { this.getClass().getName(),
                    operateTaskId, isPreCheckFailStatus() ? "during precheck" : "" });

            if (operatedVdc != null) {
                if (!isPreCheckFailStatus()) {
                    operatedVdc.setConnectionStatus(failedVdcStatus);
                }
                else {
                    // TODO remove connection status as locking mechanism
                    // in the situation when connection status was used temporarily as a lock to prevent
                    // duplicate VDC procedures, replace the status with its original value
                    operatedVdc.setConnectionStatus(operatedVdcStatus);
                }
                dbClient.updateAndReindexObject(operatedVdc);
            }
            if (coded != null) {
                completeVdcAsyncTask(Operation.Status.error, coded);
            } else {
                completeVdcAsyncTask(Operation.Status.error, null);
            }
        } finally {
            if (lockHelper != null) {
                lockHelper.release(vdcInfo.getProperty(GeoServiceJob.VDC_SHORT_ID));
            }
        }

    }

    private boolean isPreCheckFailStatus() {
        return (failedVdcStatus == ConnectionStatus.CONNECT_PRECHECK_FAILED) ||
                (failedVdcStatus == ConnectionStatus.DISCONNECT_PRECHECK_FAILED) ||
                (failedVdcStatus == ConnectionStatus.RECONNECT_PRECHECK_FAILED) ||
                (failedVdcStatus == ConnectionStatus.UPDATE_PRECHECK_FAILED) ||
                (failedVdcStatus == ConnectionStatus.REMOVE_PRECHECK_FAILED);
    }

    /**
     * Send precheck request to target vdc.
     * 
     * @param vdcProp - vdc properties
     * @return VdcPreCheckResponse from target vdc
     */
    protected VdcPreCheckResponse sendVdcPrecheckRequest(Properties vdcProp, boolean isFresher) {
        VdcPreCheckParam param = new VdcPreCheckParam();
        param.setFresher(isFresher);
        param.setConfigChangeType(changeType().toString());
        try {
            param.setSoftwareVersion(dbClient.getCoordinatorClient().getTargetInfo(RepositoryInfo.class).getCurrentVersion().toString());
        } catch (Exception ex) {
            log.error("Fail to get version info for preCheck. {}", ex.getMessage());
        }
        String vdcName = vdcProp.getProperty(GeoServiceJob.VDC_NAME);
        return geoClientCache.getGeoClient(vdcProp).syncVdcConfigPreCheck(param, vdcName);
    }

    /**
     * Send precheck2 request to target vdc.
     * 
     * @param targetVdc
     * @return VdcPreCheckResponse from target vdc
     */
    protected VdcPreCheckResponse2 sendVdcPrecheckRequest2(VirtualDataCenter targetVdc, VdcPreCheckParam2 param, int nodeCheckTimeout_ms) {
        try {
            GeoServiceClient client = helper.resetGeoClientCacheTimeout(targetVdc.getShortId(), null, nodeCheckTimeout_ms);
            return client.syncVdcConfigPreCheck(param, targetVdc.getLabel());
        } finally {
            geoClientCache.clearCache();
        }
    }

    /**
     * Make sure the living vdcs are stable.
     * 
     * @param checkOperatedVdc
     * @param ignoreException
     * @return the unstable vdc otherwise null
     */
    protected URI checkAllVdcStable(boolean ignoreException, boolean checkOperatedVdc) {
        log.info("Checking to see if vdcs involved are stable.");

        // check if the cluster is stable
        if (!helper.isClusterStable()) {
            log.error("the local vdc " + myVdc.getShortId() + " is not stable.");
            return myVdc.getId();
        }

        if (changeType() != VdcConfig.ConfigChangeType.DISCONNECT_VDC &&
                checkOperatedVdc &&
                !checkVdcStable(vdcInfo, ignoreException)) {
            return URI.create(vdcInfo.getProperty(GeoServiceJob.OPERATED_VDC_ID));
        }

        // Go through the connected list
        for (VirtualDataCenter vdc : connectedVdc) {
            if (vdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.DISCONNECTED ||
                    // skip the disconnected/disconnecting VDC
                    vdc.getId().toString().equals(vdcInfo.getProperty(GeoServiceJob.OPERATED_VDC_ID)) ||
                    // skip local
                    vdc.getId() == myVdc.getId()) {
                // skip remote / operated since it was validated already
                continue;

            }
            if (!checkVdcStable(GeoServiceHelper.getVDCInfo(vdc), ignoreException)) {
                return vdc.getId();
            }
        }
        return null;
    }

    private boolean checkVdcStable(Properties info, boolean ignoreException) {
        String shortId = info.getProperty(GeoServiceJob.VDC_SHORT_ID);
        try {
            return geoClientCache.getGeoClient(shortId).isVdcStable();
        } catch (Exception e) {
            log.error("the vdc being checked does not meet the requirement");
            if (!ignoreException) {
                throw e;
            }
            return false;
        }
    }

    /**
     * Make sure the vdc is reachable from any of the
     * connected VDCs
     */
    protected boolean isTargetVdcReachable(int nodeCheckTimeout_ms) {
        log.info("Checking to see if the vdc {} is reachable from connected VDCs", operatedVdc.getShortId());

        // Go through the connected list
        for (VirtualDataCenter vdc : connectedVdc) {

            if (vdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.DISCONNECTED)
            {
                continue; // skip the disconnected VDC
            }

            if (vdc.getId().equals(operatedVdc.getId()))
            {
                continue; // skip the VDC to be disconnected
            }

            if (vdc.getLocal()) {
                Site activeSite = drUtil.getActiveSite(operatedVdc.getShortId());
                if (helper.areNodesReachable(vdc.getShortId(), activeSite.getHostIPv4AddressMap(),
                        activeSite.getHostIPv6AddressMap(), true)) {
                    return true;
                }
                continue;
            }

            // non-local vdcs
            VdcPreCheckParam2 checkParam2 = new VdcPreCheckParam2();
            checkParam2.setConfigChangeType(VdcConfig.ConfigChangeType.DISCONNECT_VDC);

            List<URI> vdcIds = new ArrayList(1);
            vdcIds.add(operatedVdc.getId());
            checkParam2.setVdcIds(vdcIds);
            checkParam2.setIsAllNotReachable(true);

            try {
                VdcPreCheckResponse2 resp2 = sendVdcPrecheckRequest2(vdc, checkParam2, nodeCheckTimeout_ms);
                if (!resp2.getIsAllNodesNotReachable()) {
                    errMsg = String.format("The vdc %s to be disconnected is still reachable from %s", operatedVdc.getShortId(),
                            vdc.getShortId());
                    log.error(errMsg);
                    return true;
                }
            } catch (Exception e) {
                log.error("Failed to check the operatedVdc {} on the vdc {} e=",
                        new Object[] { operatedVdc.getShortId(), vdc.getShortId(), e });
                continue;
            }

        }

        return false;
    }

    /**
     * Make sure all the connected(the status is connected, not the connectedVdc instance in this class ) vdc is
     * reachable by the targetVdc, there are two usages here:
     * 1. verify if the operatedVdc is back online and reachble with other connected vdcs,
     * 2. verify current vdc is reachable with other connected vdcs.
     */
    protected boolean isAllConnectedVdcReachableWith(VirtualDataCenter targetVdc) {
        log.info("Checking to see if the vdc {} is reachable with other connected VDCs", targetVdc.getShortId());

        // Go through the connected list
        for (VirtualDataCenter vdc : connectedVdc) {
            // Don't need to check if the target vdc is reachable with itself
            if (vdc.getId().equals(targetVdc.getId())) {
                continue;
            }
            VdcNodeCheckResponse resp = null;
            List<VirtualDataCenter> vdcs = new ArrayList(1);
            vdcs.add(targetVdc);
            try {
                // vdcResp = sendVdcCheckRequest(vdc, operatedVdc);
                resp = helper.sendVdcNodeCheckRequest(vdc, vdcs);
                if (!resp.isNodesReachable()) {
                    log.error("the vdc {} can not be reached by target Vdc {}", vdc.getShortId(), targetVdc.getShortId());
                    errMsg = String.format("The Vdc %s can not be reached by target Vdc %s", vdc.getId().toString(), targetVdc.getId()
                            .toString());
                    return false;
                }
            } catch (GeoException e) {
                errMsg = e.getMessage();
                return false;
            } catch (IllegalStateException e) {
                errMsg = e.getMessage();
                return false;
            }

        }

        return true;
    }

    /**
     * For disconnect and reconnect operations, every living connected vdc's version should at least 2.1 or higher.
     */
    protected boolean isVdcVersion20() {
        log.info("Checking to see if every living vdc's version is 2.0");

        // get geoClientCache to get the version for each vdc
        for (VirtualDataCenter vdc : connectedVdc) {
            if (vdc.getId().equals(operatedVdc.getId())) {
                continue;
            }
            if (vdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.CONNECTED) {
                String viPRVersion = helper.getViPRVersion(vdc.getShortId());
                if (viPRVersion.startsWith(VIPR_INVALID_VERSION_PREFIX)) {
                    errMsg = String.format("The vipr version for vdc %s is 2.0", vdc.getLabel());
                    log.info("The vipr version for vdc {} is 2.0", vdc.getShortId());
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * 
     * This function only used for reconnect and disconnect
     * Input parameter should be CONNECTED or DISCONNECTED
     * 
     * @param isSyncOperatedVdc, only used for reconnect operation to sync operated vdc, it will trigger a node repair.
     */
    protected void updateVdcStatus(VirtualDataCenter.ConnectionStatus status, boolean isSyncOperatedVdc) {
        updateOpStatus(status);

        VdcConfig.ConfigChangeType configChangeType;

        log.info("the connection status is {}", status);
        switch (status) {
            case CONNECTED:
                configChangeType = VdcConfig.ConfigChangeType.RECONNECT_VDC;
                break;
            case DISCONNECTED:
                configChangeType = VdcConfig.ConfigChangeType.DISCONNECT_VDC;
                break;
            default:
                throw FatalGeoException.fatals.vdcWrongStatus(status.toString());
        }

        for (VirtualDataCenter vdc : allVdc) {
            if (vdc.getId().equals(operatedVdc.getId()) && (configChangeType == VdcConfig.ConfigChangeType.RECONNECT_VDC)) {
                vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.CONNECTED);
                break;
            }
        }

        VdcConfigSyncParam syncParam = buildConfigParam(allVdc);
        syncParam.setAssignedVdcId(operatedVdc.getId().toString());
        List<VirtualDataCenter> vdcsToBeSynced = new ArrayList<>();
        if (isSyncOperatedVdc) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, operatedVdc.getId());
            vdcsToBeSynced.add(vdc);
            log.info("Update vdc config for operated vdc {}", operatedVdc);
        } else {
            vdcsToBeSynced = getToBeSyncedVdc();
            if (doesContainOperatedVdc()) {
                log.info("Remove operatedVdc {} from the list {}", operatedVdc, vdcsToBeSynced);
                vdcsToBeSynced = removeOperatedVdc(vdcsToBeSynced);
            }

            log.info("Update vdc status {} to connected vdcs={}", operatedVdc, vdcsToBeSynced);
        }

        sendSyncVdcConfigMsg(vdcsToBeSynced, syncParam);
    }

    /*
     * used for sync vdc config only. Disconnect and Reconnect don't need to sent sync vdc config with other living vdcs.
     */
    private List<VirtualDataCenter> removeOperatedVdc(List<VirtualDataCenter> vdcs) {
        List<VirtualDataCenter> vdcsToBeSynced = new ArrayList<>();
        for (VirtualDataCenter vdc : vdcs) {
            if (!vdc.getId().equals(operatedVdc.getId())) {
                vdcsToBeSynced.add(vdc);
            }
        }

        return vdcsToBeSynced;
    }

    protected void sendSyncVdcConfigMsg(List<VirtualDataCenter> vdcList, VdcConfigSyncParam syncParam) {
        for (VirtualDataCenter vdc : vdcList) {
            try {
                geoClientCache.getGeoClient(vdc.getShortId()).syncVdcConfig(syncParam, vdc.getLabel());
            } catch (Exception ex) {
                // TODO need to fix that
                // Ignore sync vdc config error for reconnect vdc task, since it need to restart the remote vdc
                // if there is any vdc config change. It will trigger socket read timeout exception.
                log.error("Failed to sync VDC {}, e = {}", vdc.getShortId(), ex);
                if (!(this instanceof ReconnectVdcTaskOp)) {
                    throw GeoException.fatals.syncConfigFail(ex);
                }
            }
        }
    }

    protected void sendPostCheckMsg(List<VirtualDataCenter> vdcList, VdcPostCheckParam checkParam) {
        for (VirtualDataCenter vdc : vdcList) {
            sendPostCheckMsg(checkParam, vdc);
        }
        checkParam.setFresher(false);
        // notify local vdc to do post steps either
        helper.syncVdcConfigPostSteps(checkParam);
    }

    /**
     * @param checkParam
     * @param vdc
     * @throws Exception
     */
    private void sendPostCheckMsg(VdcPostCheckParam checkParam, VirtualDataCenter vdc) {
        log.info("Loop vdc {}:{} to do the post check", vdc.getShortId(), vdc.getApiEndpoint());
        if (vdc.getApiEndpoint() != null) {
            if (vdc.getId().equals(operatedVdc.getId())) {
                checkParam.setFresher(true);
            } else {
                checkParam.setFresher(false);
            }
            geoClientCache.getGeoClient(vdc.getShortId()).syncVdcConfigPostCheck(checkParam, vdc.getLabel());
            log.info("Post check on vdc {} succeed", vdc.getShortId());
        } else {
            log.error("Fatal error: try to sync with a vdc without endpoint");
            throw GeoException.fatals.syncBadAPIVDC(vdc.getLabel());
        }
    }

    protected void notifyPrecheckFailed() {
        if (operatedVdc == null) {
            return;
        }
        for (VirtualDataCenter vdc : connectedVdc) {
            if (operatedVdc.getId().equals(vdc.getId()) || myVdcId.equals(vdc.getId().toString())) {
                continue; // Don't check on the vdc to be disconnected and myself
            }

            try {
                // BZ
                // TODO need to have a different REST call to modify state of a remote VDC; PrecheckRequest2 should be used to check remote
                // VDC.
                // TODO need to have a different locking mecanism to set lock against concurrent VDC operations.
                VdcPreCheckParam2 param = new VdcPreCheckParam2();
                param.setConfigChangeType(changeType());
                List<URI> ids = new ArrayList(1);
                ids.add(operatedVdc.getId());
                param.setVdcIds(ids);
                param.setPrecheckFailed(true);
                param.setDefaultVdcState(operatedVdcStatus.toString());
                sendVdcPrecheckRequest2(vdc, param, DEFAULT_NODE_CHECK_TIMEOUT);
            } catch (Exception ex) {
                log.error("Failed to notify vdc : {} that recheckFaled ", vdc.getShortId());
            }
        }
    }

    protected VdcCertListParam genCertListParam(String cmd) {
        log.info("generating certs sync parameter ...");

        VdcCertListParam certsParam = genCertOperationParam(cmd);

        // add certs of the current existing VDCs
        List<VdcCertParam> certs = certsParam.getVdcCerts();
        List<VirtualDataCenter> vdcList = getAllVdc();
        for (VirtualDataCenter vdc : vdcList) {
            if (!vdc.getId().equals(operatedVdc.getId())) {
                log.info("adding cert from vdc {} into sync param...", vdc.getId().toString());
                VdcCertParam certParam = new VdcCertParam();
                certParam.setVdcId(vdc.getId());

                try {
                    Certificate cert = null;
                    if (myVdc.getId().compareTo(vdc.getId()) == 0) {
                        log.info("it is local vdc {}", vdc.getId().toString());
                        Certificate[] certChain = null;
                        certChain = keystore.getCertificateChain(
                                KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
                        cert = certChain[0];
                    } else {
                        log.info("it is a remote vdc {}", vdc.getId().toString());
                        cert = keystore.getCertificate(vdc.getId().toString());
                    }
                    certParam.setCertificate(KeyCertificatePairGenerator.getCertificateAsString(cert));

                    certs.add(certParam);
                } catch (KeyStoreException ex) {
                    log.error("Failed to get key from the keyStore at VDC " + vdc.getLabel());
                    throw GeoException.fatals.keyStoreFailure(vdc.getLabel(), ex);
                } catch (CertificateException ex) {
                    log.error("Failed to get proper certificate on VDC " + vdc.getLabel());
                    throw GeoException.fatals.connectVdcSyncCertFail(vdc.getLabel(), ex);
                }
            }
        }
        return certsParam;
    }

    protected void removeVdcFromStrategyOption(boolean wait) {
        try {
            helper.removeStrategyOption(operatedVdc.getShortId(), wait);
        } catch (Exception e) {
            log.error("Failed to set strategy options e= ", e);
            throw GeoException.fatals.vdcStrategyFailed(e);
        }
    }

    protected VirtualDataCenter.ConnectionStatus getDefaultPrecheckFailedStatus() {
        VdcConfig.ConfigChangeType changeType = changeType();
        switch (changeType) {
            case CONNECT_VDC:
                return ConnectionStatus.CONNECT_PRECHECK_FAILED;
            case REMOVE_VDC:
                return ConnectionStatus.REMOVE_PRECHECK_FAILED;
            case UPDATE_VDC:
                return ConnectionStatus.UPDATE_PRECHECK_FAILED;
            case DISCONNECT_VDC:
                return ConnectionStatus.DISCONNECT_PRECHECK_FAILED;
            case RECONNECT_VDC:
                return ConnectionStatus.RECONNECT_PRECHECK_FAILED;
            default:
                return ConnectionStatus.UPDATE_PRECHECK_FAILED;
        }
    }

    protected boolean isRemoteVdcVersionCompatible(Properties vdcInfo) {
        boolean isCompatible = true;

        SoftwareVersion remoteSoftVer = new SoftwareVersion(helper.getViPRVersion(vdcInfo));
        log.info("Remote version is {}", remoteSoftVer);
        if (vdcVersionCheckMinVer.compareTo(remoteSoftVer) >= 0) {
            log.info("Software version from remote vdc is lower than v2.3.");
            isCompatible = false;
        }

        return isCompatible;
    }
}
