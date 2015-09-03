/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.plugins.BaseCollectionException;
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
 */
public class VolumeDiscoveryPostProcessor extends StorageProcessor {
    private static final Logger _logger = LoggerFactory
            .getLogger(VolumeDiscoveryPostProcessor.class);
    private PartitionManager _partitionManager;

    public void setSupportedVPoolsForReplicas(
            Map<String, LocalReplicaObject> volumeToReplicaMap, DbClient dbClient) {
        _logger.debug("Post processing UnManagedVolumes");
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
                    }
                    else {
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

        if (modifiedUnManagedVolumes.size() > 0) {
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

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        // TODO Auto-generated method stub

    }
}
