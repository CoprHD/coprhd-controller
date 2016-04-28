/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.vdccontroller.impl;

import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.geomodel.VdcPreCheckParam2;
import com.emc.storageos.geomodel.VdcPreCheckResponse2;
import com.emc.storageos.security.ipsec.IPsecConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.geomodel.VdcConfig;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.security.geo.GeoClientCacheManager;

/*
 * Detail implementation of vdc disconnect operation
 */
public class DisconnectVdcTaskOp extends AbstractVdcTaskOp {

    private final static Logger log = LoggerFactory.getLogger(DisconnectVdcTaskOp.class);

    private final static int NODE_CHECK_TIMEOUT = 180 * 1000; // 3 minutes

    public DisconnectVdcTaskOp(InternalDbClient dbClient, GeoClientCacheManager geoClientCache,
            VdcConfigHelper helper, Service serviceInfo, VirtualDataCenter vdc, String taskId,
            KeyStore keystore, IPsecConfig ipsecConfig) {
        super(dbClient, geoClientCache, helper, serviceInfo, vdc, taskId, null, keystore, ipsecConfig);
    }

    @Override
    protected void process() {
        log.info("Start disconnect vdc");
        loadVdcInfo();
        log.info("Load vdc info is done");
        preCheck();
        disconnectVdc();
        log.info("Disconnect vdc done");
    }

    private void preCheck() {
        log.info("The precheck phrase of 'disconnect vdc' start ...");

        checkDisconnectingConcurrency();

        // TODO: use updateVdcStatus()
        updateOpStatus(ConnectionStatus.DISCONNECTING);

        URI unstable = checkAllVdcStable(true, true);
        if (unstable != null) {
            notifyPrecheckFailed();
            log.error("The 'disconnect vdc operation' should not be triggered because vdc {} is unstable", unstable.toString());
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, unstable);
            String vdcName = (vdc != null) ? vdc.getLabel() : "";
            throw GeoException.fatals.unstableVdcFailure(vdcName);
        }

        if (isTargetVdcReachable(NODE_CHECK_TIMEOUT)) {
            notifyPrecheckFailed();
            log.error("The vdc {} to be disconnected is still reachable from other VDCs", operatedVdc.getId());
            throw GeoException.fatals.disconnectVdcStillReachable(operatedVdc.getLabel());
        }

        if (isVdcVersion20()) {
            notifyPrecheckFailed();
            log.error("At least one vdc's version is less than 2.1");
            throw GeoException.fatals.vdcVersionCheckFail(errMsg);
        }

        log.info("The precheck phrase of 'disconnect vdc' successes");
    }

    private void checkDisconnectingConcurrency() {

        // Reject the disconnect request if there is any vdc that is under disconnecting

        // check local db first
        VirtualDataCenter disconnectingVdc = helper.getDisconnectingVdc();

        if (disconnectingVdc != null)
        {
            log.error("There is already a VDC {} under disconnecting", disconnectingVdc.getId());
            throw GeoException.fatals.disconnectVdcConcurrentCheckFail(disconnectingVdc.getLabel());
        }

        for (VirtualDataCenter vdc : connectedVdc) {
            if (operatedVdc.getId().equals(vdc.getId()) || myVdcId.equals(vdc.getId().toString())) {
                continue; // Don't check on the vdc to be disconnected and myself
            }

            VdcPreCheckParam2 param = new VdcPreCheckParam2();
            param.setConfigChangeType(changeType());

            List<URI> vdcIds = new ArrayList(2);
            vdcIds.add(operatedVdc.getId());
            vdcIds.add(myVdc.getId());
            param.setVdcIds(vdcIds);

            log.info("'disconnect vdc' precheck2 paramerte={}", param);
            VdcPreCheckResponse2 resp2 = null;
            try {
                resp2 = sendVdcPrecheckRequest2(vdc, param, DEFAULT_NODE_CHECK_TIMEOUT);
            } catch (Exception ex) {
                log.error("Precheck the reconnected vdc {} failed: {}", operatedVdc.getShortId(), ex);
                notifyPrecheckFailed();
                throw ex;
            }
            if (resp2.getId() != null) {
                log.error("There is already a VDC {} under disconnecting", disconnectingVdc);
                notifyPrecheckFailed();
                throw GeoException.fatals.disconnectVdcConcurrentCheckFail(disconnectingVdc.getLabel());
            }

            if (resp2.getCompatible() == false) {
                log.error("The local vdc {} has been disconnected", myVdcId);
                throw GeoException.fatals.disconnectVdcInvalidStatus(myVdc.getLabel());
            }
        }
    }

    private void disconnectVdc() {

        updateOpStatus(ConnectionStatus.DISCONNECTING);
        failedVdcStatus = ConnectionStatus.DISCONNECT_FAILED;

        log.info("Disconnect vdc operation start ...");

        removeVdcNodesFromCassandra();

        log.info("Remove the vdc {} from cassandra node list", operatedVdc);

        removeVdcFromStrategyOption(true);

        log.info("Remove the vdc {} from cassandra strategy options", operatedVdc);

        updateVdcStatus(ConnectionStatus.DISCONNECTED, false);

        log.info("Set the vdc {} status to DISCONNECT_VDC", operatedVdc);
    }

    private void removeVdcNodesFromCassandra() {
        try {
            dbClient.removeVdcNodes(operatedVdc);
            dbClient.addVdcNodesToBlacklist(operatedVdc);
        } catch (Exception e) {
            log.error("Failed to remove nodes from GeoDB : {}", e);
            throw GeoException.fatals.vdcStrategyFailed(e);
        }
    }

    @Override
    public VdcConfig.ConfigChangeType changeType() {
        return VdcConfig.ConfigChangeType.DISCONNECT_VDC;
    }

}
