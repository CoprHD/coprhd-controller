/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2013 EMC Corporation All Rights Reserved This software contains
 * the intellectual property of EMC Corporation or is licensed to EMC
 * Corporation from third parties. Use of this software and the intellectual
 * property contained therein is expressly limited to the terms and conditions
 * of the License Agreement under which it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.smis;

import java.util.Iterator;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;

public class SmisStorageDevicePreProcessor {

    private static final Logger _log = LoggerFactory.getLogger(SmisStorageDevicePreProcessor.class);
    private SmisCommandHelper _helper;
    private CIMObjectPathFactory _cimPath;
    private CIMConnectionFactory _cimConnection = null;

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setSmisCommandHelper(SmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setCimConnectionFactory(CIMConnectionFactory connectionFactory) {
        _cimConnection = connectionFactory;
    }

    /**
     * Create StoragePool Setting for a given pool. This will be useful before
     * Here are the steps to create a new PoolSetting.
     * 1. First find the storagePoolCapability for a given storagepool.
     * 2. Use the capability to create a new StoragePool Setting.
     * 3. Update instance to set the 
     * 
     * 
     * creating a volume.
     * @param storageSystem
     * @param storagePool
     * @param thinVolumePreAllocateSize
     * @throws Exception
     */
    public CIMInstance createStoragePoolSetting(StorageSystem storageSystem, StoragePool storagePool,
            long thinVolumePreAllocateSize) throws Exception {

        _log.info(String.format(
                "Create StoragePool Setting Start - Array: %s, Pool: %s, \n   thinVolumePreAllocateSize: %s",
                storageSystem.getSerialNumber(), storagePool.getNativeId(), thinVolumePreAllocateSize));
        CIMObjectPath poolSvcPath = _cimPath.getStoragePoolPath(storageSystem, storagePool);
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();
        CIMInstance modifiedSettingInstance = null;
        try {
            _log.debug("Op1 start: Getting poolCapabilities associated with this pool");
            final Iterator<?> it = client.associatorNames(poolSvcPath, SmisConstants.CIM_ELEMENTCAPABILITIES,
                    SmisConstants.SYMM_STORAGEPOOL_CAPABILITIES, null, null);
            if (it.hasNext()) {
                final CIMObjectPath poolCapabilityPath = (CIMObjectPath) it.next();
                _log.debug("Op1 end: received pool capability from provider {}", poolCapabilityPath);
                CIMArgument<?>[] outputArgs = new CIMArgument<?>[1];
                _log.info("Invoking CIMClient to create to create a new Setting");
                client.invokeMethod(poolCapabilityPath,
                        SmisConstants.CP_CREATE_SETTING, _helper.getCreatePoolSettingArguments(), outputArgs);
                CIMObjectPath settingPath = _cimPath.getCimObjectPathFromOutputArgs(outputArgs, SmisConstants.CP_NEWSETTING);
                modifiedSettingInstance = new CIMInstance(settingPath, _helper.getModifyPoolSettingArguments(thinVolumePreAllocateSize));
                client.modifyInstance(modifiedSettingInstance, SmisConstants.PS_THIN_VOLUME_INITIAL_RESERVE);
                _log.info("Modified the poolSetting instance to set ThinProvisionedInitialReserve");
            }
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            throw e;
        } catch (Exception e) {
            _log.error("Problem in createStoragePoolSetting: " + storagePool.getNativeId(), e);
            throw e;
        } finally {
            _log.info(String.format("Create StoragePool Setting End - Array:%s, Pool: %s",
                    storageSystem.getSerialNumber(), storagePool.getNativeId()));
        }
        return modifiedSettingInstance;
    }

}
