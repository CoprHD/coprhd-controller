/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefreshIfRequired;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.COPY_BEFORE_ACTIVATE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.COPY_STATE_RESTORED_INT_VALUE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CREATE_NEW_TARGET_VALUE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.DIFFERENTIAL_CLONE_VALUE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.GET_DEFAULT_REPLICATION_SETTING_DATA;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.PROVISIONING_TARGET_SAME_AS_SOURCE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.SMIS810_TF_DIFFERENTIAL_CLONE_VALUE;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.TARGET_ELEMENT_SUPPLIER;
import static javax.cim.CIMDataType.UINT16_T;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockResyncSnapshotJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCloneRestoreJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCloneResyncJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCloneVolumeJob;

public class AbstractCloneOperations implements CloneOperations {
    private static final Logger _log = LoggerFactory.getLogger(AbstractCloneOperations.class);

    private static final String CREATE_ERROR_MSG_FORMAT = "from %s to %s";
    protected static final String ACTIVATE_ERROR_MSG_FORMAT = "Failed to activate full copy %s";
    private static final String DETACH_ERROR_MSG_FORMAT = "Failed to detach full copy %s from source %s";
    private static final String RESYNC_ERROR_MSG_FORMAT = "Failed to resync full copy %s";
    private static final String FRACTURE_ERROR_MSG_FORMAT = "Failed to fracture full copy %s from source %s";

