/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vnx;

import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefreshIfRequired;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.providerfinders.FindProviderFactory;
import com.emc.storageos.volumecontroller.impl.smis.AbstractSnapshotOperations;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants.SYNC_TYPE;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockCreateCGSnapshotJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockRestoreSnapshotJob;
import com.emc.storageos.workflow.WorkflowException;

public class VnxSnapshotOperations extends AbstractSnapshotOperations {
    private static final Logger _log = LoggerFactory.getLogger(VnxSnapshotOperations.class);

    private FindProviderFactory findProviderFactory;

    public void setFindProviderFactory(final FindProviderFactory findProviderFactory) {
        this.findProviderFactory = findProviderFactory;
    }

    /**
     * This interface is for the snapshot active. The createSnapshot may have done
     * whatever is necessary to setup the snapshot for this call. The goal is to
     * make this a quick operation and the create operation has already done a lot
     * of the "heavy lifting".
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void activateSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        BlockSnapshot snapshotObj = null;
        try {
            snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            if (snapshotObj.getIsSyncActive()) {
                taskCompleter.ready(_dbClient);
                return;
            }
            BlockObject bo = BlockObject.fetch(_dbClient,
                    snapshotObj.getParent().getURI());
            _log.info("activateSingleVolumeSnapshot operation START");
            CIMObjectPath blockObjectPath = _cimPath.getBlockObjectPath(storage, bo);
            CIMArgument[] inArgs =
                    _helper.getCreateSynchronizationAspectInput(blockObjectPath, true, null, null);
            CIMArgument[] outArgs = new CIMArgument[5];
            CIMObjectPath replicationSvcPath =
                    _cimPath.getControllerReplicationSvcPath(storage);
            _helper.invokeMethod(storage, replicationSvcPath,
                    SmisConstants.CREATE_SYNCHRONIZATION_ASPECT, inArgs, outArgs);
            setIsSyncActive(snapshotObj, true);
            CIMObjectPath settingsPath = (CIMObjectPath) outArgs[0].getValue();
            CIMObjectPath syncPath = (CIMObjectPath) settingsPath.getKey(SmisConstants.CP_SETTING_DATA).getValue();
            String instanceId = (String) syncPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue();
            snapshotObj.setSettingsInstance(instanceId);
            snapshotObj.setNeedsCopyToTarget(true);
            snapshotObj.setRefreshRequired(true);
            _dbClient.persistObject(snapshotObj);
            // Success -- Update status
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } finally {
            _log.info("activateSingleVolumeSnapshot operation END");
        }
    }

    /**
     * This interface is for the snapshot active. The createSnapshot may have done
     * whatever is necessary to setup the snapshot for this call. The goal is to
     * make this a quick operation and the create operation has already done a lot
     * of the "heavy lifting".
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void activateGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        List<BlockSnapshot> snapshots = null;
        try {
            _log.info("activateGroupSnapshots operation START");
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            if (snapshotObj.getIsSyncActive()) {
                taskCompleter.ready(_dbClient);
                return;
            }

            // Check if the consistency group exists
            String consistencyGroupName = _helper.getSourceConsistencyGroupName(snapshotObj);
            storage = findProviderFactory.withGroup(storage, consistencyGroupName).find();

            if (storage == null) {
                ServiceError error = DeviceControllerErrors.smis.noConsistencyGroupWithGivenName();
                taskCompleter.error(_dbClient, error);
                return;
            }

            CIMObjectPath replicationGroupPath =
                    _cimPath.getReplicationGroupPath(storage, consistencyGroupName);
            CIMArgument[] inArgs =
                    _helper.getCreateGroupSynchronizationAspectInput(replicationGroupPath);
            CIMArgument[] outArgs = new CIMArgument[5];
            CIMObjectPath replicationSvcPath =
                    _cimPath.getControllerReplicationSvcPath(storage);
            _helper.invokeMethod(storage, replicationSvcPath,
                    SmisConstants.CREATE_SYNCHRONIZATION_ASPECT, inArgs, outArgs);
            snapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshotObj, _dbClient);
            setIsSyncActive(snapshots, true);
            // Get the settings object and apply it to all the snap objects
            CIMObjectPath settingsPath = (CIMObjectPath) outArgs[0].getValue();
            CIMObjectPath syncPath = (CIMObjectPath) settingsPath.getKey(SmisConstants.CP_SETTING_DATA).getValue();
            String instanceId = (String) syncPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue();
            for (BlockSnapshot it : snapshots) {
                it.setSettingsGroupInstance(instanceId);
                it.setNeedsCopyToTarget(true);
                it.setRefreshRequired(true);
            }
            _dbClient.persistObject(snapshots);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } finally {
            _log.info("activateGroupSnapshots operation END");
        }
    }

    /**
     * Should implement deletion of single volume snapshot. That is, deleting a snap that was
     * created independent of other volumes.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage,
                    Arrays.asList(snapshot));
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            CIMObjectPath syncObjectPath = _cimPath.getSyncObject(storage, snap);
            if (_helper.checkExists(storage, syncObjectPath, false, false) != null) {
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.callModifyReplica(storage, _helper.getDeleteSnapshotSynchronousInputArguments(syncObjectPath), outArgs);
                snap.setInactive(true);
                snap.setIsSyncActive(false);
                _dbClient.persistObject(snap);
                taskCompleter.ready(_dbClient);
            } else {
                // Perhaps, it's already been deleted or was deleted on the array.
                // In that case, we'll just say all is well, so that this operation
                // is idempotent.
                snap.setInactive(true);
                snap.setIsSyncActive(false);
                _dbClient.persistObject(snap);
                taskCompleter.ready(_dbClient);
            }
        } catch (WBEMException e) {
            String message = String.format("Error encountered during delete snapshot %s on array %s",
                    snapshot.toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            String message = String.format("Generic exception when trying to delete snapshot %s on array %s",
                    snapshot.toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("deleteSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Should implement create of a snapshot from a source volume that is part of a
     * consistency group.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param createInactive - Indicates if the snapshots should be created but not
     *            activated
     * @param readOnly - Indicates if the snapshot should be read only.
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    @Override
    public void createGroupSnapshots(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            URI snapshot = snapshotList.get(0);
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            Volume volume = _dbClient.queryObject(Volume.class, snapshotObj.getParent());
            if (ControllerUtils.isNotInRealVNXRG(volume, _dbClient)) {
                throw DeviceControllerException.exceptions.groupSnapshotNotSupported(volume.getReplicationGroupInstance());
            }

            // CTRL-5640: ReplicationGroup may not be accessible after provider fail-over.
            ReplicationUtils.checkReplicationGroupAccessibleOrFail(storage, snapshotObj, _dbClient, _helper, _cimPath);
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume.getTenant().getURI());
            String tenantName = tenant.getLabel();
            String snapLabelToUse =
                    _nameGenerator.generate(tenantName, snapshotObj.getLabel(),
                            snapshot.toString(), '-', SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
            String groupName = _helper.getSourceConsistencyGroupName(snapshotObj);
            CIMObjectPath cgPath = _cimPath.getReplicationGroupPath(storage, groupName);
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] inArgs = _helper.getCreateGroupReplicaInputArgumentsForVNX(storage, cgPath,
                    createInactive, snapLabelToUse, SYNC_TYPE.SNAPSHOT.getValue());
            CIMArgument[] outArgs = new CIMArgument[5];

            _helper.invokeMethod(storage, replicationSvc, SmisConstants.CREATE_GROUP_REPLICA, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new SmisBlockCreateCGSnapshotJob(job,
                                storage.getId(), !createInactive, null, taskCompleter)));
            }
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
            setInactive(((BlockSnapshotCreateCompleter) taskCompleter).getSnapshotURIs(), true);
        }
    }

    /**
     * Should implement clean up of all the snapshots in a volume consistency
     * group 'snap-set'. The 'snap-set' is a set of block snapshots created for a
     * set of volumes in a consistency group.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot object representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void deleteGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage,
                    Arrays.asList(snapshot));
            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));
            BlockSnapshot snapshotObj = snapshots.get(0);

            // Check if the consistency group exists
            String consistencyGroupName = _helper.getSourceConsistencyGroupName(snapshotObj);
            StorageSystem newStorage = findProviderFactory.withGroup(storage, consistencyGroupName).find();

            if (newStorage == null) {
                _log.warn("Replication Group {} not found.", consistencyGroupName);
                // Don't return, let the below code do its clean-up.
            } // else - storage will have right Provider to use.

            String snapshotGroupName = snapshotObj.getReplicationGroupInstance();
            boolean snapshotGroupExists = false;
            CIMObjectPath groupSynchronized = _cimPath.getGroupSynchronizedPath(storage, consistencyGroupName, snapshotGroupName);
            if (_helper.checkExists(storage, groupSynchronized, false, false) != null) {
                // Delete the snapshot group (not the consistency group). This group is for
                // the specific snaps that were taken for the consistency group.
                snapshotGroupExists = true;
                CIMArgument[] returnSnapGroupInput = _helper.getReturnGroupSyncToPoolInputArguments(groupSynchronized);
                _helper.callModifyReplica(storage, returnSnapGroupInput);
            }

            // Individually delete each snap in the snapshot group
            boolean hadDeleteFailure = false;
            List<BlockSnapshot> snaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshotObj, _dbClient);
            if (snapshotGroupExists) {
                for (BlockSnapshot snap : snaps) {
                    _log.info(String.format("vnxDeleteGroupSnapshots -- deleting snapshot %s", snap.getId().toString()));
                    if (!deleteConsistencyGroupSnapshot(storage, snap, taskCompleter)) {
                        // Delete has failed, it would have called complete task
                        hadDeleteFailure = true;
                    }
                }
            }
            if (!hadDeleteFailure) {
                // Set inactive=true for all snapshots in the snaps set
                for (BlockSnapshot snap : snaps) {
                    snap.setInactive(true);
                    snap.setIsSyncActive(false);
                    _dbClient.persistObject(snap);
                }
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            String message =
                    String.format("Generic exception when trying to delete snapshots from consistency group on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("deleteGorupSnapshots", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Implementation for restoring of a single volume snapshot restore. That is, this
     * volume is independent of other volumes and a snapshot was taken previously, and
     * now we want to restore that snap to the original volume.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param volume [required] - Volume URI for the volume to be restored
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage,
                    Arrays.asList(snapshot));
            BlockSnapshot from = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            Volume to = _dbClient.queryObject(Volume.class, volume);
            CIMObjectPath syncObjectPath = _cimPath.getSyncObject(storage, from);
            if (_helper.checkExists(storage, syncObjectPath, false, false) != null) {
                CIMObjectPath cimJob;
                if (_helper.isThinlyProvisioned(storage, to) || isBasedOnVNXThinStoragePool(to)) {
                    _log.info(
                            "Volume {} is thinly provisioned or based on a Thin StoragePool, need to deactivate the volume before restore",
                            to.getLabel());
                    deactivateSnapshot(storage, from, syncObjectPath);
                    cimJob = _helper
                            .callModifySettingsDefineState(storage, _helper.getRestoreFromSnapshotInputArguments(storage, to, from));
                } else {
                    // Thick volumes do not need to be deactivated prior to restore.
                    // The can be restored directly.
                    _log.info("Volume {} is not thinly provisioned, will attempt restore", to.getLabel());
                    cimJob = _helper.callModifyReplica(storage, _helper.getRestoreFromReplicaInputArguments(syncObjectPath));
                }
                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisBlockRestoreSnapshotJob(cimJob, storage.getId(), taskCompleter)));
            } else {
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(storage.getLabel());
                taskCompleter.error(_dbClient, error);
            }
        } catch (WBEMException e) {
            String message = String.format("Error encountered when trying to restore from snapshot %s on array %s",
                    snapshot.toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            String message = String.format("Generic exception when trying to restore from snapshot %s on array %s",
                    snapshot.toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("restoreSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Implementation should restore the set of snapshots that were taken for a set of
     * volumes in a consistency group. That is, at some time there was a consistency
     * group of volumes created and snapshot was taken of these; these snapshots would
     * belong to a "snap-set". This restore operation, will restore the volumes in the
     * consistency group from this snap-set. Any snapshot from the snap-set can be
     * provided to restore the whole snap-set.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshotURI [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void restoreGroupSnapshots(StorageSystem storage, URI volume, URI snapshotURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage,
                    Arrays.asList(snapshotURI));
            final BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshotURI);

            // Check if the consistency group exists
            final String consistencyGroupName = _helper.getSourceConsistencyGroupName(snapshotObj);
            storage = findProviderFactory.withGroup(storage, consistencyGroupName).find();

            if (storage == null) {
                ServiceError error = DeviceControllerErrors.smis.noConsistencyGroupWithGivenName();
                taskCompleter.error(_dbClient, error);
                return;
            }

            final String snapshotGroupName = snapshotObj.getReplicationGroupInstance();
            final CIMObjectPath groupSynchronized = _cimPath.getGroupSynchronizedPath(storage, consistencyGroupName, snapshotGroupName);
            final CIMInstance groupSynchronizedInstance = _helper.checkExists(storage, groupSynchronized, false, false);
            List<BlockSnapshot> snapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshotObj, _dbClient);
            if (groupSynchronizedInstance != null) {
                // Check if the snapshot requires a copy-to-target. This is essentially
                // the operation that would make the snapshot 'active', though it's
                // different from the ViPR snapshot activate. The copy-to-target would
                // usually be done after a ViPR snapshot activate and as part of a
                // snapshot export operation. If the copy-to-target is required, then it
                // would mean that there wasn't any export performed.
                if (snapshotObj.getNeedsCopyToTarget()) {
                    _log.info("Consistency group {} snapshots require copy-to-target",
                            consistencyGroupName);
                    List<URI> snapshotList = new ArrayList<URI>();
                    for (BlockSnapshot snapshot : snapshots) {
                        snapshotList.add(snapshot.getId());
                    }
                    internalGroupSnapCopyToTarget(storage, snapshotObj, snapshotList);
                }
                CIMObjectPath settingsPathFromOutputArg = null;
                // Deactivate Synchronization if not already deactivated
                String copyState = groupSynchronizedInstance.getPropertyValue(SmisConstants.CP_COPY_STATE).toString();
                if (!String.valueOf(SmisConstants.INACTIVE_VALUE).equalsIgnoreCase(copyState)) {
                    CIMArgument[] deactivateGroupInput = _helper.getDeactivateSnapshotSynchronousInputArguments(groupSynchronized);
                    CIMArgument[] outArgs = new CIMArgument[5];
                    _helper.callModifyReplica(storage, deactivateGroupInput, outArgs);
                    settingsPathFromOutputArg = (CIMObjectPath) outArgs[0].getValue();
                }

                final boolean isSynchronizationAspectSet = snapshotObj.getSettingsGroupInstance() != null;

                // Get the Clar_SettingsDefineState_RG_SAFS path
                final CIMObjectPath settingsPath = isSynchronizationAspectSet ?
                        _helper.getSettingsDefineStateForSourceGroup(storage, snapshotObj.getSettingsGroupInstance()) :
                        settingsPathFromOutputArg;

                // If the Clar_SynchronizationAspectForSourceGroup hasn't been set in the snapshots, then set it.
                if (!isSynchronizationAspectSet) {
                    CIMObjectPath syncPath = (CIMObjectPath) settingsPath.getKey(SmisConstants.CP_SETTING_DATA).getValue();
                    String instanceId = (String) syncPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue();
                    for (BlockSnapshot it : snapshots) {
                        it.setSettingsGroupInstance(instanceId);
                    }
                    _dbClient.persistObject(snapshots);
                }

                // Restore snapshot
                CIMArgument[] restoreInput = _helper.getRestoreFromSettingsStateInputArguments(settingsPath);
                CIMObjectPath cimJob = _helper.callModifySettingsDefineState(storage, restoreInput);
                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisBlockRestoreSnapshotJob(cimJob, storage.getId(), taskCompleter)));
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            String message =
                    String.format("Generic exception when trying to restoring snapshots from consistency group on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("restoreGroupSnapshots", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void copySnapshotToTarget(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            _log.info("copySnapshotToTarget operation START");
            callEMCRefreshIfRequired(_dbClient, _helper, storage,
                    Arrays.asList(snapshot));
            BlockSnapshot snapshotObj =
                    _dbClient.queryObject(BlockSnapshot.class, snapshot);
            CIMObjectPath target = _cimPath.getBlockObjectPath(storage, snapshotObj);

            if (snapshotObj.getSettingsInstance() == null) {
                _log.error("Snap session is null for target {}", snapshotObj.getSnapsetLabel());
                taskCompleter.error(_dbClient, DeviceControllerErrors.vnx
                        .copySnapshotToTargetSettingsInstanceNull(snapshotObj
                                .getSnapsetLabel(), snapshotObj.getId().toString()));
                return;
            }

            CIMObjectPath settingsState =
                    _helper.getSettingsDefineStateForSource(storage,
                            snapshotObj.getSettingsInstance());
            CIMArgument[] inArgs =
                    _helper.getVNXCopyToTargetInputArguments(settingsState, target);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.callModifySettingsDefineState(storage, inArgs, outArgs);
            snapshotObj.setNeedsCopyToTarget(true);
            _dbClient.persistObject(snapshotObj);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Exception in copySnapshotToTarget", e);
            taskCompleter.error(_dbClient, DeviceControllerErrors.vnx
                    .copySnapshotToTargetException(e));
        }
    }

    /**
     * This operation will make the call to copy the source information to the target
     * volumes of the snap.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshotList [required] - List of URIs pointing to BlockSnapshots
     * @param taskCompleter [required] - TaskCompleter to update with status.
     * 
     * @throws DeviceControllerException
     */
    @Override
    public void copyGroupSnapshotsToTarget(StorageSystem storage,
            List<URI> snapshotList,
            TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            _log.info("copyGroupSnapshotsToTarget operation START");
            callEMCRefreshIfRequired(_dbClient, _helper, storage, snapshotList);
            BlockSnapshot snapshot =
                    _dbClient.queryObject(BlockSnapshot.class, snapshotList.get(0));

            if (snapshot.getSettingsGroupInstance() == null) {
                taskCompleter.error(_dbClient, DeviceControllerErrors.vnx
                        .copyGroupSnapshotsToTargetSettingsInstanceNull(snapshot
                                .getSnapsetLabel(), snapshot.getId().toString()));
                return;
            }

            internalGroupSnapCopyToTarget(storage, snapshot, snapshotList);

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Exception in copyGroupSnapshotsToTarget", e);
            taskCompleter.error(_dbClient, DeviceControllerErrors.vnx
                    .copyGroupSnapshotsToTargetException(e));
        }
    }

    /**
     * This method will delete a snapshot that was created as part of volume consistency
     * group 'snap-set'.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @return true - All the snaps in the 'snap-set' where deleted successfully.
     * @throws WorkflowException
     */
    private boolean deleteConsistencyGroupSnapshot(StorageSystem storage, BlockSnapshot snap, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        boolean wasSuccess = false;
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage,
                    Arrays.asList(snap.getId()));
            CIMObjectPath syncObjectPath = _cimPath.getSyncObject(storage, snap);
            if (_helper.checkExists(storage, syncObjectPath, false, false) != null) {
                deactivateSnapshot(storage, snap, syncObjectPath);
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.callModifyReplica(storage, _helper.getDeleteSnapshotSynchronousInputArguments(syncObjectPath), outArgs);
                if (snap.getSettingsInstance() != null) {
                    Volume volume = _dbClient.queryObject(Volume.class, snap.getParent());
                    outArgs = new CIMArgument[5];
                    _helper.callModifySettingsDefineState(storage,
                            _helper.getDeleteSettingsForSnapshotInputArguments(storage, volume, snap), outArgs);
                }
            }
            wasSuccess = true;
        } catch (WBEMException e) {
            String message = String.format("Error encountered during delete snapshot %s on array %s",
                    snap.getId().toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            String message = String.format("Generic exception when trying to delete snapshot %s on array %s",
                    snap.getId().toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("deleteConsistencyGroupSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        return wasSuccess;
    }

    /**
     * Internal function to call SMI-S to run Copy-to-Target for CG snapshot
     * 
     * @param storage [in] - StorageSystem object
     * @param snapshot [in] - BlockSnapshot object. One of the snaps in the CG
     * @param snapshotList [in] - List of BlockSnapshot URIs. These are all the snaps in
     *            CG snap set.
     * @throws Exception
     */
    private void internalGroupSnapCopyToTarget(StorageSystem storage,
            BlockSnapshot snapshot,
            List<URI> snapshotList)
            throws Exception {
        String snapGroupName = snapshot.getReplicationGroupInstance();
        CIMObjectPath targetGroup =
                _cimPath.getReplicationGroupPath(storage, snapGroupName);
        CIMObjectPath settingsState =
                _helper.getSettingsDefineStateForSourceGroup(storage,
                        snapshot.getSettingsGroupInstance());
        CIMArgument[] inArgs =
                _helper.getVNXCopyToTargetGroupInputArguments(settingsState,
                        targetGroup);
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.callModifySettingsDefineState(storage, inArgs, outArgs);
        List<BlockSnapshot> snapshots =
                _dbClient.queryObject(BlockSnapshot.class, snapshotList);
        for (BlockSnapshot it : snapshots) {
            it.setNeedsCopyToTarget(false);
        }
        _dbClient.persistObject(snapshots);
    }

    /**
     * Check if 'volume' is based on a ThinPool
     * 
     * @param volume [in] Volume object used for checking the StoragePool
     * @return true iff 'volume' is based of a StoragePool that supports Thin volumes.
     */
    private boolean isBasedOnVNXThinStoragePool(Volume volume) {
        boolean result = false;
        if (volume != null) {
            StoragePool pool = _dbClient.queryObject(StoragePool.class, volume.getPool());
            if (pool != null) {
                String supportedResourceTypes = pool.getSupportedResourceTypes();
                result = (supportedResourceTypes.equals(StoragePool.SupportedResourceTypes.THIN_ONLY.name()) ||
                        supportedResourceTypes.equals(StoragePool.SupportedResourceTypes.THIN_AND_THICK.name()));
            }
        }
        return result;
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
    public void linkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            String copyMode, Boolean targetExists, TaskCompleter completer)
            throws DeviceControllerException {
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
    public void deleteSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
