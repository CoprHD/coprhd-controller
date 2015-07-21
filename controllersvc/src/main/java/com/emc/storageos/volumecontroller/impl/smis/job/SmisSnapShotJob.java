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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class SmisSnapShotJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisSnapShotJob.class);

    public SmisSnapShotJob ( CIMObjectPath cimJob, URI storageSystem,
            TaskCompleter taskCompleter, String jobName ) {
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
            
            //set Allocated Capacity
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

    /*
     * Set settings instance for VMAX V3 only
     * @param StorageSytem storage
     * @param snapshot BlockSnapshot to be updated
     * @param sourceElementId String of source volume (or source group) ID
     * @elementName String used as ElementName when creating ReplicationSettingData during single snapshot creation,
     *      or RelationshipName used in CreateGroupReplica for group snapshot
     *
     * Note elementName should be target device's DeviceID or target group ID
     * @see com.emc.storageos.volumecontroller.impl.smis.vmax.VmaxSnapshotOperations#getReplicationSettingData   
     */
    private void setSettingsInstance(StorageSystem storage,
            BlockSnapshot snapshot, String sourceElementId, String elementName) {
        if (storage.checkIfVmax3()) {
            // SYMMETRIX-+-000196700567-+-<sourceElementId>-+-<elementName>-+-0
            StringBuilder sb = new StringBuilder("SYMMETRIX");
            sb.append(Constants.SMIS80_DELIMITER)
                    .append(storage.getSerialNumber())
                    .append(Constants.SMIS80_DELIMITER).append(sourceElementId)
                    .append(Constants.SMIS80_DELIMITER).append(elementName)
                    .append(Constants.SMIS80_DELIMITER).append("0");
            snapshot.setSettingsInstance(sb.toString());
        }
    }
}
