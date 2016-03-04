/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm.xiv;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.AbstractSnapshotOperations;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMCIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMSmisConstants;

public class XIVSnapshotOperations extends AbstractSnapshotOperations {
    private static final Logger _log = LoggerFactory
            .getLogger(XIVSnapshotOperations.class);
    private XIVSmisCommandHelper _helper;
    private IBMCIMObjectPathFactory _cimPath;
    private XIVSmisStorageDevicePostProcessor _smisStorageDevicePostProcessor;

    public void setCimObjectPathFactory(
            IBMCIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    // to suppress Spring ambiguous write method warning
    public IBMCIMObjectPathFactory getCimObjectPathFactory() {
        return _cimPath;
    }

    public void setSmisCommandHelper(XIVSmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    // to suppress Spring ambiguous write method warning
    public XIVSmisCommandHelper getSmisCommandHelper() {
        return _helper;
    }

    public void setSmisStorageDevicePostProcessor(
            XIVSmisStorageDevicePostProcessor smisStorageDevicePostProcessor) {
        _smisStorageDevicePostProcessor = smisStorageDevicePostProcessor;
    }

    /**
     * Should implement creation of a single volume snapshot. That is a volume
     * that is not in any consistency group.
     * 
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param snapshot
     *            [required] - BlockSnapshot URI representing the previously
     *            created snap for the volume
     * @param taskCompleter
     *            - TaskCompleter object used for the updating operation status.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("createSingleVolumeSnapshot operation START");
        try {
            BlockSnapshot snapshotObj = _dbClient.queryObject(
                    BlockSnapshot.class, snapshot);
            Volume volume = _dbClient.queryObject(Volume.class,
                    snapshotObj.getParent());
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume
                    .getTenant().getURI());
            String tenantName = tenant.getLabel();
            String snapLabelToUse = _nameGenerator.generate(tenantName,
                    snapshotObj.getLabel(), snapshot.toString(), '-',
                    IBMSmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
            CIMArgument[] inArgs = _helper
                    .getCreateElementReplicaSnapInputArguments(storage, volume,
                            createInactive, snapLabelToUse);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.callReplicationSvc(storage,
                    IBMSmisConstants.CREATE_ELEMENT_REPLICA, inArgs, outArgs);
            _smisStorageDevicePostProcessor.processSnapshotCreation(storage,
                    snapshot, !createInactive, outArgs,
                    (BlockSnapshotCreateCompleter) taskCompleter);
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis
                    .unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
            setInactive(snapshot, true);
        }
    }

    /**
     * Implementation for restoring of a single volume snapshot restore. That
     * is, this volume is independent of other volumes and a snapshot was taken
     * previously, and now we want to restore that snap to the original volume.
     * 
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param volume
     *            [required] - Volume URI for the volume to be restored
     * @param snapshot
     *            [required] - BlockSnapshot URI representing the previously
     *            created snap for the volume
     * @param taskCompleter
     *            - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            BlockSnapshot from = _dbClient.queryObject(BlockSnapshot.class,
                    snapshot);
            Volume to = _dbClient.queryObject(Volume.class, volume);
            CIMObjectPath syncObjectPath = _cimPath
                    .getSyncObject(storage, from);
            if (_helper.checkExists(storage, syncObjectPath, false, false) != null) {
                _log.info(
                        "Volume {} is not thinly provisioned, will attempt restore",
                        to.getLabel());
                _helper.callModifyReplica(storage, _helper
                        .getRestoreFromSnapshotInputArguments(syncObjectPath));
                taskCompleter.ready(_dbClient);
            } else {
                ServiceError error = DeviceControllerErrors.smis
                        .unableToFindSynchPath(storage.getLabel());
                taskCompleter.error(_dbClient, error);
            }
        } catch (WBEMException e) {
            String message = String
                    .format("Error encountered when trying to restore from snapshot %s on array %s",
                            snapshot.toString(), storage.getLabel());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis
                    .unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            String message = String
                    .format("Generic exception when trying to restore from snapshot %s on array %s",
                            snapshot.toString(), storage.getLabel());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "restoreSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Should implement deletion of single volume snapshot. That is, deleting a
     * snap that was created independent of other volumes.
     * 
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param snapshot
     *            [required] - BlockSnapshot URI representing the previously
     *            created snap for the volume
     * @param taskCompleter
     *            - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class,
                    snapshot);
            CIMObjectPath syncObjectPath = _cimPath
                    .getSyncObject(storage, snap);
            if (_helper.checkExists(storage, syncObjectPath, false, false) != null) {
                _helper.callModifyReplica(
                        storage,
                        _helper.getDeleteSnapshotSynchronousInputArguments(syncObjectPath));
            }

            // set regardless sync object path exists or not
            // Perhaps, it's already been deleted or was deleted on the array.
            // In that case, we'll just say all is well, so that this operation
            // is idempotent.
            snap.setInactive(true);
            snap.setIsSyncActive(false);
            _dbClient.persistObject(snap);
            taskCompleter.ready(_dbClient);
        } catch (WBEMException e) {
            String message = String.format(
                    "Error encountered during delete snapshot %s on array %s",
                    snapshot.toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis
                    .unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            String message = String
                    .format("Generic exception when trying to delete snapshot %s on array %s",
                            snapshot.toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "deleteSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Should implement create of a snapshot from a source volume that is part
     * of a consistency group.
     * 
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param taskCompleter
     *            - TaskCompleter object used for the updating operation status.
     * @param snapshot
     *            [required] - BlockSnapshot URI representing the previously
     *            created snap for the volume
     * @throws DeviceControllerException
     */
    @SuppressWarnings("rawtypes")
    @Override
    public void createGroupSnapshots(StorageSystem storage,
            List<URI> snapshotList, Boolean createInactive,
            Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            URI snapshot = snapshotList.get(0);
            BlockSnapshot snapshotObj = _dbClient.queryObject(
                    BlockSnapshot.class, snapshot);

            Volume volume = _dbClient.queryObject(Volume.class,
                    snapshotObj.getParent());
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume
                    .getTenant().getURI());
            String tenantName = tenant.getLabel();
            String snapLabelToUse = _nameGenerator.generate(tenantName,
                    snapshotObj.getSnapsetLabel(), snapshot.toString(), '-',
                    IBMSmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
            // check if the snapshot group name is used on array, return if the name is used to avoid unnecessary CIM call
            // snapshot group name has to be unique on array
            CIMObjectPath sgPath = _cimPath.getSnapshotGroupPath(storage, snapLabelToUse);
            if (sgPath != null) {
                _log.error("Failed to create group snapshots: " + IBMSmisConstants.DUPLICATED_SG_NAME_ERROR);
                ServiceError error = DeviceControllerErrors.smis.methodFailed(
                        "createGroupSnapshots", IBMSmisConstants.DUPLICATED_SG_NAME_ERROR);
                taskCompleter.error(_dbClient, error);
                setInactive(snapshotList, true);
                return;
            }

            String groupName = _helper.getConsistencyGroupName(snapshotObj, storage);
            CIMObjectPath cgPath = _cimPath.getConsistencyGroupPath(storage,
                    groupName);
            CIMArgument[] inArgs = _helper.getCreateGroupReplicaInputArguments(
                    storage, cgPath, createInactive, snapLabelToUse);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.callReplicationSvc(storage,
                    IBMSmisConstants.CREATE_GROUP_REPLICA, inArgs, outArgs);
            _smisStorageDevicePostProcessor.processCGSnapshotCreation(storage,
                    snapshotList, !createInactive, snapLabelToUse,
                    (BlockSnapshotCreateCompleter) taskCompleter);
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis
                    .unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
            setInactive(snapshotList, true);
        }
    }

    /**
     * Implementation should restore the set of snapshots that were taken for a
     * set of volumes in a consistency group. That is, at some time there was a
     * consistency group of volumes created and snapshot was taken of these;
     * these snapshots would belong to a "snap-set". This restore operation,
     * will restore the volumes in the consistency group from this snap-set. Any
     * snapshot from the snap-set can be provided to restore the whole snap-set.
     * 
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param snapshot
     *            [required] - BlockSnapshot URI representing the previously
     *            created snap for the volume
     * @param taskCompleter
     *            - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void restoreGroupSnapshots(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            BlockSnapshot snapshotObj = _dbClient.queryObject(
                    BlockSnapshot.class, snapshot);
            String snapshotGroupName = snapshotObj.getReplicationGroupInstance();
            CIMObjectPath groupSynchronized = _cimPath
                    .getGroupSynchronizedPath(storage, snapshotGroupName);
            if (groupSynchronized != null) {
                // restore snapshot
                _helper.callModifyReplica(
                        storage,
                        _helper.getRestoreFromSnapshotInputArguments(groupSynchronized));
                taskCompleter.ready(_dbClient);
            } else {
                _log.error("No GroupSynchronized object found for " + snapshotGroupName);
                ServiceError error = DeviceControllerErrors.smis.methodFailed(
                        "restoreGroupSnapshots", "No GroupSynchronized object found");
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            String message = String
                    .format("Generic exception when trying to restoring snapshots from consistency group on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "restoreGroupSnapshots", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Should implement clean up of all the snapshots in a volume consistency
     * group 'snap-set'. The 'snap-set' is a set of block snapshots created for
     * a set of volumes in a consistency group.
     * 
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param snapshot
     *            [required] - BlockSnapshot object representing the previously
     *            created snap for the volume
     * @param taskCompleter
     *            - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void deleteGroupSnapshots(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            _log.info("deleteGroupSnapshots operation {}", snapshot);
            List<BlockSnapshot> snapshots = _dbClient.queryObject(
                    BlockSnapshot.class, Arrays.asList(snapshot));
            BlockSnapshot snapshotObj = snapshots.get(0);
            CIMObjectPath groupSynchronized = _cimPath.getGroupSynchronizedPath(storage, snapshotObj.getReplicationGroupInstance());
            if (groupSynchronized != null) {
                // Delete the snapshot group (not the consistency group). This
                // group is for
                // the specific snaps that were taken for the consistency group.
                try {
                    _helper.callModifyReplica(storage,
                            _helper.getReturnGroupSyncToPoolInputArguments(groupSynchronized));
                } catch (Exception e) {
                    // If the snapshot group doesn't exist, ignore the exception.
                    // This is necessary in case of removing multiple snapshots from UI.
                    // E.g., snapshots v1_s1 and v2_s1 are part of snapshot group 1,
                    // v1_s2 and v2_s2 are part of snapshot group 2.
                    // When removing the four snapshots from UI in one request, it will create four tasks for deleting
                    // the snapshots from the two snapshot groups. Essentially, two tasks will race on the deleting.
                    // SMI-S call will return code 36944 if the groupSynchronized instance doesn't exist anymore.
                    CIMObjectPath snapGroup = (CIMObjectPath) groupSynchronized.getKeyValue(IBMSmisConstants.CP_SYNCED_ELEMENT);
                    if (_helper.checkExists(storage, snapGroup, false, false) != null) {
                        throw e;
                    }
                }
            }

            // set inactive=true for all snapshots in the snaps set
            List<BlockSnapshot> snaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshotObj, _dbClient);
            for (BlockSnapshot snap : snaps) {
                snap.setInactive(true);
                snap.setIsSyncActive(false);
                _dbClient.persistObject(snap);
            }
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            String message = String
                    .format("Generic exception when trying to delete snapshots from consistency group on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "deleteGorupSnapshots", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void activateSingleVolumeSnapshot(StorageSystem storage,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // not supported by IBM XIV
    }

    @Override
    public void activateGroupSnapshots(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        // not supported by IBM XIV
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
    public void createGroupSnapshotSession(StorageSystem system, URI snapSessionURI, String groupName, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            String copyMode, Boolean targetExists, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void linkSnapshotSessionTargetGroup(StorageSystem system, URI snapshotSessionURI, List<URI> snapSessionSnapshotURIs,
            String copyMode, Boolean targetsExist, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkSnapshotSessionTarget(StorageSystem system, URI tgtSnapSessionURI, URI snapshotURI,
            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkSnapshotSessionTargetGroup(StorageSystem system, URI tgtSnapSessionURI, URI snapshotURI,
                                            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlinkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            Boolean deleteTarget, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshotSession(StorageSystem system, URI snapSessionURI, String groupName, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
