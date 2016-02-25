/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.google.common.base.Joiner;

/*
 * This is used to set supported VPools for local replicas
 * The supported VPools of replicas will be the same as that of the source.
 * This should be executed at the end of the unmanaged volume discovery.
 *
 * Also filter out snapshots of snapshot by setting INGESTABLE to false.
 */
public class VolumeDiscoveryPostProcessor extends StorageProcessor {
    private static final Logger _logger = LoggerFactory
            .getLogger(VolumeDiscoveryPostProcessor.class);
    private static final String FALSE = "false";
    private PartitionManager _partitionManager;

    public void runReplicaPostProcessing(Map<String, LocalReplicaObject> volumeToReplicaMap, DbClient dbClient) {
        setSupportedVPoolsForReplicas(volumeToReplicaMap, dbClient);
        filterUnsupportedSnapshots(volumeToReplicaMap, dbClient);
    }

    private void setSupportedVPoolsForReplicas(
            Map<String, LocalReplicaObject> volumeToReplicaMap, DbClient dbClient) {
        _logger.info("Post processing UnManagedVolumes setSupportedVPoolsForReplicas");
        List<UnManagedVolume> modifiedUnManagedVolumes = new ArrayList<UnManagedVolume>();
        // for each source, set SUPPORTED_VPOOL_LIST for its targets
        for (Entry<String, LocalReplicaObject> entry : volumeToReplicaMap.entrySet()) {
            String srcNativeGuid = entry.getKey();
            LocalReplicaObject srcObj = entry.getValue();
            if (srcObj.hasReplica() && !srcObj.isReplica()) {
                // process its dependents
                try {
                    StringSet vPools = null;
                    // check if unmanaged volume is created
                    UnManagedVolume unManagedVolume = checkUnManagedVolumeExistsInDB(srcNativeGuid, dbClient);
                    if (unManagedVolume != null) {
                        vPools = unManagedVolume.getSupportedVpoolUris();
                    } else {
                        // check if it has already been ingested
                        String volumeNativeGuid = srcNativeGuid.replace(NativeGUIDGenerator.UN_MANAGED_VOLUME, NativeGUIDGenerator.VOLUME);
                        Volume volume = checkStorageVolumeExistsInDB(volumeNativeGuid, dbClient);
                        if (volume != null) {
                            _logger.debug("Volume {} is already being managed by ViPR", volumeNativeGuid);
                            vPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(dbClient, volume.getPool(),
                                    volume.getThinlyProvisioned().toString());
                        }
                    }

                    if (vPools != null && !vPools.isEmpty()) {
                        setVPoolsForDependents(vPools, srcObj,
                                volumeToReplicaMap, modifiedUnManagedVolumes,
                                dbClient);
                    } else {
                        _logger.info("Cannot find supported VPools for {}", srcNativeGuid);
                    }
                } catch (Exception e) {
                    _logger.warn("Exception on setVPoolsForReplicas {}", e.getMessage());
                }
            }

            // if modifiedUnManagedVolumes size reaches BATCH_SIZE, persist to db
            if (modifiedUnManagedVolumes.size() >= BATCH_SIZE) {
                _partitionManager.updateAndReIndexInBatches(modifiedUnManagedVolumes, BATCH_SIZE, dbClient, "UnManagedVolumes");
                modifiedUnManagedVolumes.clear();
            }
        }

        if (!modifiedUnManagedVolumes.isEmpty()) {
            _partitionManager.updateAndReIndexInBatches(modifiedUnManagedVolumes, BATCH_SIZE, dbClient, "UnManagedVolumes");
        }
    }

    /*
     * Set VPools of replicas recursively
     */
    private void setVPoolsForDependents(StringSet vPools,
            LocalReplicaObject srcObj,
            Map<String, LocalReplicaObject> volumeToReplicaMap,
            List<UnManagedVolume> unMangedVolumesUpdate, DbClient dbClient) {
        StringSet replicas = srcObj.getReplicas();
        if (replicas != null && !replicas.isEmpty()) {
            for (String replica : replicas) {
                try {
                    // get UnManagedVolume of replica and set SUPPORTED_VPOOL_LIST
                    UnManagedVolume unManagedVolume = checkUnManagedVolumeExistsInDB(replica, dbClient);
                    if (unManagedVolume != null) {
                        _logger.debug("{} matched vpools: {}", unManagedVolume.getNativeGuid(), vPools);
                        unManagedVolume.getSupportedVpoolUris().replace(vPools);
                        unMangedVolumesUpdate.add(unManagedVolume);
                        _logger.debug("Set VPools for {} to {}", replica, Joiner.on("\t").join(unManagedVolume.getSupportedVpoolUris()));
                        LocalReplicaObject replicaObj = volumeToReplicaMap.get(replica);
                        if (replicaObj.hasReplica()) {
                            // process dependents
                            setVPoolsForDependents(vPools, replicaObj, volumeToReplicaMap, unMangedVolumesUpdate, dbClient);
                        }
                    } else {
                        // shouldn't happen
                        _logger.warn("Cannot find unmanged volume {}", replica);
                    }
                } catch (IOException e) {
                    _logger.error("Exception on setVPoolsForDependents {}", e.getMessage());
                }
            }
        }
    }

