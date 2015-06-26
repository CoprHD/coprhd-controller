/**
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.domainmodel.Operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/*
 * Get volume info from StorageVolume instance
 *
 * Based on StorageVolumeViewProcessor
 */
public class StorageVolumeProcessor extends StorageProcessor {
    private static final Logger _logger = LoggerFactory.getLogger(StorageVolumeProcessor.class);
    private static final String USAGE = "Usage";
    private static final String EMC_IS_COMPOSITE = "EMCIsComposite";

    private DbClient _dbClient;
    private Map<String, Object> _keyMap;
    private List<Volume> _updateVolumes = null;
    private List<BlockSnapshot> _updateSnapShots = null;
    private List<BlockMirror> _updateMirrors = null;
    private static final int BATCH_SIZE = 200;

    private PartitionManager _partitionManager;
    private Map<String, String> _volumeToSpaceConsumedMap = null;
    private List<CIMObjectPath> _metaVolumePaths = null;
    private boolean _isVMAX3 = false;

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        EnumerateResponse<CIMInstance> volumeInstanceChunks = (EnumerateResponse<CIMInstance>) resultObj;
        WBEMClient client = (WBEMClient) keyMap.get(Constants._cimClient);
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        _keyMap = keyMap;
        _updateVolumes = new ArrayList<Volume>();
        _updateSnapShots = new ArrayList<BlockSnapshot>();
        _updateMirrors = new ArrayList<BlockMirror>();
        CloseableIterator<CIMInstance> volumeInstances = null;
        try {
            // create empty place holder list for meta volume paths (cannot define this in xml)
            _metaVolumePaths = (List<CIMObjectPath>) keyMap.get(Constants.META_VOLUMES);
            if (_metaVolumePaths == null) {
                keyMap.put(Constants.META_VOLUMES, new ArrayList<CIMObjectPath>());
            }

            _volumeToSpaceConsumedMap = (Map<String, String>) keyMap.get(Constants.VOLUME_SPACE_CONSUMED_MAP);
            CIMObjectPath storagePoolPath = getObjectPathfromCIMArgument(_args);
            _isVMAX3 = storagePoolPath.getObjectName().equals(StoragePool.PoolClassNames.Symm_SRPStoragePool.name());

            processResultbyChunk(resultObj, keyMap);

            _partitionManager.updateInBatches(_updateVolumes, getPartitionSize(keyMap), _dbClient,
                    VOLUME);
            _partitionManager.updateInBatches(_updateSnapShots, getPartitionSize(keyMap),
                    _dbClient, BLOCK_SNAPSHOT);
            _partitionManager.updateInBatches(_updateMirrors, getPartitionSize(keyMap), _dbClient,
                    BLOCK_MIRROR);

        } catch (Exception e) {
            _logger.error("Processing Volumes and Snapshots failed", e);
        } finally {
            _updateVolumes = null;
            _updateSnapShots = null;
            _updateMirrors = null;
            if (null != volumeInstances) {
                volumeInstances.close();
            }
        }
    }

    @Override
    protected int processInstances(Iterator<CIMInstance> instances) {
        int count = 0;
        List<CIMObjectPath> metaVolumes = new ArrayList<>();
        while (instances.hasNext()) {
            try {
                count++;
                CIMInstance volumeInstance = instances.next();
                String nativeGuid = getVolumeNativeGuid(volumeInstance.getObjectPath());

                if (isSnapShot(volumeInstance)) {
                    BlockSnapshot snapShot = checkSnapShotExistsInDB(nativeGuid, _dbClient);
                    if (null == snapShot || snapShot.getInactive()) {
                        _logger.debug("Skipping Snapshot, as its not being managed in ViPR");
                        continue;
                    }
                    updateBlockSnapShot(volumeInstance, snapShot, _keyMap);
                    if (_updateSnapShots.size() > BATCH_SIZE) {
                        _partitionManager.updateInBatches(_updateSnapShots, getPartitionSize(_keyMap),
                                _dbClient, BLOCK_SNAPSHOT);
                        _updateSnapShots.clear();
                    }
                } else if (isMirror(volumeInstance)) {
                    BlockMirror mirror = checkBlockMirrorExistsInDB(nativeGuid, _dbClient);
                    if (null == mirror || mirror.getInactive()) {
                        _logger.debug("Skipping Mirror, as its not being managed in Bourne");
                        continue;
                    }
                    updateBlockMirror(volumeInstance, mirror, _keyMap);
                    if (_updateMirrors.size() > BATCH_SIZE) {
                        _partitionManager.updateInBatches(_updateMirrors, getPartitionSize(_keyMap),
                                _dbClient, BLOCK_MIRROR);
                        _updateMirrors.clear();
                    }
                } else {
                    Volume storageVolume = checkStorageVolumeExistsInDB(nativeGuid, _dbClient);
                    if (null == storageVolume || storageVolume.getInactive()) {
                        continue;
                    }
                    _logger.debug("Volume managed by Bourne :" + storageVolume.getNativeGuid());
                    updateStorageVolume(volumeInstance, storageVolume, _keyMap);

                    // Check if this is a meta volume and if we need to set missing meta volume related properties.
                    // This is applicable for meta volumes discovered as unmanaged volumes and ingested prior to vipr controller 2.2 .
                    if (storageVolume.getIsComposite() && (storageVolume.getCompositionType() == null || storageVolume.getCompositionType().isEmpty())) {
                        // meta volume is missing meta related data. Need to discover this data and set in the volume.
                        metaVolumes.add(volumeInstance.getObjectPath());
                        _logger.info("Found meta volume in vipr with missing data: {}, name: {}",
                                volumeInstance.getObjectPath(), storageVolume.getLabel());
                    }
                }

                if (_updateVolumes.size() > BATCH_SIZE) {
                    _partitionManager.updateInBatches(_updateVolumes, getPartitionSize(_keyMap),
                            _dbClient, VOLUME);
                    _updateVolumes.clear();
                }
            }
            catch (Exception e) {
                _logger.error("Processing volume instance.", e);
            }
        }

        // Add meta volumes to the keyMap
        if (metaVolumes != null && !metaVolumes.isEmpty()) {
            _metaVolumePaths.addAll(metaVolumes);
            _logger.info("Added {} meta volumes.", metaVolumes.size());
        }

        return count;
    }

    private boolean isComposite(CIMInstance volumeViewInstance) {
        String isComposite = getCIMPropertyValue(volumeViewInstance, EMC_IS_COMPOSITE);
        return isComposite.equalsIgnoreCase("true");
    }

    private boolean isMirror(CIMInstance volumeViewInstance) {
        String usage = getCIMPropertyValue(volumeViewInstance, USAGE);
        //8 refers to Mirror
        return usage.equalsIgnoreCase(EIGHT);
    }

    private boolean isSnapShot(CIMInstance volumeInstance) {
        String usage = getCIMPropertyValue(volumeInstance, USAGE);
        // 12 refers to Snapshot
        return usage.equalsIgnoreCase(TWELVE);
    }

    private void updateBlockSnapShot(CIMInstance volumeInstance, BlockSnapshot snapShot, Map<String, Object> keyMap) {
        String spaceConsumed = getAllocatedCapacity(volumeInstance, _volumeToSpaceConsumedMap, _isVMAX3);
        if (spaceConsumed != null) {
            snapShot.setAllocatedCapacity(Long.parseLong(spaceConsumed));
        }

        snapShot.setProvisionedCapacity(returnProvisionedCapacity(
                volumeInstance, keyMap));
        _updateSnapShots.add(snapShot);
    }

    private void updateBlockMirror(CIMInstance volumeInstance, BlockMirror mirror, Map<String, Object> keyMap) {
        String spaceConsumed = getAllocatedCapacity(volumeInstance, _volumeToSpaceConsumedMap, _isVMAX3);
        if (spaceConsumed != null) {
            mirror.setAllocatedCapacity(Long.parseLong(spaceConsumed));
        }

        mirror.setProvisionedCapacity(returnProvisionedCapacity(volumeInstance,
                keyMap));
        _updateMirrors.add(mirror);
    }

    private void updateStorageVolume(CIMInstance volumeInstance, Volume volume, Map<String, Object> keyMap) {
        String spaceConsumed = getAllocatedCapacity(volumeInstance, _volumeToSpaceConsumedMap, _isVMAX3);
        if (spaceConsumed != null) {
            volume.setAllocatedCapacity(Long.parseLong(spaceConsumed));
        }

        volume.setProvisionedCapacity(returnProvisionedCapacity(
                volumeInstance, keyMap));

        // If meta volume was ingested prior to upgrade to 2.2 it won't have
        // 'isComposite' set. We need to check
        // cim instance here to see if the volume is meta volume and set it in
        // the volume instance.
        if (isComposite(volumeInstance) && !volume.getIsComposite()) {
            volume.setIsComposite(true);
            _logger.info("Set volume {} to composite (meta volume)",
                    volume.getId());
        }
        _updateVolumes.add(volume);
    }
}
