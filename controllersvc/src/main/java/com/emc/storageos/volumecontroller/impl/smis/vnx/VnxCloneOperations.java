/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vnx;

import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefreshIfRequired;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CREATE_LIST_REPLICA;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.JOB;
import static java.text.MessageFormat.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.AbstractCloneOperations;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisVnxCreateCGCloneJob;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyGroupUtils;

/**
 * For VNX, clone would be smi-s mirror (Snapview clone)
 * 
 * 
 */
public class VnxCloneOperations extends AbstractCloneOperations {
    private static final Logger log = LoggerFactory.getLogger(VnxCloneOperations.class);
    private static final String MODIFY_GROUP_ERROR = "Failed to modify group full copy.";

    /**
     * Should implement create of a clone from a source volume that is part of a
     * consistency group.
     * 
     * Implementation note: In this method we will kick of the asynchronous creation
     * of the target devices required for the CG clones. Upon the successful
     * device creation, the post operations will take place, which will include the
     * creation of the target group and the group clone operation.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param clonetList [required] - clone URI list
     * @param createInactive whether the clone needs to to be created with sync_active=true/false
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
            Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {

        log.info("START create group clone operation");

        // List of target device ids
        List<String> targetDeviceIds = null;

        // The source consistency group name
        String sourceGroupName = null;

        try {
            final Volume first = _dbClient.queryObject(Volume.class, cloneList.get(0));
            Volume sourceVolume = _dbClient.queryObject(Volume.class, first.getAssociatedSourceVolume());
            sourceGroupName = ConsistencyGroupUtils.getSourceConsistencyGroupName(sourceVolume, _dbClient);

            if (!ControllerUtils.isNotInRealVNXRG(sourceVolume, _dbClient)) {
                // CTRL-5640: ReplicationGroup may not be accessible after provider fail-over.
                ReplicationUtils.checkReplicationGroupAccessibleOrFail(storage, sourceVolume, _dbClient, _helper, _cimPath);
            }

            // Group volumes by pool and size
            List<String> sourceIds = new ArrayList<String>();
            List<Volume> clones = _dbClient.queryObject(Volume.class, cloneList);
            for (Volume clone : clones) {
                final Volume volume = _dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
                sourceIds.add(volume.getNativeId());
            }

            targetDeviceIds = new ArrayList<String>();
            for (Volume clone : clones) {
                final URI poolId = clone.getPool();
                Volume source = _dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
                // Create target devices
                final List<String> newDeviceIds = ReplicationUtils.createTargetDevices(storage, sourceGroupName, clone.getLabel(),
                        createInactive,
                        1, poolId, clone.getCapacity(), source.getThinlyProvisioned(), source, taskCompleter, _dbClient, _helper,
                        _cimPath);

                targetDeviceIds.addAll(newDeviceIds);
            }

            CIMObjectPath[] cloneVolumePaths = _cimPath
                    .getVolumePaths(storage, targetDeviceIds.toArray(new String[targetDeviceIds.size()]));
            CIMObjectPath[] sourceVolumePaths = _cimPath.getVolumePaths(storage, sourceIds.toArray(new String[sourceIds.size()]));

            // Create list replica
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] inArgs = _helper.getCreateListReplicaInputArguments(storage, sourceVolumePaths, cloneVolumePaths);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, replicationSvc, CREATE_LIST_REPLICA, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, JOB);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new SmisVnxCreateCGCloneJob(job,
                                storage.getId(), !createInactive, taskCompleter)));
            }

        } catch (Exception e) {
            final String errMsg = format(
                    "An exception occurred when trying to create clones for consistency group {0} on storage system {1}",
                    sourceGroupName, storage.getId());
            log.error(errMsg, e);
            // Roll back changes
            ReplicationUtils.rollbackCreateReplica(storage, null, targetDeviceIds, taskCompleter, _dbClient, _helper, _cimPath);
            List<Volume> clones = _dbClient.queryObject(Volume.class, cloneList);
            for (Volume clone : clones) {
                clone.setInactive(true);
            }
            _dbClient.persistObject(clones);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createGroupClones", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        log.info("createGroupClone operation END");
    }

    /**
     * For VNX clone, activate would be fracture the mirror in smi-s.
     */
    @Override
    public void activateSingleClone(StorageSystem storageSystem, URI fullCopy, TaskCompleter completer) {
        log.info("START activateSingleClone for {}", fullCopy);
        try {
            Volume clone = _dbClient.queryObject(Volume.class, fullCopy);
            URI sourceUri = clone.getAssociatedSourceVolume();

            BlockObject sourceObj = BlockObject.fetch(_dbClient, sourceUri);
            StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceObj.getStorageController());
            CIMObjectPath syncObject = _cimPath.getStorageSynchronized(sourceSystem, sourceObj, storageSystem, clone);
            CIMInstance instance = _helper.checkExists(storageSystem, syncObject, false, false);
            if (instance != null) {
                fractureReplica(storageSystem, syncObject);
                clone.setSyncActive(true);
                clone.setRefreshRequired(true);
                clone.setReplicaState(ReplicationState.SYNCHRONIZED.name());
                _dbClient.persistObject(clone);
                if (completer != null) {
                    completer.ready(_dbClient);
                }
                log.info("FINISH activateSingleClone for {}", fullCopy);
            } else {
                String errorMsg = "The clone is already detached. active will not be performed.";
                log.info(errorMsg);
                ServiceError error = DeviceControllerErrors.smis.methodFailed("activateSingleClone", errorMsg);
                if (completer != null) {
                    completer.error(_dbClient, error);
                }
            }

        } catch (Exception e) {
            String errorMsg = String.format(ACTIVATE_ERROR_MSG_FORMAT, fullCopy);
            log.error(errorMsg, e);
            if (completer != null) {
                completer.error(_dbClient, DeviceControllerException.exceptions.activateVolumeFullCopyFailed(e));
            }
        }
        log.info("activateSingleClone operation END");
    }

    /**
     * This interface is for the clone activate in CG, for vnx, it is to Split (Consistent Fracture) the mirror.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param clones [required] - clone URIs
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void activateGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter completer) {
        log.info("activateGroupClones operation START");
        try {
            modifyGroupClones(storage, clones, SmisConstants.SPLIT_VALUE);
            List<Volume> cloneVols = _dbClient.queryObject(Volume.class, clones);
            for (Volume clone : cloneVols) {
                clone.setSyncActive(true);
                clone.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            }
            _dbClient.persistObject(cloneVols);
            if (completer != null) {
                completer.ready(_dbClient);
            }
        } catch (Exception e) {
            log.error(MODIFY_GROUP_ERROR, e);
            completer.error(_dbClient, DeviceControllerException.exceptions.activateVolumeFullCopyFailed(e));
        }
        log.info("activateGroupClones operation END");

    }

    @Override
    public void fractureGroupClones(StorageSystem storageSystem, List<URI> clones,
            TaskCompleter taskCompleter) {
        log.info("START fractureGroupClone operation");
        try {
            if (_helper.groupHasReplicasInSplitState(storageSystem, clones, Volume.class)) {
                log.info("Resync group clones with mixed states");
                modifyGroupClones(storageSystem, clones, SmisConstants.RESYNC_VALUE);
            }

            modifyGroupClones(storageSystem, clones, SmisConstants.SPLIT_VALUE);
            List<Volume> cloneVols = _dbClient.queryObject(Volume.class, clones);
            for (Volume clone : cloneVols) {
                clone.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            }
            _dbClient.persistObject(cloneVols);
            if (taskCompleter != null) {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            log.error(MODIFY_GROUP_ERROR, e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions.fractureFullCopyFailed(e));
        }
        log.info("fractureGroupClone operation END");

    }

    @Override
    public void detachGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter completer) {
        log.info("START detachGroupClone operation");
        try {
            modifyGroupClones(storage, clones, SmisConstants.DETACH_VALUE);
            List<Volume> cloneVols = _dbClient.queryObject(Volume.class, clones);
            for (Volume clone : cloneVols) {
                ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(clone, _dbClient);
                clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                clone.setReplicaState(ReplicationState.DETACHED.name());
            }
            _dbClient.persistObject(cloneVols);
            if (completer != null) {
                completer.ready(_dbClient);
            }
        } catch (Exception e) {
            log.error(MODIFY_GROUP_ERROR, e);
            completer.error(_dbClient, DeviceControllerException.exceptions.detachVolumeFullCopyFailed(e));
        }
        log.info("detachGroupClone operation END");

    }

    @Override
    public void restoreGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter completer) {
        log.info("START restoreGroupClone operation");
        try {
            modifyGroupClones(storage, clones, SmisConstants.RESTORE_FROM_REPLICA);
            completer.ready(_dbClient);
        } catch (Exception e) {
            log.error(MODIFY_GROUP_ERROR, e);
            completer.error(_dbClient, DeviceControllerException.exceptions.restoreVolumeFromFullCopyFailed(e));
        }
        log.info("restoreGroupClone operation END");

    }

    @Override
    public void resyncGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter completer) {
        log.info("START resyncGroupClone operation");
        try {
            modifyGroupClones(storage, clones, SmisConstants.RESYNC_VALUE);
            completer.ready(_dbClient);
        } catch (Exception e) {
            log.error(MODIFY_GROUP_ERROR, e);
            completer.error(_dbClient, DeviceControllerException.exceptions.resynchronizeFullCopyFailed(e));
        }
        log.info("resyncGroupClone operation END");

    }

    /**
     * It would call modifyListSynchronization for synchronized operations, e.g. fracture, detach, etc
     * 
     * @param storageSystem
     * @param clones
     * @param operationValue
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    private void modifyGroupClones(StorageSystem storageSystem, List<URI> clones, int operationValue)
            throws Exception {

        callEMCRefreshIfRequired(_dbClient, _helper, storageSystem, clones);
        List<Volume> cloneVols = _dbClient.queryObject(Volume.class, clones);
        List<CIMObjectPath> syncPaths = new ArrayList<CIMObjectPath>();

        for (Volume clone : cloneVols) {
            URI sourceUri = clone.getAssociatedSourceVolume();
            Volume sourceObj = _dbClient.queryObject(Volume.class, sourceUri);
            CIMObjectPath syncObject = _cimPath.getStorageSynchronized(storageSystem, sourceObj, storageSystem, clone);
            CIMInstance instance = _helper.checkExists(storageSystem, syncObject, false, false);
            if (instance != null) {
                syncPaths.add(syncObject);
            } else {
                log.error("Storage synchronized instance is not available for clone {}", clone.getLabel());
                throw DeviceControllerException.exceptions.synchronizationInstanceNull(clone.getLabel());
            }
        }

        CIMArgument[] modifyCGCloneInput = _helper.getModifyListReplicaInputArguments(
                syncPaths.toArray(new CIMObjectPath[] {}),
                operationValue);
        _helper.callModifyListReplica(storageSystem, modifyCGCloneInput);
    }

}
