/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.vdccontroller.impl;

import java.security.KeyStore;
import java.util.List;
import java.util.Properties;

import com.emc.storageos.security.ipsec.IPsecConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.geo.vdccontroller.VdcController;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/*
 * Detail implementation of vdc controller
 */
public class VdcControllerImpl implements VdcController {
    private final static Logger log = LoggerFactory.getLogger(VdcControllerImpl.class);

    @Autowired
    private InternalDbClient dbClient;

    @Autowired
    private GeoClientCacheManager geoClientCache;

    @Autowired
    private VdcConfigHelper helper;

    private Service serviceInfo;

    @Autowired
    private VdcOperationLockHelper vdcLockHelper;

    @Autowired
    private BasePermissionsHelper permissionsHelper;

    @Autowired
    @Qualifier("keyGenerator")
    InternalApiSignatureKeyGenerator apiSignatureGenerator;

    public void setDbClient(InternalDbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setVdcHelper(VdcConfigHelper helper) {
        this.helper = helper;
    }

    public void setVdcOperationLockHelper(VdcOperationLockHelper helper) {
        this.vdcLockHelper = helper;
    }

    public void setPermissionsHelper(BasePermissionsHelper permissionsHelper) {
        this.permissionsHelper = permissionsHelper;
    }

    public void setSignatureGenerator(InternalApiSignatureKeyGenerator generator) {
        this.apiSignatureGenerator = generator;
    }

    public void setGeoClientManager(GeoClientCacheManager clientManager) {
        this.geoClientCache = clientManager;
    }

    private KeyStore _keyStore;

    @Autowired
    private IPsecConfig ipsecConfig;

    @Override
    public void setKeystore(KeyStore keystore) {
        _keyStore = keystore;
    }

    public void setServiceInfo(Service serviceInfo) {
        this.serviceInfo = serviceInfo;
    }

    @Override
    public void connectVdc(VirtualDataCenter localVdc, String task, List<Object> taskParams) throws
            InternalException {
        log.info("Starting to connect a new vdc to the system, task id {}",
                localVdc.getShortId(), task);

        // during connect vdc process, the whole system will rolling reboot to apply
        // the new system properity, we shall not redo the finished steps
        ConnectVdcTaskOp vdcOp = new ConnectVdcTaskOp(dbClient, geoClientCache, helper,
                serviceInfo, localVdc, task, (Properties) taskParams.get(0), apiSignatureGenerator, _keyStore, ipsecConfig);
        log.info("Initialize ConnectVdcTaskOp done. ");
        vdcOp.setLockHelper(vdcLockHelper);
        vdcOp.setBasePermissionHelper(permissionsHelper);
        vdcOp.handle();
    }

    @Override
    public void removeVdc(VirtualDataCenter vdc, String task, List<Object> taskParams) throws InternalException {
        log.info("Starting to remove a vdc {} into the system, task id {}",
                vdc.getShortId(), task);

        RemoveVdcTaskOp vdcOp = new RemoveVdcTaskOp(dbClient, geoClientCache, helper,
                serviceInfo, vdc, task, _keyStore, ipsecConfig);
        log.info("Initialize RemoveVdcTaskOp done. ");
        vdcOp.setLockHelper(vdcLockHelper);
        vdcOp.handle();
    }

    public void updateVdc(VirtualDataCenter vdcToBeUpdated, String task, List<Object> params) throws InternalException {
        log.info("Starting to update vdc {} info in the system, task id {}", vdcToBeUpdated.getShortId(), task);

        // TODO: during update, vip change needs reboot?
        UpdateVdcTaskOp vdcOp = new UpdateVdcTaskOp(dbClient, geoClientCache, helper,
                serviceInfo, vdcToBeUpdated, task, params, apiSignatureGenerator, _keyStore, ipsecConfig);
        log.info("Initialize UpdateVdcTaskOp done. ");
        vdcOp.setLockHelper(vdcLockHelper);
        vdcOp.handle();
    }

    @Override
    public void disconnectVdc(VirtualDataCenter vdcToBeDisconnected, String task, List<Object> taskParams) throws InternalException {
        log.info("Starting to disconnect vdc {} info in the system, task id {}", vdcToBeDisconnected.getShortId(), task);

        DisconnectVdcTaskOp vdcOp = new DisconnectVdcTaskOp(dbClient, geoClientCache, helper, serviceInfo, vdcToBeDisconnected, task,
                _keyStore, ipsecConfig);
        log.info("Initialize DisconnectVdcTaskOp done. ");
        vdcOp.setLockHelper(vdcLockHelper);
        vdcOp.handle();
    }

    @Override
    public void reconnectVdc(VirtualDataCenter vdcToBeReconnected, String task, List<Object> taskParams) throws InternalException {
        log.info("Starting to reconnect vdc {} info in the system, task id {}", vdcToBeReconnected.getShortId(), task);

        ReconnectVdcTaskOp vdcOp = new ReconnectVdcTaskOp(dbClient, geoClientCache, helper,
                serviceInfo, vdcToBeReconnected, task, _keyStore, ipsecConfig);
        log.info("Initialize ReconnectVdcTaskOp done. ");
        vdcOp.setLockHelper(vdcLockHelper);
        vdcOp.handle();
    }
}
