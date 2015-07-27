/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.ibm.xiv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

public class XIVStorageVolumeProcessor extends StorageProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(XIVStorageVolumeProcessor.class);

    private static final String SOURCE_VOLUME_NAME = "SourceVolumeName";
    private static final String VOLUME = "Volume";
    private static final String BLOCK_SNAPSHOT = "BlockSnapshot";
    private static final String VIRTUAL_SPACE_CONSUMED = "VirtualSpaceConsumed";
    private static final String BLOCK_SIZE = "BlockSize";
    private static final String CONSUMABLE_BLOCKS = "ConsumableBlocks";
    private static final String SYSTEMNAME = "SystemName";
    private static final String DEVICEID = "DeviceID";
    private static final int BATCH_SIZE = 200;
    
    private DbClient _dbClient = null;
    private List<Volume> _updateVolumes = null;
    private List<BlockSnapshot> _updateSnapShots;
    private PartitionManager _partitionManager;

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        final Iterator<?> it = (Iterator<?>) resultObj;
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        _updateVolumes = new ArrayList<Volume>();
        _updateSnapShots = new ArrayList<BlockSnapshot>();

        try {
            while (it.hasNext()) {
                CIMInstance volumeViewInstance = null;
                volumeViewInstance = (CIMInstance) it.next();
                String nativeGuid = getVolumeViewNativeGuid(volumeViewInstance
                        .getObjectPath());
                String source = getCIMPropertyValue(volumeViewInstance,
                        SOURCE_VOLUME_NAME);

                if (source != null && !source.isEmpty()) {
                    BlockSnapshot snapShot = checkSnapShotExistsInDB(
                            nativeGuid, _dbClient);
                    if (snapShot == null || snapShot.getInactive()) {
                        _logger.debug("Skipping unmanged snapshot {}", nativeGuid);
                        continue;
                    }
                    _logger.debug("Snapshot managed by ViPR {}", nativeGuid);
                    updateBlockSnapShot(volumeViewInstance, snapShot,
                            nativeGuid);
                    if (_updateSnapShots.size() > BATCH_SIZE) {
                        _partitionManager.updateInBatches(_updateSnapShots,
                                getPartitionSize(keyMap), _dbClient,
                                BLOCK_SNAPSHOT);
                        _updateSnapShots.clear();
                    }
                } else {
                    Volume storageVolume = checkStorageVolumeExistsInDB(
                            nativeGuid, _dbClient);
                    if (storageVolume == null || storageVolume.getInactive()) {
                        _logger.debug("Skipping unmanaged volume {}", nativeGuid);
                        continue;
                    }
                    _logger.debug("Volume managed by ViPR {}", nativeGuid);
                    updateStorageVolume(volumeViewInstance, storageVolume);
                }
                if (_updateVolumes.size() > BATCH_SIZE) {
                    _partitionManager.updateInBatches(_updateVolumes,
                            getPartitionSize(keyMap), _dbClient, VOLUME);
                    _updateVolumes.clear();
                }
            }

            // if list empty, this method returns back immediately.
            // partition size might not be used in this context, as batch size <
            // partition size.
            // TODO metering might need some extra work to push volumes in
            // batches, hence not changing this method signature
            _partitionManager.updateInBatches(_updateVolumes,
                    getPartitionSize(keyMap), _dbClient, VOLUME);
            _partitionManager.updateInBatches(_updateSnapShots,
                    getPartitionSize(keyMap), _dbClient, BLOCK_SNAPSHOT);

        } catch (Exception e) {
            _logger.error("Processing Volumes and Snapshots failed", e);
        } finally {
            _updateVolumes = null;
            _updateSnapShots = null;
        }
    }

    private void updateBlockSnapShot(CIMInstance volumeInstance,
            BlockSnapshot snapShot, String nativeGuid) {
        snapShot.setAllocatedCapacity(Long.parseLong(getCIMPropertyValue(
                volumeInstance, VIRTUAL_SPACE_CONSUMED)));
        snapShot.setProvisionedCapacity(returnProvisionedCapacity(volumeInstance));
        _updateSnapShots.add(snapShot);
    }

    private void updateStorageVolume(CIMInstance volumeInstance,
            Volume storageVolume) throws IOException {
        storageVolume.setAllocatedCapacity(Long.parseLong(getCIMPropertyValue(
                volumeInstance, VIRTUAL_SPACE_CONSUMED)));
        storageVolume
                .setProvisionedCapacity(returnProvisionedCapacity(volumeInstance));
        _updateVolumes.add(storageVolume);
    }

    private String getVolumeViewNativeGuid(CIMObjectPath path) {
        String systemName = path.getKey(SYSTEMNAME).getValue().toString();
        String id = path.getKey(DEVICEID).getValue().toString();
        // for snapshot or Volume , native Guid format is same
        return NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                systemName.toUpperCase(), id);
    }
    
    private long returnProvisionedCapacity(CIMInstance volumeInstance) {
        long blocksize = Long.parseLong(volumeInstance.getPropertyValue(
                BLOCK_SIZE).toString());
        long blocks = Long.parseLong(volumeInstance.getPropertyValue(
                CONSUMABLE_BLOCKS).toString());
        return blocksize * blocks;
    }
}
