/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.scaleio;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSnapshotVolumeResponse;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.DefaultSnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

public class ScaleIOSnapshotOperations extends DefaultSnapshotOperations {

    private static Logger log = LoggerFactory.getLogger(ScaleIOSnapshotOperations.class);
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
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot, Boolean createInactive,
            Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            ScaleIORestClient scaleIOHandle = scaleIOHandleFactory.using(dbClient).getClientHandle(storage);
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);

            Volume parent = dbClient.queryObject(Volume.class, blockSnapshot.getParent().getURI());
            String systemId = scaleIOHandle.getSystemId();
            ScaleIOSnapshotVolumeResponse result = scaleIOHandle.snapshotVolume(parent.getNativeId(),
                    blockSnapshot.getLabel(), systemId);
            String nativeId = result.getVolumeIdList().get(0);
            ScaleIOHelper.updateSnapshotWithSnapshotVolumeResult(dbClient, blockSnapshot, systemId, nativeId);
            dbClient.persistObject(blockSnapshot);
            ScaleIOHelper.updateStoragePoolCapacity(dbClient, scaleIOHandle, blockSnapshot);
            taskCompleter.ready(dbClient);

        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code = DeviceControllerErrors.scaleio.encounteredAnExceptionFromScaleIOOperation("createSingleVolumeSnapshot",
                    e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void createGroupSnapshots(StorageSystem storage, List<URI> snapshotList, Boolean createInactive,
            Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            ScaleIORestClient scaleIOHandle = scaleIOHandleFactory.using(dbClient).getClientHandle(storage);
            List<BlockSnapshot> blockSnapshots = dbClient.queryObject(BlockSnapshot.class, snapshotList);
            Map<String, String> parent2snap = new HashMap<>();
            Set<URI> poolsToUpdate = new HashSet<>();

            for (BlockSnapshot blockSnapshot : blockSnapshots) {
                Volume parent = dbClient.queryObject(Volume.class, blockSnapshot.getParent().getURI());
                parent2snap.put(parent.getNativeId(), blockSnapshot.getLabel());
                poolsToUpdate.add(parent.getPool());
            }
            String systemId = scaleIOHandle.getSystemId();
            ScaleIOSnapshotVolumeResponse result = scaleIOHandle.snapshotMultiVolume(parent2snap, systemId);

            List<String> nativeIds = result.getVolumeIdList();
            Map<String, String> snapNameIdMap = scaleIOHandle.getVolumes(nativeIds);
            ScaleIOHelper.updateSnapshotsWithSnapshotMultiVolumeResult(dbClient, blockSnapshots, systemId, snapNameIdMap,
                    result.getSnapshotGroupId());
            dbClient.persistObject(blockSnapshots);

            List<StoragePool> pools = dbClient.queryObject(StoragePool.class, Lists.newArrayList(poolsToUpdate));
            for (StoragePool pool : pools) {
                ScaleIOHelper.updateStoragePoolCapacity(dbClient, scaleIOHandle, pool, storage);
            }

            taskCompleter.ready(dbClient);

        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code = DeviceControllerErrors.scaleio.encounteredAnExceptionFromScaleIOOperation("createGroupVolumeSnapshot",
                    e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void activateSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void activateGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            ScaleIORestClient scaleIOHandle = scaleIOHandleFactory.using(dbClient).getClientHandle(storage);
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);

            if (blockSnapshot != null && !blockSnapshot.getInactive() &&
                    // If the blockSnapshot.nativeId is not filled in then the
                    // snapshot create may have failed somehow, so we'll allow
                    // this case to be marked as success, so that the inactive
                    // state against the BlockSnapshot object can be set.
                    !Strings.isNullOrEmpty(blockSnapshot.getNativeId())) {
                scaleIOHandle.removeVolume(blockSnapshot.getNativeId());
            }

            if (blockSnapshot != null) {
                blockSnapshot.setInactive(true);
                dbClient.persistObject(blockSnapshot);
                ScaleIOHelper.updateStoragePoolCapacity(dbClient, scaleIOHandle, blockSnapshot);
            }
            taskCompleter.ready(dbClient);

        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code = DeviceControllerErrors.scaleio.encounteredAnExceptionFromScaleIOOperation("deleteSingleVolumeSnapshot",
                    e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void deleteGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            ScaleIORestClient scaleIOHandle = scaleIOHandleFactory.using(dbClient).getClientHandle(storage);
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);
            Set<URI> poolsToUpdate = new HashSet<>();

            scaleIOHandle.removeConsistencyGroupSnapshot(blockSnapshot.getSnapsetLabel());

            List<BlockSnapshot> groupSnapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(blockSnapshot, dbClient);
            for (BlockSnapshot groupSnapshot : groupSnapshots) {
                Volume parent = dbClient.queryObject(Volume.class, groupSnapshot.getParent().getURI());
                poolsToUpdate.add(parent.getPool());
                groupSnapshot.setInactive(true);
            }
            dbClient.persistObject(groupSnapshots);

            List<StoragePool> pools = dbClient.queryObject(StoragePool.class, Lists.newArrayList(poolsToUpdate));
            for (StoragePool pool : pools) {
                ScaleIOHelper.updateStoragePoolCapacity(dbClient, scaleIOHandle, pool, storage);
            }
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code = DeviceControllerErrors.scaleio.encounteredAnExceptionFromScaleIOOperation("deleteGroupSnapshots",
                    e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void establishVolumeSnapshotGroupRelation(StorageSystem storage, URI sourceVolume,
            URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