    protected DbClient _dbClient;
    protected SmisCommandHelper _helper;
    protected CIMObjectPathFactory _cimPath;
    protected NameGenerator _nameGenerator;

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSmisCommandHelper(SmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void createSingleClone(StorageSystem storageSystem, URI sourceVolume, URI cloneVolume,
            Boolean createInactive, TaskCompleter taskCompleter) {
        _log.info("START createSingleClone operation");
        try {
            BlockObject sourceObj = BlockObject.fetch(_dbClient, sourceVolume);
            URI tenantUri = null;
            Volume baseVolume = null;
            boolean isSourceSnap = false;
            if (sourceObj instanceof BlockSnapshot) {
                // In case of snapshot, get the tenant from its parent volume
                NamedURI parentVolUri = ((BlockSnapshot) sourceObj).getParent();
                Volume parentVolume = _dbClient.queryObject(Volume.class, parentVolUri);
                tenantUri = parentVolume.getTenant().getURI();
                baseVolume = parentVolume;
                isSourceSnap = true;
            } else {
                // This is a default flow
                tenantUri = ((Volume) sourceObj).getTenant().getURI();
                baseVolume = (Volume) sourceObj;
            }

            // CTRL-1992: Need to resync any existing snapshot restore sessions, if applicable
            if (_helper.arraySupportsResync(storageSystem)) {
                CloseableIterator<CIMObjectPath> syncObjectIter =
                        _cimPath.getSyncObjects(storageSystem, sourceObj);
                CIMObjectPath path = null;
                while (syncObjectIter.hasNext()) {
                    path = syncObjectIter.next();
                    CIMInstance instance =
                            _helper.getInstance(storageSystem, path, false, false,
                                    SmisConstants.PS_COPY_STATE_AND_DESC_SYNCTYPE);
                    String copyState =
                            instance.getPropertyValue(SmisConstants.CP_COPY_STATE).
                                    toString();
                    String copyStateDesc =
                            instance.getPropertyValue(SmisConstants.
                                    EMC_COPY_STATE_DESC).toString();
                    String syncType = instance.getPropertyValue(SmisConstants.CP_SYNC_TYPE).
                            toString();
                    _log.info(String.format("Sync %s has copyState %s (%s) syncType %s",
                            path.toString(), copyState, copyStateDesc, syncType));
                    if (copyState.equals(COPY_STATE_RESTORED_INT_VALUE) &&
                            syncType.equals(Integer.toString(SmisConstants.SNAPSHOT_VALUE))) {
                        // This snapshot is in the 'Restored' state, need to
                        // resync it, before we can create a full copy
                        _log.info("Sync {} is in restored state, need to resync",
                                path);
                        SmisBlockResyncSnapshotJob job =
                                new SmisBlockResyncSnapshotJob(null, storageSystem.getId(),
                                        new TaskCompleter() {
                                            @Override
                                            protected void
                                                    complete(DbClient dbClient,
                                                            Operation.Status status,
                                                            ServiceCoded coded) throws DeviceControllerException {

                                            }
                                        });
                        CIMArgument[] result = new CIMArgument[5];
                        _helper.invokeMethodSynchronously(storageSystem,
                                _cimPath.getControllerReplicationSvcPath(storageSystem),
                                SmisConstants.MODIFY_REPLICA_SYNCHRONIZATION,
                                _helper.getResyncSnapshotInputArguments(path),
                                result, job);
                        if (job.isSuccess()) {
                            _log.info("{} was successfully resynchronized", path.toString());
                        } else {
                            _log.error("Encountered a failure while trying to resynchronize a restored snapshot");
                            ServiceError error = DeviceControllerErrors.smis.
                                    resyncActiveRestoreSessionFailure(sourceObj.getLabel());
                            taskCompleter.error(_dbClient, error);
                            return;
                        }
                    }
                }
            }

            Volume cloneObj = _dbClient.queryObject(Volume.class, cloneVolume);
            StoragePool targetPool = _dbClient.queryObject(StoragePool.class, cloneObj.getPool());
            TenantOrg tenantOrg = _dbClient.queryObject(TenantOrg.class, tenantUri);
            String cloneLabel = generateLabel(tenantOrg, cloneObj);

            CIMObjectPath volumeGroupPath = _helper.getVolumeGroupPath(storageSystem, baseVolume, targetPool);
            CIMObjectPath sourceVolumePath = _cimPath.getBlockObjectPath(storageSystem, sourceObj);
            CIMObjectPath replicationSvcPath = _cimPath.getControllerReplicationSvcPath(storageSystem);
            CIMArgument[] inArgs = null;
            CIMInstance repSettingData = null;
            if (storageSystem.deviceIsType(Type.vmax)) {

                if (createInactive && storageSystem.getUsingSmis80()) {
                    repSettingData = _helper.getReplicationSettingDataInstanceForDesiredCopyMethod(storageSystem, COPY_BEFORE_ACTIVATE,
                            true);
                } else if (storageSystem.checkIfVmax3() && ControllerUtils.isVmaxUsing81SMIS(storageSystem, _dbClient)) {
                    /**
                     * VMAX3 using SMI 8.1 provider needs to send DesiredCopyMethodology=32770
                     * to create TimeFinder differential clone.
                     */
                    repSettingData = _helper.getReplicationSettingDataInstanceForDesiredCopyMethod(storageSystem,
                            SMIS810_TF_DIFFERENTIAL_CLONE_VALUE, true);
                } else {
                    repSettingData = _helper.getReplicationSettingDataInstanceForDesiredCopyMethod(storageSystem, DIFFERENTIAL_CLONE_VALUE,
                            true);
                }
                inArgs = _helper.getCloneInputArguments(cloneLabel, sourceVolumePath, volumeGroupPath, storageSystem,
                        targetPool, createInactive, repSettingData);
            } else if (storageSystem.deviceIsType(Type.vnxblock)) {
                if (!isSourceSnap) {
                    repSettingData = getReplicationSettingDataInstanceForThinProvisioningPolicy(storageSystem,
                            PROVISIONING_TARGET_SAME_AS_SOURCE);
                    // don't supply target pool when using thinlyProvisioningPolicy=PROVISIONING_TARGET_SAME_AS_SOURCE
                    inArgs = _helper.getCreateElementReplicaMirrorInputArgumentsWithReplicationSettingData(storageSystem, sourceObj, null,
                            false, repSettingData, cloneLabel);
                    cloneObj.setPool(baseVolume.getPool());
                    _dbClient.persistObject(cloneObj);
                } else {
                    // when source is snapshot, create clone instead of mirror, since creating mirror from a snap is not supported.
                    inArgs = _helper.getCloneInputArguments(cloneLabel, sourceVolumePath, volumeGroupPath, storageSystem,
                            targetPool, createInactive, null);
                }
            }
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storageSystem, replicationSvcPath, SmisConstants.CREATE_ELEMENT_REPLICA, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisCloneVolumeJob(job, storageSystem.getId(),
                        taskCompleter)));
            }
        } catch (Exception e) {
            Volume clone = _dbClient.queryObject(Volume.class, cloneVolume);
            if (clone != null) {
                clone.setInactive(true);
                _dbClient.persistObject(clone);
            }
            String errorMsg = String.format(CREATE_ERROR_MSG_FORMAT, sourceVolume, cloneVolume);
            _log.error(errorMsg, e);
            SmisException serviceCode = DeviceControllerExceptions.smis.createFullCopyFailure(errorMsg, e);
            taskCompleter.error(_dbClient, serviceCode);
            throw serviceCode;
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void detachSingleClone(StorageSystem storageSystem, URI cloneVolume,
            TaskCompleter taskCompleter) {
        _log.info("START detachSingleClone operation");
        Volume clone = null;
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storageSystem,
                    Arrays.asList(cloneVolume));
            clone = _dbClient.queryObject(Volume.class, cloneVolume);
            URI sourceUri = clone.getAssociatedSourceVolume();
            if (!NullColumnValueGetter.isNullURI(sourceUri)) {
                BlockObject sourceObj = BlockObject.fetch(_dbClient, sourceUri);
                if (sourceObj != null) {
                    StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceObj.getStorageController());
                    CIMObjectPath syncObject = _cimPath.getStorageSynchronized(sourceSystem, sourceObj, storageSystem, clone);
                    CIMInstance instance = _helper.checkExists(storageSystem, syncObject, false, false);
                    if (instance != null) {
                        CIMArgument[] inArgs = _helper.getDetachSynchronizationInputArguments(syncObject);
                        CIMArgument[] outArgs = new CIMArgument[5];
                        _helper.callModifyReplica(storageSystem, inArgs, outArgs);
                    } else {
                        _log.info("The clone is already detached. Detach will not be performed.");
                    }
                } else {
                    _log.info("The clone's source volume cannot be found in the database. Detach will not be performed.");
                }
            } else {
                _log.info("The clone does not have a source volume. Detach will not be performed.");
            }

            // Update sync active property
            /**
             * cq:609984 - No need to reset sync active flag as its caused problem
             * when check for activated target volume.
             * 
             * @see <code>BlockService#activateFullCopy
             * volume.setSyncActive(false);
             */
            ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(clone, _dbClient);
            clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
            clone.setReplicaState(ReplicationState.DETACHED.name());
            _dbClient.persistObject(clone);
            if (taskCompleter != null) {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            String errorMsg = String.format(DETACH_ERROR_MSG_FORMAT, cloneVolume, clone.getAssociatedSourceVolume());
            _log.error(errorMsg, e);
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, DeviceControllerException.exceptions.detachVolumeFullCopyFailed(e));
            }
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void activateSingleClone(StorageSystem storageSystem, URI fullCopy, TaskCompleter completer) {
        _log.info("START activateSingleClone for {}", fullCopy);
        try {
            Volume volume = _dbClient.queryObject(Volume.class, fullCopy);
            CIMArgument[] inArgs = _helper.getActivateFullCopyArguments(storageSystem, volume);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.callModifyReplica(storageSystem, inArgs, outArgs);

            // Update sync active property
            volume.setSyncActive(true);
            volume.setRefreshRequired(true);
            volume.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            _dbClient.persistObject(volume);
            completer.ready(_dbClient);
            _log.info("FINISH activateSingleClone for {}", fullCopy);
        } catch (Exception e) {
            String errorMsg = String.format(ACTIVATE_ERROR_MSG_FORMAT, fullCopy);
            _log.error(errorMsg, e);
            completer.error(_dbClient, DeviceControllerException.exceptions.activateVolumeFullCopyFailed(e));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void fractureSingleClone(StorageSystem storageSystem, URI source, URI cloneVolume,
            TaskCompleter taskCompleter) {
        _log.info("START fractureSingleClone operation");
        Volume clone = null;
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storageSystem,
                    Arrays.asList(cloneVolume));
            clone = _dbClient.queryObject(Volume.class, cloneVolume);
            URI sourceUri = clone.getAssociatedSourceVolume();
            if (!NullColumnValueGetter.isNullURI(sourceUri)) {
                BlockObject sourceObj = BlockObject.fetch(_dbClient, sourceUri);
                if (sourceObj != null) {
                    StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceObj.getStorageController());
                    CIMObjectPath syncObject = _cimPath.getStorageSynchronized(sourceSystem, sourceObj, storageSystem, clone);
                    CIMInstance instance = _helper.checkExists(storageSystem, syncObject, false, false);
                    if (instance != null) {
                        fractureReplica(storageSystem, syncObject);
                    } else {
                        String errorMsg = "The clone is already detached. fracture will not be performed.";
                        _log.info(errorMsg);
                        ServiceError error = DeviceControllerErrors.smis.methodFailed("fractureSingleClone", errorMsg);
                        taskCompleter.error(_dbClient, error);
                    }
                } else {
                    String errorMsg = "The clone's source volume cannot be found in the database. Fractrure will not be performed.";
                    _log.info(errorMsg);
                    ServiceError error = DeviceControllerErrors.smis.methodFailed("fractureSingleClone", errorMsg);
                    taskCompleter.error(_dbClient, error);
                }
            } else {
                String errorMsg = "The clone does not have a source volume. Fracture will not be performed.";
                _log.info(errorMsg);
                ServiceError error = DeviceControllerErrors.smis.methodFailed("fractureSingleClone", errorMsg);
                taskCompleter.error(_dbClient, error);
            }

            clone.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            _dbClient.persistObject(clone);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            String errorMsg = String.format(FRACTURE_ERROR_MSG_FORMAT, cloneVolume, clone.getAssociatedSourceVolume());
            _log.error(errorMsg, e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions.fractureFullCopyFailed(e));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void resyncSingleClone(StorageSystem storageSystem, URI cloneVolume,
            TaskCompleter taskCompleter) {
        _log.info("START resyncSingleClone operation");
        Volume clone = null;
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storageSystem,
                    Arrays.asList(cloneVolume));
            clone = _dbClient.queryObject(Volume.class, cloneVolume);
            URI sourceUri = clone.getAssociatedSourceVolume();
            Volume sourceObj = _dbClient.queryObject(Volume.class, sourceUri);
            StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceObj.getStorageController());
            CIMObjectPath syncObject = _cimPath.getStorageSynchronized(sourceSystem, sourceObj, storageSystem, clone);
            CIMInstance instance = _helper.checkExists(storageSystem, syncObject, false, false);
            if (instance != null) {
                CIMArgument[] inArgs = _helper.getResyncReplicaInputArguments(syncObject);
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.callModifyReplica(storageSystem, inArgs, outArgs);
                CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
                if (job != null) {
                    ControllerServiceImpl.enqueueJob(new QueueJob(new SmisCloneResyncJob(job, storageSystem.getId(),
                            taskCompleter)));
                }
            } else {
                String errorMsg = "The clone is already detached. resync will not be performed.";
                _log.info(errorMsg);
                ServiceError error = DeviceControllerErrors.smis.methodFailed("resyncSingleClone", errorMsg);
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            String errorMsg = String.format(RESYNC_ERROR_MSG_FORMAT, cloneVolume);
            _log.error(errorMsg, e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions.resynchronizeFullCopyFailed(e));
        }
    }

    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
            Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    protected String generateLabel(TenantOrg tenantOrg, Volume cloneObj) {
        return _nameGenerator.generate(tenantOrg.getLabel(), cloneObj.getLabel(), cloneObj.getId().toString(), '-',
                SmisConstants.MAX_VOLUME_NAME_LENGTH);
    }

    @SuppressWarnings("rawtypes")
    private CIMInstance getReplicationSettingDataInstanceForThinProvisioningPolicy(final StorageSystem storageSystem, int desiredValue) {
        CIMInstance modifiedInstance = null;

        try {
            CIMObjectPath replicationSettingCapabilities = _cimPath
                    .getReplicationServiceCapabilitiesPath(storageSystem);
            CIMArgument[] inArgs = _helper.getReplicationSettingDataInstance();
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storageSystem, replicationSettingCapabilities,
                    GET_DEFAULT_REPLICATION_SETTING_DATA, inArgs, outArgs);
            for (CIMArgument<?> outArg : outArgs) {
                if (null == outArg) {
                    continue;
                }
                if (outArg.getName().equalsIgnoreCase(SmisConstants.DEFAULT_INSTANCE)) {
                    CIMInstance repInstance = (CIMInstance) outArg.getValue();
                    if (null != repInstance) {
                        CIMProperty<?> thinProvisioningPolicy = new CIMProperty<Object>(SmisConstants.THIN_PROVISIONING_POLICY, UINT16_T,
                                new UnsignedInteger16(desiredValue));
                        CIMProperty<?> targetElementSupplier = new CIMProperty<Object>(TARGET_ELEMENT_SUPPLIER,
                                UINT16_T, new UnsignedInteger16(CREATE_NEW_TARGET_VALUE));
                        CIMProperty<?>[] propArray = new CIMProperty<?>[] { thinProvisioningPolicy, targetElementSupplier };
                        modifiedInstance = repInstance.deriveInstance(propArray);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Error retrieving Replication Setting Data Instance ", e);
        }
        return modifiedInstance;
    }

    @Override
    public void activateGroupClones(StorageSystem storage, List<URI> clone, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void fractureGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void detachGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void establishVolumeCloneGroupRelation(StorageSystem storage, URI sourceVolume, URI clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * Implementation for restoring of a single volume clone restore.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param volume [required] - Volume URI for the volume to be restored
     * @param clone [required] - URI representing the previously created clone
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void restoreFromSingleClone(StorageSystem storage, URI clone, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("START restore Single clone operation");
        try {
            callEMCRefreshIfRequired(_dbClient, _helper, storage, Arrays.asList(clone));
            Volume cloneVol = _dbClient.queryObject(Volume.class, clone);
            Volume originalVol = _dbClient.queryObject(Volume.class, cloneVol.getAssociatedSourceVolume());
            CIMObjectPath syncObjectPath = _cimPath.getStorageSynchronized(storage, originalVol, storage, cloneVol);
            if (_helper.checkExists(storage, syncObjectPath, false, false) != null) {
                CIMArgument[] outArgs = new CIMArgument[5];
                CIMArgument[] inArgs = _helper.getRestoreFromReplicaInputArgumentsWithForce(syncObjectPath);
                _helper.callModifyReplica(storage, inArgs, outArgs);
                CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
                if (job != null) {
                    ControllerServiceImpl.enqueueJob(new QueueJob(new SmisCloneRestoreJob(job,
                            storage.getId(), taskCompleter)));
                }

            } else {
                ServiceError error = DeviceControllerErrors.smis.unableToFindSynchPath(storage.getLabel());
                taskCompleter.error(_dbClient, error);
            }
        } catch (WBEMException e) {
            String message = String.format("Error encountered when trying to restore from clone %s on array %s",
                    clone.toString(), storage.getSerialNumber());
            _log.error(message, e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, error);
        } catch (Exception e) {
            String message = String.format("Generic exception when trying to restore from clone %s on array %s",
                    clone.toString(), storage.getSerialNumber());
            _log.error(message, e);
            taskCompleter.error(_dbClient, DeviceControllerException.exceptions.restoreVolumeFromFullCopyFailed(e));
        }
    }

    @Override
    public void restoreGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void resyncGroupClones(StorageSystem storage, List<URI> clones, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    protected void fractureReplica(StorageSystem storageSystem, CIMObjectPath syncObject) throws WBEMException {
        CIMArgument[] inArgs = _helper.getFractureMirrorInputArgumentsWithCopyState(syncObject, null);
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.callModifyReplica(storageSystem, inArgs, outArgs);
    }

}
