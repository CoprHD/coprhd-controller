/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vmax;

import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefreshIfRequired;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.JOB_COMPLETED_NO_ERROR;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.WBEMException;

import com.emc.storageos.db.client.model.SynchronizationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
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
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants.SYNC_TYPE;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockCreateCGMirrorJob;
import com.google.common.base.Joiner;

public class VmaxMirrorOperations extends AbstractMirrorOperations {
    private static final Logger _log = LoggerFactory.getLogger(VmaxMirrorOperations.class);

    @Override
    public void establishVolumeNativeContinuousCopyGroupRelation(StorageSystem storage, URI sourceVolume,
            URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("establishVolumeNativeContinuousCopyGroupRelation operation START");
        try {
            /**
             * get groupPath for source volume
             * get groupPath for mirror
             * get mirrors belonging to the same Replication Group
             * get Element synchronizations between volumes and mirrors
             * call CreateGroupReplicaFromElementSynchronizations
             */
            BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
            Volume volumeObj = _dbClient.queryObject(Volume.class, sourceVolume);
            CIMObjectPath srcRepSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
            String volumeGroupName = _helper.getConsistencyGroupName(volumeObj, storage);
            CIMObjectPath volumeGroupPath = _cimPath.getReplicationGroupPath(storage, volumeGroupName);
            CIMObjectPath mirrorGroupPath = _cimPath.getReplicationGroupPath(storage, mirrorObj.getReplicationGroupInstance());

            CIMObjectPath groupSynchronizedPath = _cimPath.getGroupSynchronized(volumeGroupPath, mirrorGroupPath);
            CIMInstance syncInstance = _helper.checkExists(storage, groupSynchronizedPath, false, false);
            if (syncInstance == null) {
                // List<Volume> volumes = ControllerUtils.getVolumesPartOfCG(sourceVolume.getConsistencyGroup(), _dbClient);
                // get all mirrors belonging to a Replication Group. There may be multiple mirrors available for a Volume
                List<BlockMirror> mirrors = ControllerUtils.
                        getMirrorsPartOfReplicationGroup(mirrorObj.getReplicationGroupInstance(), _dbClient);

                List<CIMObjectPath> elementSynchronizations = new ArrayList<CIMObjectPath>();
                for (BlockMirror mirrorObject : mirrors) {
                    Volume volume = _dbClient.queryObject(Volume.class, mirrorObject.getSource());
                    elementSynchronizations.add(_cimPath.getStorageSynchronized(storage, volume,
                        storage, mirrorObject));
                }

                _log.info("Creating Group synchronization between volume group and mirror group");
                CIMArgument[] inArgs = _helper.getCreateGroupReplicaFromElementSynchronizationsForSRDFInputArguments(volumeGroupPath,
                        mirrorGroupPath, elementSynchronizations);
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.invokeMethod(storage, srcRepSvcPath,
                        SmisConstants.CREATE_GROUP_REPLICA_FROM_ELEMENT_SYNCHRONIZATIONS, inArgs, outArgs);
                // No Job returned
            } else {
                _log.info("Link already established..");
            }

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error(
                    "Failed to establish group relation between volume group and mirror group. Volume: {}, Mirror: {}",
                    sourceVolume, mirror);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    @Override
    public void createGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean createInactive,
                                         TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("createGroupMirrors operation START");
        List<BlockMirror> mirrors = null;
        try {
            if (!storage.getUsingSmis80()) {
                throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
            }

            mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
            BlockMirror firstMirror = mirrors.get(0);
            Volume sourceVolume = _dbClient.queryObject(Volume.class, firstMirror.getSource());
            String sourceGroupName = _helper.getConsistencyGroupName(sourceVolume, storage);
            String replicaLabel = ControllerUtils.generateLabel(sourceVolume.getLabel(), firstMirror.getLabel());
            // CTRL-5640: ReplicationGroup may not be accessible after provider fail-over.
            ReplicationUtils.checkReplicationGroupAccessibleOrFail(storage, sourceVolume, _dbClient, _helper, _cimPath);
            // Create CG mirrors
            CIMObjectPath job = VmaxGroupOperationsUtils.internalCreateGroupReplica(storage, sourceGroupName, replicaLabel, null, createInactive, taskCompleter,
                                            SYNC_TYPE.MIRROR, _dbClient, _helper, _cimPath);
            ControllerServiceImpl.enqueueJob(
                    new QueueJob(new SmisBlockCreateCGMirrorJob(job, storage.getId(), taskCompleter)));

            for (BlockMirror mirror : mirrors) {
                mirror.setSyncState(SynchronizationState.SYNCHRONIZED.name());
            }

            _dbClient.persistObject(mirrors);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            // Roll back changes
            if (mirrors != null && !mirrors.isEmpty()) {
                for (BlockMirror mirrorObj : mirrors) {
                    mirrorObj.setInactive(true);
                }
            }

            _dbClient.persistObject(mirrors);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createGroupMirrors", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("createGroupMirrors operation END");
    }

    @Override
    public void fractureGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("fractureGroupMirrors operation START");
        if (!storage.getUsingSmis80()) {
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }

        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage, mirrorList);
            CIMObjectPath groupSynchronized = ReplicationUtils.getMirrorGroupSynchronizedPath(storage, mirrorList.get(0),
                    _dbClient, _helper, _cimPath);
            if (null == _helper.checkExists(storage, groupSynchronized, false, false)) {
                _log.error("Unable to find group synchronized {}", groupSynchronized.toString());
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrorList.get(0));
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(mirror.getReplicationGroupInstance());
                taskCompleter.error(_dbClient, error);
            }
            // If there is any mirror in split state, resume the group to make it consistent.
            if (_helper.groupHasReplicasInSplitState(storage, mirrorList, BlockMirror.class)) {
                resumeGroupMirrors(storage, mirrorList);
            }
            // Fracture the mirror now.
            CIMArgument[] fractureCGMirrorInput = _helper.getFractureMirrorInputArgumentsWithCopyState(
                    groupSynchronized, sync);
            _helper.callModifyReplica(storage, fractureCGMirrorInput);
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
    }

    @Override
    public void resumeGroupMirrors(StorageSystem storage, List<URI> mirrorList, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("resumeGroupMirrors operation START");
        if (!storage.getUsingSmis80()) {
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }

        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage, mirrorList);
            CIMObjectPath groupSynchronized = ReplicationUtils.getMirrorGroupSynchronizedPath(storage, mirrorList.get(0),
                                                _dbClient, _helper, _cimPath);
            if (null == _helper.checkExists(storage, groupSynchronized, false, false)) {
                _log.error("Unable to find group synchronized {}", groupSynchronized.toString());
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrorList.get(0));
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(mirror.getReplicationGroupInstance());
                taskCompleter.error(_dbClient, error);
                return;
            }
            resumeGroupMirrors(storage, mirrorList);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Utility to resume Group mirrors.
     * @param storage
     * @param mirrorList
     */
    private void resumeGroupMirrors(StorageSystem storage, List<URI> mirrorList) throws WBEMException {
        CIMObjectPath groupSynchronized = ReplicationUtils.getMirrorGroupSynchronizedPath(storage,
                mirrorList.get(0), _dbClient, _helper, _cimPath);
        CIMArgument[] resumeCGMirrorInput = _helper
                .getResumeSynchronizationInputArgumentsWithCopyState(groupSynchronized);
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.callModifyReplica(storage, resumeCGMirrorInput, outArgs);
        List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
        for (BlockMirror mirror : mirrors) {
            mirror.setSyncState(SynchronizationState.SYNCHRONIZED.name());
        }
        _dbClient.persistObject(mirrors);
    }

    @Override
    public void detachGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean deleteGroup, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("START detach group mirror operation");
        if (!storage.getUsingSmis80()) {
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }

        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage, mirrorList);
            CIMObjectPath groupSynchronized = ReplicationUtils.getMirrorGroupSynchronizedPath(storage, mirrorList.get(0), _dbClient,
                                                    _helper, _cimPath);
            if (_helper.checkExists(storage, groupSynchronized, false, false) != null) {
                CIMArgument[] detachCGMirrorInput = _helper.getDetachSynchronizationInputArguments(groupSynchronized);
                // Invoke method to detach local mirrors
                UnsignedInteger32 result = (UnsignedInteger32) _helper.callModifyReplica(storage, detachCGMirrorInput, new CIMArgument[5]);
                if (JOB_COMPLETED_NO_ERROR.equals(result)) {
                    List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
                    if (deleteGroup) {
                        ReplicationUtils.deleteReplicationGroup(storage, mirrors.get(0).getReplicationGroupInstance(), _dbClient, _helper, _cimPath);
                    }

                    // Set mirrors replication group to null
                    for (BlockMirror mirror : mirrors) {
                        if (deleteGroup) {
                            mirror.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                            mirror.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                        }
                        mirror.setSyncState(NullColumnValueGetter.getNullStr());
                    }

                    _dbClient.persistObject(mirrors);
                    taskCompleter.ready(_dbClient);
                } else {
                    String msg = String.format("SMI-S call returned unsuccessfully: %s", result);
                    taskCompleter.error(_dbClient, DeviceControllerException.errors.smis.jobFailed(msg));
                }
            } else {
                _log.error("Unable to find group synchronized {}", groupSynchronized.toString());
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrorList.get(0));
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(mirror.getReplicationGroupInstance());
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void removeMirrorFromDeviceMaskingGroup(StorageSystem system, List<URI> mirrorList,
                                                   TaskCompleter completer) throws DeviceControllerException {
        _log.info("removeMirrorFromGroup operation START");
        try{
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrorList.get(0));
            CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(system, mirror.getReplicationGroupInstance(),
                    SmisConstants.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);

            List<URI> replicasPartOfGroup = _helper.findVolumesInReplicationGroup(system, maskingGroupPath, mirrorList);
            if (replicasPartOfGroup.isEmpty()) {
                _log.info("Mirrors {} already removed from Device Masking Group {}",
                        Joiner.on(", ").join(mirrorList), maskingGroupPath);
            } else {
                String[] members = _helper.getBlockObjectAlternateNames(replicasPartOfGroup);
                CIMObjectPath[] memberPaths = _cimPath.getVolumePaths(system, members);
                CIMArgument[] inArgs = _helper.getRemoveAndUnmapMaskingGroupMembersInputArguments(
                        maskingGroupPath, memberPaths, system, false);
                CIMArgument[] outArgs = new CIMArgument[5];

                _log.info("Invoking remove mirrors {} from Device Masking Group equivalent to its Replication Group {}",
                        Joiner.on(", ").join(members), mirror.getReplicationGroupInstance());
                _helper.invokeMethodSynchronously(system, _cimPath.getControllerConfigSvcPath(system),
                        SmisConstants.REMOVE_MEMBERS, inArgs, outArgs, null);
            }

            completer.ready(_dbClient);
        } catch (Exception e) {
            _log.error(
                    "Failed to remove mirrors from its Device Masking Group. Mirrors: {}",
                    Joiner.on(", ").join(mirrorList), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
        _log.info("removeMirrorFromGroup operation END");
    }
}
