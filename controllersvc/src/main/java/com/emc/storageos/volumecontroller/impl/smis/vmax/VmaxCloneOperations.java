/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vmax;

import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefreshIfRequired;
import static java.text.MessageFormat.format;

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

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
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
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants.SYNC_TYPE;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCloneRestoreJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCloneResyncJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateCGCloneJob;

public class VmaxCloneOperations extends AbstractCloneOperations {
    private static final Logger _log = LoggerFactory.getLogger(VmaxCloneOperations.class);

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
     * @param cloneList [required] - clone URI list
     * @param createInactive whether the clone needs to to be created with sync_active=true/false
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
            Boolean createInactive, TaskCompleter taskCompleter) {
        _log.info("START create group clone operation");
        // Target group CIM Path
        CIMObjectPath targetGroupPath = null;

        // List of target device ids
        List<String> targetDeviceIds = null;

        // The source consistency group name
        String sourceGroupName = null;

        try {
            final Volume first = _dbClient.queryObject(Volume.class, cloneList.get(0));
            Volume sourceVolume = _dbClient.queryObject(Volume.class, first.getAssociatedSourceVolume());
            sourceGroupName = _helper.getConsistencyGroupName(sourceVolume, storage);
            URI tenant = sourceVolume.getTenant().getURI();
            TenantOrg tenantOrg = _dbClient.queryObject(TenantOrg.class, tenant);
            String targetGroupLabel = generateLabel(tenantOrg, sourceVolume);
            // CTRL-5640: ReplicationGroup may not be accessible after provider fail-over.
            ReplicationUtils.checkReplicationGroupAccessibleOrFail(storage, sourceVolume, _dbClient, _helper, _cimPath);

            final Map<String, List<Volume>> clonesBySizeMap = new HashMap<String, List<Volume>>();

            List<Volume> clones = _dbClient.queryObject(Volume.class, cloneList);

            // For 8.0 providers, no need to create target devices and
            // target group separately for volumes in CG.
            // They will be created as part of 'CreateGroupReplica' call.
            // For 4.6 providers and VMAX3 arrays, target devices and target group will be
            // created separately before 'CreateGroupReplica' call.
            if (storage.checkIfVmax3() || !storage.getUsingSmis80()) {
                targetDeviceIds = new ArrayList<String>();
                for (Volume clone : clones) {

                    final URI poolId = clone.getPool();
                    Volume source = _dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
                    // Create target devices
                    final List<String> newDeviceIds = ReplicationUtils.createTargetDevices(storage, sourceGroupName,
                            clone.getLabel(),
                            createInactive,
                            1, poolId, clone.getCapacity(), source.getThinlyProvisioned(), source, taskCompleter, _dbClient, _helper,
                            _cimPath);

                    targetDeviceIds.addAll(newDeviceIds);
                }

                // Create target device group
                targetGroupPath = ReplicationUtils.createTargetDeviceGroup(storage, sourceGroupName, targetDeviceIds, taskCompleter,
                        _dbClient, _helper, _cimPath,
                        SYNC_TYPE.CLONE);
            }
            // Create CG clone
            CIMObjectPath job = VmaxGroupOperationsUtils.internalCreateGroupReplica(storage, sourceGroupName, targetGroupLabel,
                    targetGroupPath, createInactive, taskCompleter,
                    SYNC_TYPE.CLONE, _dbClient, _helper, _cimPath);

            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new SmisCreateCGCloneJob(job,
                                storage.getId(), !createInactive, taskCompleter)));
            }

        } catch (Exception e) {
            final String errMsg = format(
                    "An exception occurred when trying to create clones for consistency group {0} on storage system {1}",
                    sourceGroupName, storage.getId());
            _log.error(errMsg, e);

            // Roll back changes
            ReplicationUtils.rollbackCreateReplica(storage, targetGroupPath, targetDeviceIds, taskCompleter, _dbClient, _helper, _cimPath);
            List<Volume> clones = _dbClient.queryObject(Volume.class, cloneList);
            for (Volume clone : clones) {
                clone.setInactive(true);
            }
            _dbClient.persistObject(clones);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createGroupClones", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

    }

    /**
     * This interface is for the clone activate in CG.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param clones [required] - clone URIs
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    public void activateGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter taskCompleter) {
        try {
            _log.info("activateGroupClones operation START");
            Volume cloneObj = _dbClient.queryObject(Volume.class, clones.get(0));
            if (cloneObj.getSyncActive()) {
                _log.warn("Trying to activate CG clone, which is already active",
                        cloneObj.getId().toString());
                return;
            }
            Volume sourceVolume = _dbClient.queryObject(Volume.class, cloneObj.getAssociatedSourceVolume());
            boolean isSuccess = VmaxGroupOperationsUtils.activateGroupReplicas(storage, sourceVolume, cloneObj,
                    SYNC_TYPE.CLONE, taskCompleter, _dbClient, _helper, _cimPath);
            if (isSuccess) {
                List<Volume> cloneList = new ArrayList<Volume>();
                for (URI clone : clones) {
                    Volume theClone = _dbClient.queryObject(Volume.class, clone);
                    theClone.setSyncActive(true);
                    theClone.setRefreshRequired(true);
                    theClone.setReplicaState(ReplicationState.SYNCHRONIZED.name());
                    cloneList.add(theClone);
                }
                _dbClient.persistObject(cloneList);
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } finally {
            _log.info("activateGroupClones operation END");
        }
    }

    /**
     * Implementation for restoring of clones in CG.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param clones [required] - URIs representing the previously created clones
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void restoreGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("START restore group clone operation");
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage, clones);
            Volume clone = _dbClient.queryObject(Volume.class, clones.get(0));
            Volume sourceVol = _dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
            String consistencyGroupName = _helper.getConsistencyGroupName(sourceVol, storage);
            String replicationGroupName = clone.getReplicationGroupInstance();
            CIMObjectPath groupSynchronized = _cimPath.getGroupSynchronizedPath(storage, consistencyGroupName, replicationGroupName);
            if (_helper.checkExists(storage, groupSynchronized, false, false) != null) {
                CIMObjectPath cimJob = null;
                CIMArgument[] restoreCGCloneInput = _helper.getRestoreFromReplicaInputArgumentsWithForce(groupSynchronized);
                cimJob = _helper.callModifyReplica(storage, restoreCGCloneInput);

                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisCloneRestoreJob(cimJob, storage.getId(), taskCompleter)));
            } else {
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(consistencyGroupName);
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            String message =
                    String.format("Exception when trying to restoring clones from consistency group on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("restoreGroupClones", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * Implementation for resync clones in CG.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param clones [required] - URIs representing the previously created clones
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void resyncGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("START resync group clone operation");
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage, clones);
            Volume clone = _dbClient.queryObject(Volume.class, clones.get(0));
            Volume sourceVol = _dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
            String consistencyGroupName = _helper.getConsistencyGroupName(sourceVol, storage);
            String replicationGroupName = clone.getReplicationGroupInstance();
            CIMObjectPath groupSynchronized = _cimPath.getGroupSynchronizedPath(storage, consistencyGroupName, replicationGroupName);
            if (_helper.checkExists(storage, groupSynchronized, false, false) != null) {
                CIMObjectPath cimJob = null;
                CIMArgument[] resyncCGCloneInput = _helper.getResyncReplicaInputArguments(groupSynchronized);
                cimJob = _helper.callModifyReplica(storage, resyncCGCloneInput);

                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisCloneResyncJob(cimJob, storage.getId(), taskCompleter)));
            } else {
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(consistencyGroupName);
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            String message =
                    String.format("Exception when trying to resync clones from consistency group on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions.resynchronizeFullCopyFailed(e));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void fractureGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter completer) {
        _log.info("START fracture group clone operation");
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage, clones);
            CIMObjectPath groupSynchronized = ReplicationUtils.getCloneGroupSynchronizedPath(storage, clones.get(0),
                    _dbClient, _helper, _cimPath);
            if (_helper.checkExists(storage, groupSynchronized, false, false) != null) {
                if (_helper.groupHasReplicasInSplitState(storage, clones, Volume.class)) {
                    SmisCloneResyncJob job = new SmisCloneResyncJob(null, storage.getId(), completer);
                    CIMArgument[] resyncCGCloneInput = _helper.getResyncReplicaInputArguments(groupSynchronized);
                    _log.info("Resync group clones with mixed states");
                    _helper.callModifyReplicaSynchronously(storage, resyncCGCloneInput, job);
                }

                CIMArgument[] fractureCGCloneInput = _helper.getFractureMirrorInputArguments(groupSynchronized, null);
                _helper.callModifyReplica(storage, fractureCGCloneInput);
                List<Volume> cloneVolumes = _dbClient.queryObject(Volume.class, clones);
                for (Volume theClone : cloneVolumes) {
                    theClone.setReplicaState(ReplicationState.SYNCHRONIZED.name());
                }
                _dbClient.persistObject(cloneVolumes);
                completer.ready(_dbClient);
            } else {
                Volume clone = _dbClient.queryObject(Volume.class, clones.get(0));
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(clone.getReplicationGroupInstance());
                completer.error(_dbClient, error);
            }
        } catch (Exception e) {
            String message =
                    String.format("Exception when trying to fracture clones from consistency group on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            completer.error(_dbClient, DeviceControllerException.exceptions.fractureFullCopyFailed(e));
        }

    }

    @SuppressWarnings("rawtypes")
    @Override
    public void detachGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter completer) {
        _log.info("START detach group clone operation");
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage, clones);
            CIMObjectPath groupSynchronized = ReplicationUtils.getCloneGroupSynchronizedPath(storage, clones.get(0), _dbClient,
                    _helper, _cimPath);
            if (_helper.checkExists(storage, groupSynchronized, false, false) != null) {
                CIMArgument[] detachCGCloneInput = _helper.getDetachSynchronizationInputArguments(groupSynchronized);
                _helper.callModifyReplica(storage, detachCGCloneInput);
                List<Volume> cloneVolumes = _dbClient.queryObject(Volume.class, clones);
                for (Volume theClone : cloneVolumes) {
                    ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(theClone, _dbClient);
                    theClone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                    theClone.setReplicaState(ReplicationState.DETACHED.name());
                }
                _dbClient.persistObject(cloneVolumes);
                if (completer != null) {
                    completer.ready(_dbClient);
                }
            } else {
                Volume clone = _dbClient.queryObject(Volume.class, clones.get(0));
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(clone.getReplicationGroupInstance());
                if (completer != null) {
                    completer.error(_dbClient, error);
                }
            }
        } catch (Exception e) {
            String message =
                    String.format("Exception when trying to detach clones from consistency group on array %s",
                            storage.getSerialNumber());
            _log.error(message, e);
            completer.error(_dbClient, DeviceControllerException.exceptions.detachVolumeFullCopyFailed(e));
        }

    }

    @Override
    public void establishVolumeCloneGroupRelation(StorageSystem storage, URI sourceVolume, URI clone, TaskCompleter completer) {
        _log.info("establishVolumeCloneGroupRelation operation START");
        try {
            /**
             * get groupPath for source volume
             * get groupPath for clone
             * get clones belonging to the same Replication Group
             * get Element synchronizations between volumes and clones
             * call CreateGroupReplicaFromElementSynchronizations
             */
            Volume cloneObj = _dbClient.queryObject(Volume.class, clone);
            Volume volumeObj = _dbClient.queryObject(Volume.class, sourceVolume);
            CIMObjectPath srcRepSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
            String volumeGroupName = _helper.getConsistencyGroupName(volumeObj, storage);
            CIMObjectPath volumeGroupPath = _cimPath.getReplicationGroupPath(storage, volumeGroupName);
            CIMObjectPath cloneGroupPath = _cimPath.getReplicationGroupPath(storage, cloneObj.getReplicationGroupInstance());

            CIMObjectPath groupSynchronizedPath = _cimPath.getGroupSynchronized(volumeGroupPath, cloneGroupPath);
            CIMInstance syncInstance = _helper.checkExists(storage, groupSynchronizedPath, false, false);
            if (syncInstance == null) {
                // get all clones belonging to a Replication Group. There may be multiple clones available for a Volume
                List<Volume> fullCopies = ControllerUtils.
                        getFullCopiesPartOfReplicationGroup(cloneObj.getReplicationGroupInstance(), _dbClient);

                List<CIMObjectPath> elementSynchronizations = new ArrayList<CIMObjectPath>();
                for (Volume fullCopy : fullCopies) {
                    URI sourceVolumeURI = fullCopy.getAssociatedSourceVolume();
                    if (!NullColumnValueGetter.isNullURI(sourceVolumeURI)) {
                        Volume volume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
                        elementSynchronizations.add(_cimPath.getStorageSynchronized(storage, volume,
                                storage, fullCopy));
                    }
                }

                _log.info("Creating Group synchronization between volume group and clone group");
                CIMArgument[] inArgs = _helper.getCreateGroupReplicaFromElementSynchronizationsForSRDFInputArguments(volumeGroupPath,
                        cloneGroupPath, elementSynchronizations);
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.invokeMethod(storage, srcRepSvcPath,
                        SmisConstants.CREATE_GROUP_REPLICA_FROM_ELEMENT_SYNCHRONIZATIONS, inArgs, outArgs);
                // No Job returned
            } else {
                _log.info("Link already established..");
            }

            completer.ready(_dbClient);
        } catch (Exception e) {
            _log.error(
                    "Failed to establish group relation between volume group and clone group. Volume: {}, Clone: {}",
                    sourceVolume, clone);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
    }

}
