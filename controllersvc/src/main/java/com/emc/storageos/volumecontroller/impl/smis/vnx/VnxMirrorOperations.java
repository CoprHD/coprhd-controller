/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vnx;

import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefreshIfRequired;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.SynchronizationState;
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
import com.emc.storageos.volumecontroller.impl.smis.AbstractMirrorOperations;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisVnxCreateCGMirrorJob;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyGroupUtils;

/*
 * Modified based on VnxCloneOperations.java. For VNX, group clones and group mirrors are the same.
 */
public class VnxMirrorOperations extends AbstractMirrorOperations {
    private static final Logger _log = LoggerFactory.getLogger(VnxMirrorOperations.class);

    /**
     * Should implement create of a mirror from a source volume that is part of a
     * consistency group.
     *
     * Implementation note: In this method we will kick of the asynchronous creation
     * of the target devices required for the CG clones. Upon the successful
     * device creation, the post operations will take place, which will include the
     * creation of the target group and the group clone operation.
     *
     * @param storage [required] - StorageSystem object representing the array
     * @param mirrorList [required] - mirror URI list
     * @param createInactive whether the mirror needs to to be created with sync_active=true/false
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    @Override
    public void createGroupMirrors(StorageSystem storage, List<URI> mirrorList,
            Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("createGroupMirrors operation START");

        List<BlockMirror> mirrors = null;
        List<String> targetDeviceIds = null;
        try {
            mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
            BlockMirror firstMirror = mirrors.get(0);
            Volume sourceVolume = _dbClient.queryObject(Volume.class, firstMirror.getSource());
            String sourceGroupName = ConsistencyGroupUtils.getSourceConsistencyGroupName(sourceVolume, _dbClient);

            if (!ControllerUtils.isNotInRealVNXRG(sourceVolume, _dbClient)) {
                // CTRL-5640: ReplicationGroup may not be accessible after provider fail-over.
                ReplicationUtils.checkReplicationGroupAccessibleOrFail(storage, sourceVolume, _dbClient, _helper, _cimPath);
            }

            List<String> sourceIds = new ArrayList<String>();
            targetDeviceIds = new ArrayList<String>();
            Map<String, String> tgtToSrcMap = new HashMap<String, String>();
            for (BlockMirror mirror : mirrors) {
                final URI poolId = mirror.getPool();
                final Volume source = _dbClient.queryObject(Volume.class, mirror.getSource());
                sourceIds.add(source.getNativeId());
                // Create target devices
                final List<String> newDeviceIds = ReplicationUtils.createTargetDevices(storage, sourceGroupName, mirror.getLabel(),
                        createInactive,
                        1, poolId, mirror.getCapacity(), source.getThinlyProvisioned(), null, taskCompleter, _dbClient, _helper, _cimPath);
                targetDeviceIds.addAll(newDeviceIds);
                tgtToSrcMap.put(newDeviceIds.get(0), source.getNativeId());
            }

            CIMObjectPath[] targetDevicePaths = _cimPath.getVolumePaths(storage,
                    targetDeviceIds.toArray(new String[targetDeviceIds.size()]));
            CIMObjectPath[] sourceDevicePaths = _cimPath.getVolumePaths(storage, sourceIds.toArray(new String[sourceIds.size()]));

            // Create list replica
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] inArgs = _helper.getCreateListReplicaInputArguments(storage, sourceDevicePaths, targetDevicePaths);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, replicationSvc, SmisConstants.CREATE_LIST_REPLICA, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            ControllerServiceImpl.enqueueJob(
                    new QueueJob(new SmisVnxCreateCGMirrorJob(job,
                            storage.getId(), tgtToSrcMap, taskCompleter)));

            for (BlockMirror mirror : mirrors) {
                mirror.setSyncState(SynchronizationState.SYNCHRONIZED.name());
            }

            _dbClient.persistObject(mirrors);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            // Roll back changes
            ReplicationUtils.rollbackCreateReplica(storage, null, targetDeviceIds, taskCompleter, _dbClient, _helper, _cimPath);
            if (mirrors != null && !mirrors.isEmpty()) {
                for (BlockMirror mirror : mirrors) {
                    mirror.setInactive(true);
                }
            }

            _dbClient.persistObject(mirrors);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createGroupMirrors", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("createGroupMirrors operation END");
    }

    @Override
    public void fractureGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean sync,
            TaskCompleter taskCompleter) {
        _log.info("fractureGroupMirrors operation START");

        try {
            // Check if all the mirrors in the group are in consistent state.
            // If there is a mix of mirrors in Sync and Split states, resume the group.
            if (_helper.groupHasReplicasInSplitState(storage, mirrorList, BlockMirror.class)) {
                resumeGroupMirrors(storage, mirrorList);
            }

            int operation = (sync != null && sync) ? SmisConstants.SPLIT_VALUE : SmisConstants.FRACTURE_VALUE;
            int copyState = (operation == SmisConstants.SPLIT_VALUE) ? SmisConstants.SPLIT : SmisConstants.FRACTURED;
            modifyGroupMirrors(storage, mirrorList, operation, copyState);
            List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
            for (BlockMirror mirror : mirrors) {
                mirror.setSyncState(SynchronizationState.FRACTURED.name());
            }

            _dbClient.persistObject(mirrors);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, error);
        }

        _log.info("fractureGroupMirrors operation END");
    }

    private void resumeGroupMirrors(StorageSystem storage, List<URI> mirrorList) throws Exception {
        modifyGroupMirrors(storage, mirrorList, SmisConstants.RESYNC_VALUE, SmisConstants.SYNCHRONIZED);
        List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
        for (BlockMirror mirror : mirrors) {
            mirror.setSyncState(SynchronizationState.SYNCHRONIZED.name());
        }

        _dbClient.persistObject(mirrors);
    }

    @Override
    public void resumeGroupMirrors(StorageSystem storage, List<URI> mirrorList, TaskCompleter taskCompleter) {
        _log.info("START resumeGroupMirrors operation");

        try {
            resumeGroupMirrors(storage, mirrorList);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, error);
        }

        _log.info("resumeGroupMirrors operation END");
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.impl.smis.AbstractMirrorOperations#detachGroupMirrors(com.emc.storageos.db.client.model.
     * StorageSystem, java.util.List, java.lang.Boolean, com.emc.storageos.volumecontroller.TaskCompleter)
     * deleteGroup is not used for VNX
     */
    @Override
    public void detachGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean deleteGroup, TaskCompleter taskCompleter) {
        _log.info("START detachGroupMirrors operation");

        try {
            modifyGroupMirrors(storage, mirrorList, SmisConstants.DETACH_VALUE, SmisConstants.NON_COPY_STATE);
            List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
            for (BlockMirror mirror : mirrors) {
                mirror.setReplicaState(ReplicationState.DETACHED.name());
                mirror.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                mirror.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                mirror.setSyncState(NullColumnValueGetter.getNullStr());
            }
            _dbClient.persistObject(mirrors);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, error);
        }

        _log.info("detachGroupMirrors operation END");
    }

    /**
     * Invoke modifyListSynchronization for synchronized operations, e.g. fracture, detach, etc
     *
     * @param storageSystem
     * @param mirrorList
     * @param operation
     * @param copyState
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    private void modifyGroupMirrors(StorageSystem storageSystem, List<URI> mirrorList, int operation, int copyState)
            throws Exception {
        callEMCRefreshIfRequired(_dbClient, _helper, storageSystem, mirrorList);
        List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
        List<CIMObjectPath> syncPaths = new ArrayList<CIMObjectPath>();
        for (BlockMirror mirror : mirrors) {
            Volume source = _dbClient.queryObject(Volume.class, mirror.getSource());
            CIMObjectPath syncObject = _cimPath.getStorageSynchronized(storageSystem, source, storageSystem, mirror);
            CIMInstance instance = _helper.checkExists(storageSystem, syncObject, false, false);
            if (instance != null) {
                syncPaths.add(syncObject);
            } else {
                _log.error("Storage synchronized instance is not available for mirror {}", mirror.getLabel());
                throw DeviceControllerException.exceptions.synchronizationInstanceNull(mirror.getLabel());
            }
        }

        CIMArgument[] modifyCGMirrorInput = _helper.getModifyListReplicaInputArguments(syncPaths.toArray(new CIMObjectPath[] {}), operation,
                copyState);
        _helper.callModifyListReplica(storageSystem, modifyCGMirrorInput);
    }
}
