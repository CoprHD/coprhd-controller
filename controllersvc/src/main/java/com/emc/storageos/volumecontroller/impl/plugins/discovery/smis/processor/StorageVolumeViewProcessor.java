/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.domainmodel.Operation;

public class StorageVolumeViewProcessor extends StorageProcessor {

    private static final String SVUSAGE = "SVUsage";

    private Logger _logger = LoggerFactory.getLogger(StorageVolumeViewProcessor.class);
    private DbClient _dbClient;
    private List<Volume> _updateVolumes = null;
    private List<Object> _args;
    private List<BlockSnapshot> _updateSnapShots;
    private List<BlockMirror> _updateMirrors;
    private static final int BATCH_SIZE = 200;

    private PartitionManager _partitionManager;
    List<CIMObjectPath> _metaVolumeViewPaths = null;

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        EnumerateResponse<CIMInstance> volumeInstanceChunks = (EnumerateResponse<CIMInstance>) resultObj;
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        _updateVolumes = new ArrayList<Volume>();
        _updateSnapShots = new ArrayList<BlockSnapshot>();
        _updateMirrors = new ArrayList<BlockMirror>();
        CloseableIterator<CIMInstance> volumeInstances = null;
        try {
            _metaVolumeViewPaths = (List<CIMObjectPath>) keyMap.get(Constants.META_VOLUMES_VIEWS);
            if (_metaVolumeViewPaths == null) {
                _metaVolumeViewPaths = new ArrayList<CIMObjectPath>();
                keyMap.put(Constants.META_VOLUMES_VIEWS, _metaVolumeViewPaths);
            }
            // create empty place holder list for meta volume paths (cannot define this in xml)
            List<CIMObjectPath> metaVolumePaths = (List<CIMObjectPath>) keyMap.get(Constants.META_VOLUMES);
            if (metaVolumePaths == null) {
                keyMap.put(Constants.META_VOLUMES, new ArrayList<CIMObjectPath>());
            }

            CIMObjectPath storagePoolPath = getObjectPathfromCIMArgument(_args);
            volumeInstances = volumeInstanceChunks.getResponses();
            processVolumes(volumeInstances, keyMap);
            while (!volumeInstanceChunks.isEnd()) {
                _logger.info("Processing Next Volume Chunk of size {}", BATCH_SIZE);
                volumeInstanceChunks = client.getInstancesWithPath(storagePoolPath,
                        volumeInstanceChunks.getContext(), new UnsignedInteger32(BATCH_SIZE));
                processVolumes(volumeInstanceChunks.getResponses(), keyMap);
            }

            // if list empty, this method returns back immediately.
            // partition size might not be used in this context, as batch size < partition size.
            // TODO metering might need some extra work to push volumes in batches, hence not changing this method
            // signature
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

    private void processVolumes(CloseableIterator<CIMInstance> volumeInstances,
            Map<String, Object> keyMap) throws IOException {

        List<CIMObjectPath> metaVolumes = new ArrayList<>();
        while (volumeInstances.hasNext()) {
            CIMInstance volumeViewInstance = volumeInstances.next();
            String nativeGuid = getVolumeViewNativeGuid(volumeViewInstance.getObjectPath(), keyMap);

            if (isSnapShot(volumeViewInstance)) {
                BlockSnapshot snapShot = checkSnapShotExistsInDB(nativeGuid, _dbClient);
                if (null == snapShot || snapShot.getInactive()) {
                    _logger.debug("Skipping Snapshot, as its not being managed in Bourne");
                    continue;
                }
                updateBlockSnapShot(volumeViewInstance, snapShot, keyMap);
                if (_updateSnapShots.size() > BATCH_SIZE) {
                    _partitionManager.updateInBatches(_updateSnapShots, getPartitionSize(keyMap),
                            _dbClient, BLOCK_SNAPSHOT);
                    _updateSnapShots.clear();
                }
            } else if (isMirror(volumeViewInstance)) {
                BlockMirror mirror = checkBlockMirrorExistsInDB(nativeGuid, _dbClient);
                if (null == mirror || mirror.getInactive()) {
                    _logger.debug("Skipping Mirror, as its not being managed in Bourne");
                    continue;
                }
                updateBlockMirror(volumeViewInstance, mirror, keyMap);
                if (_updateMirrors.size() > BATCH_SIZE) {
                    _partitionManager.updateInBatches(_updateMirrors, getPartitionSize(keyMap),
                            _dbClient, BLOCK_MIRROR);
                    _updateMirrors.clear();
                }
            } else {
                Volume storageVolume = checkStorageVolumeExistsInDB(nativeGuid, _dbClient);
                if (null == storageVolume || storageVolume.getInactive()) {
                    continue;
                }
                _logger.debug("Volume managed by Bourne :" + storageVolume.getNativeGuid());
                updateStorageVolume(volumeViewInstance, storageVolume, keyMap);

                // Check if this is a meta volume and if we need to set missing meta volume related properties.
                // This is applicable for meta volumes discovered as unmanaged volumes and ingested prior to vipr controller 2.2 .
                if (storageVolume.getIsComposite()
                        && (storageVolume.getCompositionType() == null || storageVolume.getCompositionType().isEmpty())) {
                    // meta volume is missing meta related data. Need to discover this data and set in the volume.
                    metaVolumes.add(volumeViewInstance.getObjectPath());
                    _logger.info("Found meta volume in vipr with missing data: {}, name: {}",
                            volumeViewInstance.getObjectPath(), storageVolume.getLabel());
                }
            }
            if (_updateVolumes.size() > BATCH_SIZE) {
                _partitionManager.updateInBatches(_updateVolumes, getPartitionSize(keyMap),
                        _dbClient, VOLUME);
                _updateVolumes.clear();
            }
        }

        // Add meta volumes to the keyMap
        try {
            if (metaVolumes != null && !metaVolumes.isEmpty()) {
                _metaVolumeViewPaths.addAll(metaVolumes);
                _logger.info("Added  {} meta volumes.", metaVolumes.size());
            }
        } catch (Exception ex) {
            _logger.error("Processing meta volumes.", ex);

        }
    }

    private boolean isComposite(CIMInstance volumeViewInstance) {
        String isComposite = getCIMPropertyValue(volumeViewInstance, EMC_IS_META_VOLUME);
        return isComposite.equalsIgnoreCase("true");
    }

    private boolean isMirror(CIMInstance volumeViewInstance) {
        String usage = getCIMPropertyValue(volumeViewInstance, SVUSAGE);
        // 8 refers to Mirror
        return usage.equalsIgnoreCase(EIGHT);
    }

    private boolean isSnapShot(CIMInstance volumeInstance) {
        String usage = getCIMPropertyValue(volumeInstance, SVUSAGE);
        // 12 refers to Snapshot
        return usage.equalsIgnoreCase(TWELVE);
    }

    private void updateBlockSnapShot(CIMInstance volumeInstance,
            BlockSnapshot snapShot, Map<String, Object> keyMap) {
        snapShot.setAllocatedCapacity(Long.parseLong(getCIMPropertyValue(
                volumeInstance, EMC_ALLOCATED_CAPACITY)));
        snapShot.setProvisionedCapacity(returnProvisionedCapacity(
                volumeInstance, keyMap));
        _updateSnapShots.add(snapShot);
    }

    private void updateBlockMirror(CIMInstance volumeInstance,
            BlockMirror mirror, Map<String, Object> keyMap) {
        mirror.setAllocatedCapacity(Long.parseLong(getCIMPropertyValue(
                volumeInstance, EMC_ALLOCATED_CAPACITY)));
        mirror.setProvisionedCapacity(returnProvisionedCapacity(volumeInstance,
                keyMap));
        _updateMirrors.add(mirror);
    }

    private void updateStorageVolume(CIMInstance volumeInstance,
            Volume storageVolume, Map<String, Object> keyMap)
            throws IOException {
        storageVolume.setAllocatedCapacity(Long.parseLong(getCIMPropertyValue(
                volumeInstance, EMC_ALLOCATED_CAPACITY)));
        storageVolume.setProvisionedCapacity(returnProvisionedCapacity(
                volumeInstance, keyMap));

        // If meta volume was ingested prior to upgrade to 2.2 it won't have
        // 'isComposite' set. We need to check
        // cim instance here to see if the volume is meta volume and set it in
        // the volume instance.
        if (isComposite(volumeInstance) && !storageVolume.getIsComposite()) {
            storageVolume.setIsComposite(true);
            _logger.info("Set volume {} to composite (meta volume)",
                    storageVolume.getId());
        }
        _updateVolumes.add(storageVolume);

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;

    }
}
