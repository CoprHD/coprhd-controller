/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.scaleio;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSnapshotVolumeResponse;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScaleIOCloneOperations implements CloneOperations {

    private static Logger log = LoggerFactory.getLogger(ScaleIOCloneOperations.class);
    private DbClient dbClient;
    private ScaleIOHandleFactory scaleIOHandleFactory;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setScaleIOHandleFactory(ScaleIOHandleFactory scaleIOHandleFactory) {
        this.scaleIOHandleFactory = scaleIOHandleFactory;
    }

    @Override
    public void createSingleClone(StorageSystem storageSystem, URI sourceVolume, URI cloneVolume, Boolean createInactive,
            TaskCompleter taskCompleter) {
        try {
            ScaleIORestClient scaleIOHandle = scaleIOHandleFactory.using(dbClient).getClientHandle(storageSystem);
            Volume cloneObj = dbClient.queryObject(Volume.class, cloneVolume);
            BlockObject parent = BlockObject.fetch(dbClient, sourceVolume);
            String systemId = scaleIOHandle.getSystemId();
            // Note: ScaleIO snapshots can be treated as full copies, hence re-use of #snapshotVolume here.
            ScaleIOSnapshotVolumeResponse result = scaleIOHandle.snapshotVolume(parent.getNativeId(), cloneObj.getLabel(), systemId);
            String nativeId = result.getVolumeIdList().get(0);
            ScaleIOHelper.updateSnapshotWithSnapshotVolumeResult(dbClient, cloneObj, systemId, nativeId);
            // Snapshots result does not provide capacity info, so we need to perform a queryVolume
            updateCloneFromQueryVolume(scaleIOHandle, cloneObj);
            dbClient.persistObject(cloneObj);
            
            StoragePool pool = dbClient.queryObject(StoragePool.class, cloneObj.getPool());
            pool.removeReservedCapacityForVolumes(Arrays.asList(cloneObj.getId().toString()));
            ScaleIOHelper.updateStoragePoolCapacity(dbClient, scaleIOHandle, cloneObj); 
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            Volume clone = dbClient.queryObject(Volume.class, cloneVolume);
            if (clone != null) {
                clone.setInactive(true);
                dbClient.persistObject(clone);
            }
            log.error("Encountered an exception", e);
            ServiceCoded code = DeviceControllerErrors.scaleio.encounteredAnExceptionFromScaleIOOperation("createSingleClone",
                    e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void detachSingleClone(StorageSystem storageSystem, URI cloneVolume, TaskCompleter taskCompleter) {
        log.info("START detachSingleClone operation");
        // no operation, set to ready
        Volume clone = dbClient.queryObject(Volume.class, cloneVolume);
        clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
        clone.setReplicaState(ReplicationState.DETACHED.name());
        dbClient.persistObject(clone);
        taskCompleter.ready(dbClient);
    }

    @Override
    public void activateSingleClone(StorageSystem storageSystem, URI fullCopy, TaskCompleter completer) {
        // Not supported
    }

    private void updateCloneFromQueryVolume(ScaleIORestClient scaleIOHandle, Volume cloneObj) throws Exception {

        try {
            ScaleIOVolume vol = scaleIOHandle.queryVolume(cloneObj.getNativeId());
            long size = Long.parseLong(vol.getSizeInKb()) * 1024L;
            cloneObj.setAllocatedCapacity(size);
            cloneObj.setProvisionedCapacity(size);
        } catch (Exception e) {
            log.warn("Failed to update full copy {} with size information: {}", cloneObj.getId(),
                    e.getMessage());
            throw e;
        }
    }

    @Override
    public void restoreFromSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void fractureSingleClone(StorageSystem storageSystem, URI sourceVolume,
            URI clone, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void resyncSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        // no support
    }

    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
            Boolean createInactive, TaskCompleter taskCompleter) {
        try {
            ScaleIORestClient scaleIOHandle = scaleIOHandleFactory.using(dbClient).getClientHandle(storage);
            List<Volume> clones = dbClient.queryObject(Volume.class, cloneList);
            Map<String, String> parent2snap = new HashMap<>();
            Set<URI> poolsToUpdate = new HashSet<>();

            for (Volume clone : clones) {
                Volume parent = dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
                parent2snap.put(parent.getNativeId(), clone.getLabel());
                poolsToUpdate.add(parent.getPool());
            }
            String systemId = scaleIOHandle.getSystemId();
            ScaleIOSnapshotVolumeResponse result = scaleIOHandle.snapshotMultiVolume(parent2snap, systemId);

            List<String> nativeIds = result.getVolumeIdList();
            Map<String, ScaleIOVolume> cloneNameMap = scaleIOHandle.getVolumeNameMap(nativeIds);
            Multimap<URI, String> poolToVolumesMap = ArrayListMultimap.create();
            for (Volume clone : clones) {
                String name = clone.getLabel();
                ScaleIOVolume sioVolume = cloneNameMap.get(name);
                ScaleIOHelper.updateSnapshotWithSnapshotVolumeResult(dbClient, clone, systemId, sioVolume.getId());
                clone.setAllocatedCapacity(Long.parseLong(sioVolume.getSizeInKb()) * 1024L);
                clone.setProvisionedCapacity(clone.getAllocatedCapacity());
                clone.setCapacity(clone.getAllocatedCapacity());
                clone.setReplicationGroupInstance(result.getSnapshotGroupId());
                poolToVolumesMap.put(clone.getPool(), clone.getId().toString());
            }
            dbClient.persistObject(clones);

            List<StoragePool> pools = dbClient.queryObject(StoragePool.class, Lists.newArrayList(poolsToUpdate));
            for (StoragePool pool : pools) {
                pool.removeReservedCapacityForVolumes(poolToVolumesMap.get(pool.getId()));
                ScaleIOHelper.updateStoragePoolCapacity(dbClient, scaleIOHandle, pool, storage);
            }

            taskCompleter.ready(dbClient);

        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code = DeviceControllerErrors.scaleio.encounteredAnExceptionFromScaleIOOperation("createGroupClone",
                    e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void activateGroupClones(StorageSystem storage, List<URI> clone, TaskCompleter taskCompleter) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void restoreGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void fractureGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void resyncGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void detachGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        List<Volume> cloneVolumes = dbClient.queryObject(Volume.class, clones);
        for (Volume theClone : cloneVolumes) {
            URI source = theClone.getAssociatedSourceVolume();
            Volume sourceVol = dbClient.queryObject(Volume.class, source);
            if (sourceVol != null && sourceVol.getFullCopies() != null) {
                sourceVol.getFullCopies().remove(theClone.getId().toString());
            }
            theClone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
            theClone.setReplicaState(ReplicationState.DETACHED.name());
            dbClient.persistObject(sourceVol);
        }
        dbClient.persistObject(cloneVolumes);
        if (completer != null) {
            completer.ready(dbClient);
        }

    }

}
