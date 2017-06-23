/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.JOB_COMPLETED_NO_ERROR;

import java.net.URI;
import java.util.List;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import com.emc.storageos.db.client.model.SynchronizationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockCreateMirrorJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockDeleteCGMirrorJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockDeleteMirrorJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockResumeMirrorJob;

/**
 * A class to provide common, array-independent mirror implementations
 */
public abstract class AbstractMirrorOperations implements MirrorOperations {
    private static final Logger _log = LoggerFactory.getLogger(AbstractMirrorOperations.class);
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
    public void createSingleVolumeMirror(StorageSystem storage, URI mirror, Boolean createInactive,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("createSingleVolumeMirror operation START");
        try {
            BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
            StoragePool targetPool = _dbClient.queryObject(StoragePool.class, mirrorObj.getPool());
            Volume source = _dbClient.queryObject(Volume.class, mirrorObj.getSource());
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, source.getTenant().getURI());
            String tenantName = tenant.getLabel();
            String targetLabelToUse = _nameGenerator.generate(tenantName, mirrorObj.getLabel(), mirror.toString(),
                    '-', SmisConstants.MAX_VOLUME_NAME_LENGTH);
            CIMObjectPath replicationSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
            CIMArgument[] inArgs = null;
            if (storage.checkIfVmax3()) {
                CIMObjectPath volumeGroupPath = _helper.getVolumeGroupPath(storage, storage, source, targetPool);
                CIMInstance replicaSettingData = getDefaultReplicationSettingData(storage);
                inArgs = _helper.getCreateElementReplicaMirrorInputArguments(storage, source, targetPool,
                        createInactive, targetLabelToUse, volumeGroupPath, replicaSettingData);
            }
            else {
                inArgs = _helper.getCreateElementReplicaMirrorInputArguments(storage, source, targetPool,
                        createInactive, targetLabelToUse);
            }

            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, replicationSvcPath, SmisConstants.CREATE_ELEMENT_REPLICA, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisBlockCreateMirrorJob(job,
                        storage.getId(), !createInactive, taskCompleter)));
                // Resynchronizing state applies to the initial copy as well as future
                // re-synchronization's.
                mirrorObj.setSyncState(SynchronizationState.RESYNCHRONIZING.toString());
                _dbClient.persistObject(mirrorObj);
            }
        } catch (final InternalException e) {
            _log.info("Problem making SMI-S call: ", e);
            taskCompleter.error(_dbClient, e);
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError serviceError = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    @Override
    public void fractureSingleVolumeMirror(StorageSystem storage, URI mirror, Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("fractureSingleVolumeMirror operation START");
        CloseableIterator<CIMObjectPath> storageSyncRefs = null;
        try {
            BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
            CIMObjectPath mirrorPath = _cimPath.getBlockObjectPath(storage, mirrorObj);
            // Get reference to the CIM_StorageSynchronized instance
            storageSyncRefs = _helper.getReference(storage, mirrorPath, SmisConstants.CIM_STORAGE_SYNCHRONIZED, null);
            boolean isVmax3 = storage.checkIfVmax3();
            while (storageSyncRefs.hasNext()) {
                CIMObjectPath storageSync = storageSyncRefs.next();
                CIMArgument[] inArgs = isVmax3 ? _helper.getFractureMirrorInputArgumentsWithCopyState(storageSync, sync)
                        : _helper.getFractureMirrorInputArguments(storageSync, sync);
                CIMArgument[] outArgs = new CIMArgument[5];
                // Invoke method to fracture the synchronization
                _helper.callModifyReplica(storage, inArgs, outArgs);
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            _log.info("Problem making SMI-S call", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        } finally {
            if (storageSyncRefs != null) {
                storageSyncRefs.close();
            }
        }
    }

    @Override
    public void resumeSingleVolumeMirror(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("resumeSingleVolumeMirror operation START");
        CloseableIterator<CIMObjectPath> storageSyncRefs = null;
        try {
            BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
            CIMObjectPath mirrorPath = _cimPath.getBlockObjectPath(storage, mirrorObj);
            // Get reference to the CIM_StorageSynchronized instance
            storageSyncRefs = _helper.getReference(storage, mirrorPath, SmisConstants.CIM_STORAGE_SYNCHRONIZED, null);
            if (!storageSyncRefs.hasNext()) {
                _log.error("No synchronization instance found for {}", mirror);
                taskCompleter.error(_dbClient, DeviceControllerException.exceptions.resumeVolumeMirrorFailed(mirror));
                return;
            }
            boolean isVmax3 = storage.checkIfVmax3();
            while (storageSyncRefs.hasNext()) {
                CIMObjectPath storageSync = storageSyncRefs.next();
                _log.debug(storageSync.toString());
                /**
                 * JIRA CTRL-11855
                 * User created mirror and did pause operation using SMI 4.6.2.
                 * Then He upgraded to SMI 8.0.3. While doing mirror resume getting exception from SMI because of the
                 * existing mirrorObj.getSynchronizedInstance() contains SystemName=\"SYMMETRIX+000195701573\""
                 * This is wrong with 8.0.3 as SystemName=\"SYMMETRIX-+-000195701573\"".
                 * To resolve this issue setting new value collected from current smis provider here.
                 * 
                 */
                mirrorObj.setSynchronizedInstance(storageSync.toString());
                _dbClient.persistObject(mirrorObj);
                CIMArgument[] inArgs = isVmax3 ? _helper.getResumeSynchronizationInputArgumentsWithCopyState(storageSync)
                        : _helper.getResumeSynchronizationInputArguments(storageSync);
                CIMArgument[] outArgs = new CIMArgument[5];
                _helper.callModifyReplica(storage, inArgs, outArgs);
                CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
                if (job != null) {
                    ControllerServiceImpl.enqueueJob(new QueueJob(new SmisBlockResumeMirrorJob(job,
                            storage.getId(), taskCompleter)));
                } else {
                    CIMInstance syncObject = _helper.getInstance(storage, storageSync, false, false,
                            new String[] { SmisConstants.CP_SYNC_STATE });
                    mirrorObj.setSyncState(CIMPropertyFactory.getPropertyValue(syncObject, SmisConstants.CP_SYNC_STATE));
                    _dbClient.persistObject(mirrorObj);
                    taskCompleter.ready(_dbClient);
                }

            }
        } catch (Exception e) {
            _log.error("Failed to resume single volume mirror: {}", mirror);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        } finally {
            if (storageSyncRefs != null) {
                storageSyncRefs.close();
            }
        }
    }

    @Override
    public void establishVolumeNativeContinuousCopyGroupRelation(StorageSystem storage, URI sourceVolume,
            URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void detachSingleVolumeMirror(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("detachSingleVolumeMirror operation START");
        try {
            BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
            CIMArgument[] inArgs = _helper.getDetachSynchronizationInputArguments(storage, mirrorObj);
            CIMArgument[] outArgs = new CIMArgument[5];

            // Invoke method to detach the local mirror
            UnsignedInteger32 result = (UnsignedInteger32) _helper.callModifyReplica(storage, inArgs, outArgs);
            if (JOB_COMPLETED_NO_ERROR.equals(result)) {
                taskCompleter.ready(_dbClient);
            } else {
                String msg = String.format("SMI-S call returned unsuccessfully: %s", result);
                taskCompleter.error(_dbClient, DeviceControllerException.errors.smis.jobFailed(msg));
            }
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    @Override
    public void deleteSingleVolumeMirror(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("deleteSingleVolumeMirror operation START");
        try {
            BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
            if (storage.checkIfVmax3()) {
                _helper.removeVolumeFromParkingSLOStorageGroup(storage, new String[] { mirrorObj.getNativeId() }, false);
                _log.info("Done invoking remove volume {} from parking SLO storage group", mirrorObj.getNativeId());
            }

            CIMObjectPath mirrorPath = _cimPath.getBlockObjectPath(storage, mirrorObj);
            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storage);
            CIMArgument[] inArgs = _helper.getDeleteMirrorInputArguments(storage, mirrorPath);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, configSvcPath, SmisConstants.RETURN_TO_STORAGE_POOL, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisBlockDeleteMirrorJob(job,
                        storage.getId(), taskCompleter)));
            }
        } catch (Exception e) {
            _log.info("Problem making SMI-S call: ", e);
            ServiceError serviceError = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    // For VMAX V3 only for now
    public CIMInstance getDefaultReplicationSettingData(StorageSystem storage) throws WBEMException {
        CIMInstance defaultInstance = null;
        CloseableIterator<CIMInstance> repSvcCapIter = null;
        try {
            repSvcCapIter =
                    _helper.getAssociatorInstances(storage,
                            _cimPath.getControllerReplicationSvcPath(storage), null,
                            _cimPath.prefixWithParamName(SmisConstants.REPLICATION_SERVICE_CAPABILTIES),
                            null, null, null);
            if (repSvcCapIter != null && repSvcCapIter.hasNext()) {
                CIMInstance instance = repSvcCapIter.next();
                CIMArgument[] in = _helper.getDefaultReplicationSettingDataInputArgumentsForLocalMirror();
                CIMArgument[] out = new CIMArgument[5];
                _helper.invokeMethod(storage, instance.getObjectPath(), SmisConstants.GET_DEFAULT_REPLICATION_SETTING_DATA, in, out);
                defaultInstance = (CIMInstance) _cimPath.getFromOutputArgs(out, SmisConstants.DEFAULT_INSTANCE);
            }
        } finally {
            if (repSvcCapIter != null) {
                repSvcCapIter.close();
            }
        }
        return defaultInstance;
    }

    @Override
    public void createGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean createInactive, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void fractureGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean sync, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void resumeGroupMirrors(StorageSystem storage, List<URI> mirrorList, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void detachGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean deleteGroup, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void deleteGroupMirrors(StorageSystem storage, List<URI> mirrorList, TaskCompleter taskCompleter)
        throws DeviceControllerException {
        _log.info("deleteGroupMirrors operation START");
        if (!((storage.getUsingSmis80() && storage.deviceIsType(Type.vmax)) || storage.deviceIsType(Type.vnxblock))) {
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }

        try {
            String[] deviceIds = null;
            BlockMirror firstMirror = _dbClient.queryObject(BlockMirror.class, mirrorList.get(0));
            String repGroupName = firstMirror.getReplicationGroupInstance();
            if (NullColumnValueGetter.isNotNullValue(repGroupName)) {
                CIMObjectPath repGroupPath = _cimPath.getReplicationGroupPath(storage, repGroupName);
                Set<String> deviceIdsSet = _helper.getVolumeDeviceIdsFromStorageGroup(storage, repGroupPath);
                deviceIds = deviceIdsSet.toArray(new String[deviceIdsSet.size()]);

                // Delete replication group
                ReplicationUtils.deleteReplicationGroup(storage, repGroupName, _dbClient, _helper, _cimPath);
                // Set mirrors replication group to null
                List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorList);
                for (BlockMirror mirror : mirrors) {
                    mirror.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    mirror.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                }

                _dbClient.persistObject(mirrors);
            } else {
                deviceIds = _helper.getBlockObjectNativeIds(mirrorList);
            }

            if (storage.checkIfVmax3()) {
                _helper.removeVolumeFromParkingSLOStorageGroup(storage, deviceIds, false);
                _log.info("Done invoking remove volumes from parking SLO storage group");
            }

            CIMObjectPath[] mirrorPaths = _cimPath.getVolumePaths(storage, deviceIds);
            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storage);
            CIMArgument[] inArgs = null;
            if (storage.deviceIsType(Type.vnxblock)) {
                inArgs = _helper.getReturnElementsToStoragePoolArguments(mirrorPaths);
            } else {
                inArgs = _helper.getReturnElementsToStoragePoolArguments(mirrorPaths, SmisConstants.CONTINUE_ON_NONEXISTENT_ELEMENT);
            }
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, configSvcPath, SmisConstants.RETURN_ELEMENTS_TO_STORAGE_POOL, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            ControllerServiceImpl.enqueueJob(new QueueJob(new SmisBlockDeleteCGMirrorJob(job,
                    storage.getId(), taskCompleter)));
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError serviceError = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    @Override
    public void removeMirrorFromDeviceMaskingGroup(StorageSystem system, List<URI> mirrorList,
            TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