    private void filterUnsupportedSnapshots(
            Map<String, LocalReplicaObject> volumeToReplicaMap, DbClient dbClient) {
        _logger.info("Post processing UnManagedVolumes filterNestedSnapshots");
        List<UnManagedVolume> modifiedUnManagedVolumes = new ArrayList<UnManagedVolume>();

        for (Entry<String, LocalReplicaObject> entry : volumeToReplicaMap.entrySet()) {
            String nativeGuid = entry.getKey();
            LocalReplicaObject obj = entry.getValue();
            // Process each snapshot
            if (LocalReplicaObject.Types.BlockSnapshot.equals(obj.getType())) {
                try {
                    UnManagedVolume unManagedVolume = checkUnManagedVolumeExistsInDB(nativeGuid, dbClient);

                    // If the snapshot synchronization path indicates the snapshot target volume
                    // is linked to an unsupported synchronization aspect, then the snapshot is not
                    // ingestable.
                    String syncAspectPath = obj.getSettingsInstance();
                    if (Constants.NOT_INGESTABLE_SYNC_ASPECT.equals(syncAspectPath)) {
                        unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_INGESTABLE.name(), FALSE);
                        unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_NOT_INGESTABLE_REASON.name(),
                                "The snapshot cannot be ingested because the snapshot target volume is linked to an unsupported "
                                        + "array snapshot whose name is used by multiple array snapshots for the same source volume. The "
                                        + "storage system likely uses generation numbers to differentiate these snapshots, and ViPR does not "
                                        + "currently support generation numbers.");
                        modifiedUnManagedVolumes.add(unManagedVolume);
                    } else {
                        // If a snapshot has its own snapshot targets, then the snapshot target and all its
                        // snapshot targets are non ingestable
                        StringSet targets = obj.getSnapshots();
                        if (targets != null && !targets.isEmpty()) {
                            if (unManagedVolume != null) {
                                _logger.info(
                                        "Set UnManagedVolume {} for {} to non ingestable, this snapshot target is the source of other snapshot targets.",
                                        unManagedVolume.getId(), nativeGuid);
                                unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_INGESTABLE.name(),
                                        FALSE);
                                modifiedUnManagedVolumes.add(unManagedVolume);
                            } else {
                                _logger.warn("No UnManagedVolume found for {}", nativeGuid);
                            }

                            // set all its snapshot targets to non ingestable since they are snapshot targets of a snapshot target
                            for (String tgtNativeId : targets) {
                                UnManagedVolume tgtUnManagedVolume = checkUnManagedVolumeExistsInDB(tgtNativeId, dbClient);
                                if (tgtUnManagedVolume != null) {
                                    _logger.info(
                                            "Set UnManagedVolume {} for {} to non ingestable, the source of this snapshot target is also a snapshot target.",
                                            unManagedVolume.getId(), tgtNativeId);
                                    tgtUnManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_INGESTABLE.name(),
                                            FALSE);
                                    modifiedUnManagedVolumes.add(tgtUnManagedVolume);
                                } else {
                                    _logger.warn("No UnManagedVolume found for {}", tgtNativeId);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    _logger.warn("Exception on filterNestedSnapshots {}", e.getMessage());
                }
            }

            // if modifiedUnManagedVolumes size reaches BATCH_SIZE, persist to db
            if (modifiedUnManagedVolumes.size() >= BATCH_SIZE) {
                _partitionManager.updateAndReIndexInBatches(modifiedUnManagedVolumes, BATCH_SIZE, dbClient, "UnManagedVolumes");
                modifiedUnManagedVolumes.clear();
            }
        }

        if (!modifiedUnManagedVolumes.isEmpty()) {
            _partitionManager.updateAndReIndexInBatches(modifiedUnManagedVolumes, BATCH_SIZE, dbClient, "UnManagedVolumes");
        }
    }

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        // TODO Auto-generated method stub

    }
}
