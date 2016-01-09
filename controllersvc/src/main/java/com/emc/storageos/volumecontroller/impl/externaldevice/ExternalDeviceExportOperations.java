/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExternalDeviceExportOperations implements ExportMaskOperations {

    private static Logger log = LoggerFactory.getLogger(ExternalDeviceExportOperations.class);

    private DbClient dbClient;

    // Need this reference to get driver for device type.
    private ExternalBlockStorageDevice externalDevice;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setExternalDevice(ExternalBlockStorageDevice externalDevice) {
        this.externalDevice = externalDevice;
    }

    @Override
    public void createExportMask(StorageSystem storage, URI exportMaskUri, VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList, List<Initiator> initiatorList, TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} createExportMask START...", storage.getSerialNumber());
        log.info("Export mask id: {}", exportMaskUri);
        log.info("createExportMask: assignments: {}", targetURIList);
        log.info("createExportMask: initiators: {}", initiatorList);
        log.info("createExportMask: volume-HLU pairs: {}", volumeURIHLUs);

        taskCompleter.ready(dbClient);

        log.info("{} createExportMask END...", storage.getSerialNumber() );
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMask, List<URI> volumeURIList, List<URI> targetURIList, List<Initiator> initiatorList, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void addVolume(StorageSystem storage, URI exportMask, VolumeURIHLU[] volumeURIHLUs, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void removeVolume(StorageSystem storage, URI exportMask, List<URI> volume, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void addInitiator(StorageSystem storage, URI exportMask, List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void removeInitiator(StorageSystem storage, URI exportMask, List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {

    }


    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
                                                 List<String> initiatorNames, boolean mustHaveAllPorts) {
        // not supported. There are no common masking concepts. So, return null.
        return null;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        return null;
    }

    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask, List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback, TaskCompleter taskCompleter) throws Exception {

    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return null;
    }
}
