/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation All Rights Reserved This software contains the
 * intellectual property of EMC Corporation or is licensed to EMC Corporation from third parties.
 * Use of this software and the intellectual property contained therein is expressly limited to the
 * terms and conditions of the License Agreement under which it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.net.URI;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockCreateMirrorJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockDeleteMirrorJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockResumeMirrorJob;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.JOB_COMPLETED_NO_ERROR;

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
                CIMObjectPath volumeGroupPath = _helper.getVolumeGroupPath(storage, source, targetPool);
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
                mirrorObj.setSyncState(BlockMirror.SynchronizationState.RESYNCHRONIZING.toString());
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
            _log.info("Problem making SMI-S call: ", e);
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
                _helper.removeVolumeFromParkingSLOStorageGroup(storage, mirrorObj.getNativeId(), false);
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
}
