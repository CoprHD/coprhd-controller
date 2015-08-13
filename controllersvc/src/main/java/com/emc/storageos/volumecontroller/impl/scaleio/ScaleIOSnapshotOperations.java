/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.scaleio;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.scaleio.api.ScaleIOCLI;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllCommand;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllVolumesResult;
import com.emc.storageos.scaleio.api.ScaleIORemoveConsistencyGroupSnapshotsResult;
import com.emc.storageos.scaleio.api.ScaleIORemoveVolumeResult;
import com.emc.storageos.scaleio.api.ScaleIOSnapshotMultiVolumeResult;
import com.emc.storageos.scaleio.api.ScaleIOSnapshotVolumeResult;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class ScaleIOSnapshotOperations implements SnapshotOperations {

    private static Logger log = LoggerFactory.getLogger(ScaleIOSnapshotOperations.class);
    private DbClient dbClient;
    private ScaleIOCLIFactory scaleIOCLIFactory;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setScaleIOCLIFactory(ScaleIOCLIFactory scaleIOCLIFactory) {
        this.scaleIOCLIFactory = scaleIOCLIFactory;
    }

    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot, Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storage);
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);

            Volume parent = dbClient.queryObject(Volume.class, blockSnapshot.getParent().getURI());
            String systemId = getScaleIOCustomerId(scaleIOCLI);
            ScaleIOSnapshotVolumeResult result = scaleIOCLI.snapshotVolume(parent.getNativeId(),
                    blockSnapshot.getLabel());

            if (result.isSuccess()) {
                ScaleIOCLIHelper.updateSnapshotWithSnapshotVolumeResult(dbClient, blockSnapshot, systemId, result);
                dbClient.persistObject(blockSnapshot);
                ScaleIOCLIHelper.updateStoragePoolCapacity(dbClient, scaleIOCLI, blockSnapshot);
                taskCompleter.ready(dbClient);
            } else {
                blockSnapshot.setInactive(true);
                dbClient.persistObject(blockSnapshot);
                ServiceCoded code = DeviceControllerErrors.scaleio.createSnapshotError(blockSnapshot.getLabel(),
                        result.getErrorString());
                taskCompleter.error(dbClient, code);
            }
        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.
                            encounteredAnExceptionFromScaleIOOperation("createSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void createGroupSnapshots(StorageSystem storage, List<URI> snapshotList, Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storage);
            List<BlockSnapshot> blockSnapshots = dbClient.queryObject(BlockSnapshot.class, snapshotList);
            Map<String, String> parent2snap = new HashMap<>();
            Set<URI> poolsToUpdate = new HashSet<>();

            for (BlockSnapshot blockSnapshot : blockSnapshots) {
                Volume parent = dbClient.queryObject(Volume.class, blockSnapshot.getParent().getURI());
                parent2snap.put(parent.getNativeId(), blockSnapshot.getLabel());
                poolsToUpdate.add(parent.getPool());
            }
            String systemId = getScaleIOCustomerId(scaleIOCLI);
            ScaleIOSnapshotMultiVolumeResult result = scaleIOCLI.snapshotMultiVolume(parent2snap);

            if (result.isSuccess()) {
                ScaleIOCLIHelper.updateSnapshotsWithSnapshotMultiVolumeResult(dbClient, blockSnapshots, systemId, result);
                // Fix for multi-snapshot output bug (CTRL-4516)
                ScaleIOQueryAllVolumesResult allVolumesResult = scaleIOCLI.queryAllVolumes();
                for (BlockSnapshot snapshot : blockSnapshots) {
                    String id = allVolumesResult.getVolumeIdFromName(snapshot.getLabel());
                    snapshot.setNativeId(id);
                    snapshot.setWWN(ScaleIOCLIHelper.generateWWN(systemId, id));
                }
                dbClient.persistObject(blockSnapshots);

                List<StoragePool> pools = dbClient.queryObject(StoragePool.class, Lists.newArrayList(poolsToUpdate));
                for (StoragePool pool : pools) {
                    ScaleIOCLIHelper.updateStoragePoolCapacity(dbClient, scaleIOCLI, pool, storage);
                }

                taskCompleter.ready(dbClient);
            } else {
                for (BlockSnapshot blockSnapshot : blockSnapshots) {
                    blockSnapshot.setInactive(true);
                }
                dbClient.persistObject(blockSnapshots);
                log.info("Failed to create snapshot: {}", result.getErrorString());
                String uris = Joiner.on(',').join(snapshotList);
                ServiceCoded code = DeviceControllerErrors.scaleio.createSnapshotError(uris, result.getErrorString());
                taskCompleter.error(dbClient, code);
            }
        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.
                            encounteredAnExceptionFromScaleIOOperation("createGroupVolumeSnapshot", e.getMessage());
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
            ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storage);
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);
            boolean anyFailures = false;
            String errorMessage = null;

            if (blockSnapshot != null && !blockSnapshot.getInactive() &&
                    // If the blockSnapshot.nativeId is not filled in then the
                    // snapshot create may have failed somehow, so we'll allow
                    // this case to be marked as success, so that the inactive
                    // state against the BlockSnapshot object can be set.
                    !Strings.isNullOrEmpty(blockSnapshot.getNativeId())) {
                ScaleIORemoveVolumeResult result = scaleIOCLI.removeVolume(blockSnapshot.getNativeId());

                if (!result.isSuccess()) {
                    anyFailures = true;
                    errorMessage = result.errorString();
                }
            }

            if (!anyFailures) {
                if (blockSnapshot != null) {
                    blockSnapshot.setInactive(true);
                    dbClient.persistObject(blockSnapshot);
                    ScaleIOCLIHelper.updateStoragePoolCapacity(dbClient, scaleIOCLI, blockSnapshot);
                }
                taskCompleter.ready(dbClient);
            } else {
                ServiceCoded code = DeviceControllerErrors.scaleio.deleteSnapshotError(blockSnapshot.getLabel(),
                        errorMessage);
                taskCompleter.error(dbClient, code);
            }
        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.
                            encounteredAnExceptionFromScaleIOOperation("deleteSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void deleteGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            ScaleIOCLI scaleIOCLI = scaleIOCLIFactory.using(dbClient).getCLI(storage);
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, snapshot);
            Set<URI> poolsToUpdate = new HashSet<>();

            ScaleIORemoveConsistencyGroupSnapshotsResult result =
                    scaleIOCLI.removeConsistencyGroupSnapshot(blockSnapshot.getSnapsetLabel());

            if (result.isSuccess()) {
                List<BlockSnapshot> groupSnapshots = ControllerUtils.getBlockSnapshotsBySnapsetLabelForProject(blockSnapshot, dbClient);
                for (BlockSnapshot groupSnapshot : groupSnapshots) {
                    Volume parent = dbClient.queryObject(Volume.class, groupSnapshot.getParent().getURI());
                    poolsToUpdate.add(parent.getPool());
                    groupSnapshot.setInactive(true);
                }
                dbClient.persistObject(groupSnapshots);

                List<StoragePool> pools = dbClient.queryObject(StoragePool.class, Lists.newArrayList(poolsToUpdate));
                for (StoragePool pool : pools) {
                    ScaleIOCLIHelper.updateStoragePoolCapacity(dbClient, scaleIOCLI, pool, storage);
                }

                taskCompleter.ready(dbClient);
            } else {
                ServiceCoded code = DeviceControllerErrors.scaleio.deleteSnapshotError(blockSnapshot.getLabel(),
                        result.getErrorString());
                taskCompleter.error(dbClient, code);
            }
        } catch (Exception e) {
            log.error("Encountered an exception", e);
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.
                            encounteredAnExceptionFromScaleIOOperation("deleteGroupSnapshots", e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void restoreGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void copySnapshotToTarget(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void copyGroupSnapshotsToTarget(StorageSystem storage, List<URI> snapshotList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void terminateAnyRestoreSessions(StorageSystem storage, BlockObject from, URI volume,
            TaskCompleter taskCompleter) throws Exception {
        throw new UnsupportedOperationException("Not supported");
    }

    private String getScaleIOCustomerId(ScaleIOCLI scaleIOCLI) {
        ScaleIOQueryAllResult scaleIOQueryAllResult = scaleIOCLI.queryAll();
        return scaleIOQueryAllResult.getProperty(ScaleIOQueryAllCommand.SCALEIO_CUSTOMER_ID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createGroupSnapshotSession(StorageSystem system, List<URI> snapSessionURIs, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkBlockSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI, Boolean createInactive,
            String copyMode, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
