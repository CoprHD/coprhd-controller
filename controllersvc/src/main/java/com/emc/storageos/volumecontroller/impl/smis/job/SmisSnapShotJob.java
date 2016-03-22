/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class SmisSnapShotJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisSnapShotJob.class);

    public SmisSnapShotJob(CIMObjectPath cimJob, URI storageSystem,
            TaskCompleter taskCompleter, String jobName) {
        super(cimJob, storageSystem, taskCompleter, jobName);
    }

    /**
     * This method updates provisioned capacity and allocated capacity for snapshots.
     * It also set settingsInstance for VMAX V3 snapshot.
     * 
     * @param snapShot
     * @param syncVolume
     * @param client
     * @param storage
     * @param sourceVolId
     * @param elementName
     */
    protected void commonSnapshotUpdate(
            BlockSnapshot snapShot, CIMInstance syncVolume, WBEMClient client,
            StorageSystem storage, String sourceElementId, String elementName) {
        try {
            CIMProperty consumableBlocks = syncVolume
                    .getProperty(SmisConstants.CP_CONSUMABLE_BLOCKS);
            CIMProperty blockSize = syncVolume.getProperty(SmisConstants.CP_BLOCK_SIZE);
            // calculate provisionedCapacity = consumableBlocks * block size
            Long provisionedCapacity = Long.valueOf(consumableBlocks.getValue()
                    .toString()) * Long.valueOf(blockSize.getValue().toString());
            snapShot.setProvisionedCapacity(provisionedCapacity);

            // set Allocated Capacity
            CloseableIterator<CIMInstance> iterator = null;
            iterator = client.referenceInstances(syncVolume.getObjectPath(),
                    SmisConstants.CIM_ALLOCATED_FROM_STORAGEPOOL, null, false,
                    SmisConstants.PS_SPACE_CONSUMED);
            if (iterator.hasNext()) {
                CIMInstance allocatedFromStoragePoolPath = iterator.next();
                CIMProperty spaceConsumed = allocatedFromStoragePoolPath
                        .getProperty(SmisConstants.CP_SPACE_CONSUMED);
                if (null != spaceConsumed) {
                    snapShot.setAllocatedCapacity(Long.valueOf(spaceConsumed.getValue()
                            .toString()));
                }
            }

            // set settingsInstance for VMAX V3 only
            setSettingsInstance(storage, snapShot, sourceElementId, elementName);
        } catch (Exception e) {
            // Don't want to fail the snapshot creation, if capacity retrieval fails, as auto discovery cycle
            // will take care of updating capacity informations later.
            _log.error(
                    "Caught an exception while trying to update Capacity and SettingsInstance for Snapshots",
                    e);
        }
    }

    private void setSettingsInstance(StorageSystem storage,
                                     BlockSnapshot snapshot, String sourceElementId, String elementName) {
        String instance = SmisUtils.generateVmax3SettingsInstance(storage, sourceElementId, elementName);
        snapshot.setSettingsInstance(instance);
    }
}
