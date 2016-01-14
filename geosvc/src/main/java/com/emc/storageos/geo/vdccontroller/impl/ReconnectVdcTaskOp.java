/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.vdccontroller.impl;

import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.emc.storageos.geomodel.VdcCertListParam;
import com.emc.storageos.geomodel.VdcPreCheckParam2;
import com.emc.storageos.geomodel.VdcPreCheckResponse2;
import com.emc.storageos.geomodel.VdcConfig;
import com.emc.storageos.security.ipsec.IPsecConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.security.geo.GeoClientCacheManager;

/*
 * Detail implementation of vdc reconnect operation
 */
public class ReconnectVdcTaskOp extends AbstractVdcTaskOp {

    private final static Logger log = LoggerFactory.getLogger(ReconnectVdcTaskOp.class);

    private final static int NODE_CHECK_TIMEOUT = 60 * 1000; // one minute

    public ReconnectVdcTaskOp(InternalDbClient dbClient, GeoClientCacheManager geoClientCache,
            VdcConfigHelper helper, Service serviceInfo, VirtualDataCenter vdc, String taskId, KeyStore keystore, IPsecConfig ipsecConfig) {
        super(dbClient, geoClientCache, helper, serviceInfo, vdc, taskId, null, keystore, ipsecConfig);
    }

    @Override
    protected void process() {
        log.info("Start reconnect vdc operation to vdc {}", operatedVdc.getId());
        loadVdcInfo();
        log.info("Load vdc info is done");

        preCheck();

        reconnectVdc();

        // release global lock, same way with connect vdc.
        postStep();
        log.info("Reconnect vdc done");
    }

    private void preCheck() {
        URI reconnectVdcId = operatedVdc.getId();

        URI unstable = checkAllVdcStable(true, true);
        if (unstable != null) {
            log.error("The 'reconnect vdc operation' should not be triggered because vdc {} is unstable", unstable);
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, unstable);
            String vdcName = (vdc != null) ? vdc.getLabel() : "";
            throw GeoException.fatals.unstableVdcFailure(vdcName);
        }

        if (!isReconnectVdcInRightStatus()) {
            log.error("The vdc {} to be reconnected has wrong status {}", reconnectVdcId, operatedVdcStatus);
            throw GeoException.fatals.reconnectVdcInvalidStatus(operatedVdc.getLabel());
        }

        // Only reconnect the vdc back once it is connected with other connected vdcs.
        if (!isAllConnectedVdcReachableWith(operatedVdc)) {
            log.error("There is at least one vdc is unreachable with the vdc {} which will need to be reconnected ", operatedVdc);
            throw GeoException.fatals.reconnectVdcUnreachable(errMsg);
        }

        // Only reconnect the vdc back if myVdc is connected with other connected vdcs.
        if (!isAllConnectedVdcReachableWith(myVdc)) {
            log.error("There is at least one vdc is unreachable with the vdc {} which will perform this operation ", myVdcId);
            throw GeoException.fatals.reconnectVdcUnreachable(errMsg);
        }

        if (isVdcVersion20()) {
            log.error("At least one vdc's version is less than 2.1");
            throw GeoException.fatals.vdcVersionCheckFail(errMsg);
        }

        // TODO: check if the vdc need to be reconnected back's IP and SSL changed or not. Or any related change.
        checkReconnectingVdc();

        log.info("ReconnectVdcTaskOp precheck phrase success");
    }

    private void checkReconnectingVdc() {
        VdcPreCheckParam2 param = new VdcPreCheckParam2();
        param.setConfigChangeType(changeType());

        Map<String, List<String>> blackLists = dbClient.getBlacklist();
        List<String> blackList = new ArrayList();

        Collection<List<String>> lists = blackLists.values();
        for (List<String> list : lists) {
            blackList = list;

            // since all lists are same, so we only need the first one
            break;
        }

        param.setBlackList(blackList);

        List<String> whiteList = getWhiteList();
        param.setWhiteList(whiteList);

        List<URI> ids = new ArrayList(1);
        ids.add(myVdc.getId());

        param.setVdcIds(ids);

        log.info("checkReconnectingVdc param={}", param);

        VdcPreCheckResponse2 resp2 = null;
        try {
            resp2 = sendVdcPrecheckRequest2(operatedVdc, param, NODE_CHECK_TIMEOUT);
        } catch (Exception ex) {
            log.error("Precheck the reconnected vdc {} failed: {}", operatedVdc.getShortId(), ex);
            throw ex;
        }
        if (resp2.getCompatible() == false) {
            log.error("Precheck the reconnected vdc {} failed", operatedVdc.getShortId());
            throw GeoException.fatals.reconnectVdcIncompatible();
        }

        log.info("The precheck reconnect vdc {} is passed", operatedVdc.getShortId());
    }

    private List<String> getWhiteList() {
        List<String> whiteList = new ArrayList();
        List<URI> ids = dbClient.queryByType(VirtualDataCenter.class, true);

        for (URI id : ids) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, id);
            if (vdc.getConnectionStatus() == ConnectionStatus.CONNECTED) {
                Collection<String> addresses = dbClient.queryHostIPAddressesMap(vdc).values();
                whiteList.addAll(addresses);
            }
        }

        return whiteList;
    }

    private void reconnectVdc() {

        // TODO: use updateVdcStatus() later
        updateOpStatus(ConnectionStatus.RECONNECTING);
        failedVdcStatus = ConnectionStatus.RECONNECT_FAILED;

        lockHelper.acquire(operatedVdc.getShortId());
        log.info("Acquired global lock, go on with reconnect vdc");

        removeVdcFromBlacklist();

        setStrategyOption();

        // Sync cert for operated vdc, incase there is any add or delete vdc after it has been disconnected.
        syncCertForOperatedVdc();

        // Update status for other living vdcs
        updateVdcStatus(ConnectionStatus.CONNECTED, false);
        // update config for operated vdc, will sync config and trigger node repair
        updateVdcStatus(ConnectionStatus.CONNECTED, true);
    }

    private void postStep() {
        lockHelper.release(operatedVdc.getShortId());
    }

    /*
     * sync cert for operated vdc incase there is any add or delete vdc after it has been disconnected.
     * Will simulate the add vdc operation, add all existing certs from myVdc to operatedVdc
     */
    private void syncCertForOperatedVdc() {
        VdcCertListParam certListParam = genCertListParam(VdcCertListParam.CMD_ADD_CERT);
        syncCertForSingleVdc(certListParam, operatedVdc);
    }

    private void setStrategyOption() {
        try {
            helper.addStrategyOption(operatedVdc, true);
        } catch (Exception e) {
            log.error("e= ", e);
            throw GeoException.fatals.vdcStrategyFailed(e);
        }
    }

    private void removeVdcFromBlacklist() {
        try {
            dbClient.removeVdcNodesFromBlacklist(operatedVdc);
        } catch (Exception e) {
            throw GeoException.fatals.failedRemoveNodesFromBlackList(myVdc.getLabel(), operatedVdc.getId().toString(), e);
        }
    }

    /*
     * This function is only used for reconnect vdc pre check. Do not allow below three status to reconnect,
     * because in such situation, the vdc might not in the fedration.
     */
    private boolean isReconnectVdcInRightStatus() {
        if (operatedVdcStatus == VirtualDataCenter.ConnectionStatus.CONNECT_FAILED ||
                operatedVdcStatus == VirtualDataCenter.ConnectionStatus.REMOVE_FAILED) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public VdcConfig.ConfigChangeType changeType() {
        return VdcConfig.ConfigChangeType.RECONNECT_VDC;
    }

}
