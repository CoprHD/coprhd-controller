/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.SynchronizationState;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.api.HDSApiProtectionManager;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.hds.model.ReplicationInfo;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SimpleTaskCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSBlockMirrorDeleteJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSReplicationSyncJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSReplicationSyncJob.ReplicationStatus;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSCommandHelper;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.MirrorOperations;

public class HDSMirrorOperations implements MirrorOperations {

    private static final Logger log = LoggerFactory.getLogger(HDSMirrorOperations.class);
    private DbClient dbClient;
    private HDSApiFactory hdsApiFactory;
    private HDSProtectionOperations hdsProtectionOperations;
    private HDSCommandHelper hdsCommandHelper;

    /**
     * 1. Find ReplicationGroup objId from Device Manager
     * 2. Check dummy Host Group available on Storage System. if not available create a dummy Host Group name.
     * 3. Create a secondary volume and add dummy host group on it.
     * 4. create a SI pair.
     * 
     * @param storageSystem {@link StorageSystem}
     * @param mirrorVolumeURI {@link URI}
     * @param createInactive {@link Boolean}
     * @param taskCompleter {@link TaskCompleter}
     */
    @Override
    public void createSingleVolumeMirror(StorageSystem storageSystem, URI mirrorVolumeURI,
            Boolean createInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        log.info("START createSingleVolumeMirror operation");

        try {

            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem), storageSystem.getSmisUserName(),
                    storageSystem.getSmisPassword());
            HDSApiProtectionManager hdsApiProtectionManager = hdsApiClient.getHdsApiProtectionManager();
            String replicationGroupObjectID = hdsApiClient.getHdsApiProtectionManager().getReplicationGroupObjectId();

            if (replicationGroupObjectID == null) {
                log.error("Unable to find replication group information/pair management server for pair configuration");
                throw HDSException.exceptions.replicationGroupNotAvailable();
            }
            BlockMirror mirrorObj = dbClient.queryObject(BlockMirror.class, mirrorVolumeURI);
            Volume source = dbClient.queryObject(Volume.class, mirrorObj.getSource());
            hdsProtectionOperations.createSecondaryVolumeForMirror(storageSystem, source.getId(), mirrorObj);

            mirrorObj = dbClient.queryObject(BlockMirror.class, mirrorVolumeURI);
            hdsProtectionOperations.addDummyLunPath(hdsApiClient, mirrorObj);

            String pairName = hdsProtectionOperations.generatePairName(source, mirrorObj);
            log.info("Pair Name :{}", pairName);
            ReplicationInfo replicationInfo = hdsApiProtectionManager.
                    createShadowImagePair(replicationGroupObjectID, pairName,
                            HDSUtils.getSystemArrayType(storageSystem), HDSUtils.getSystemSerialNumber(storageSystem),
                            source.getNativeId(), mirrorObj.getNativeId(), storageSystem.getModel());
            mirrorObj.setSyncState(SynchronizationState.SYNCHRONIZED.name());
            dbClient.persistObject(mirrorObj);
            log.info("Replication Info object :{}", replicationInfo.toXMLString());
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            String errorMsg = String.format(CREATE_ERROR_MSG_FORMAT, mirrorVolumeURI);
            log.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("createSingleVolumeMirror",
                    e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("FINISHED createSingleVolumeMirror operation");

    }

    /**
     * Split ShadowImage pair
     */
    @Override
    public void fractureSingleVolumeMirror(StorageSystem storage, URI mirror,
            Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("fractureSingleVolumeMirror started");
        try {
            BlockMirror mirrorObj = dbClient.queryObject(BlockMirror.class, mirror);
            Volume sourceVolume = dbClient.queryObject(Volume.class, mirrorObj.getSource());
            hdsProtectionOperations.modifyShadowImagePair(storage, sourceVolume.getNativeId(),
                    mirrorObj.getNativeId(), HDSApiProtectionManager.ShadowImageOperationType.split);
            HDSJob syncjob = new HDSReplicationSyncJob(
                    storage.getId(), sourceVolume.getNativeId(),
                    mirrorObj.getNativeId(), ReplicationStatus.SPLIT, taskCompleter);
            hdsCommandHelper.waitForAsyncHDSJob(syncjob);
            mirrorObj.setSyncState(SynchronizationState.FRACTURED.name());
            dbClient.persistObject(mirrorObj);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            log.error("Failed to resume single volume mirror: {}", mirror, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("fractureSingleVolumeMirror completed");

    }

    /**
     * Resync ShadowImage pair
     */
    @Override
    public void resumeSingleVolumeMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("resumeSingleVolumeMirror started");
        try {
            BlockMirror mirrorObj = dbClient.queryObject(BlockMirror.class, mirror);
            Volume sourceVolume = dbClient.queryObject(Volume.class, mirrorObj.getSource());
            hdsProtectionOperations.modifyShadowImagePair(storage, sourceVolume.getNativeId(),
                    mirrorObj.getNativeId(), HDSApiProtectionManager.ShadowImageOperationType.resync);
            HDSJob syncjob = new HDSReplicationSyncJob(
                    storage.getId(), sourceVolume.getNativeId(),
                    mirrorObj.getNativeId(), ReplicationStatus.PAIR, taskCompleter);
            hdsCommandHelper.waitForAsyncHDSJob(syncjob);
            mirrorObj.setSyncState(SynchronizationState.SYNCHRONIZED.name());
            dbClient.persistObject(mirrorObj);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            log.error("Failed to resume single volume mirror: {}", mirror, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("resumeSingleVolumeMirror completed");
    }

    @Override
    public void establishVolumeNativeContinuousCopyGroupRelation(StorageSystem storage, URI sourceVolume,
            URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * 1. Delete ShadowImage Pair
     * 2. Delete DummyLunPath from secondary volume
     */
    @Override
    public void detachSingleVolumeMirror(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        NamedURI sourceVolumeURI = null;
        try {
            BlockMirror mirrorObj = dbClient.queryObject(BlockMirror.class, mirror);
            // TODO needs to sync pair and wait for synchronization here
            Volume source = dbClient.queryObject(Volume.class, mirrorObj.getSource());
            sourceVolumeURI = mirrorObj.getSource();
            boolean status = hdsProtectionOperations.modifyShadowImagePair(storage, source.getNativeId(),
                    mirrorObj.getNativeId(), HDSApiProtectionManager.ShadowImageOperationType.split);
            if (status) {
                String taskId = UUID.randomUUID().toString();
                TaskCompleter completer = new SimpleTaskCompleter(BlockMirror.class, mirror, taskId);

                HDSJob syncjob = new HDSReplicationSyncJob(
                        storage.getId(), source.getNativeId(),
                        mirrorObj.getNativeId(), ReplicationStatus.SPLIT, completer);
                hdsCommandHelper.waitForAsyncHDSJob(syncjob);
            } else {
                log.info("Replication info is not available on pair management server");
            }

            hdsProtectionOperations.deleteShadowImagePair(storage, source, mirrorObj);
            hdsProtectionOperations.removeDummyLunPath(storage, mirror);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            String errorMsg = String.format(DETACH_ERROR_MSG_FORMAT, mirror,
                    sourceVolumeURI != null ? sourceVolumeURI.toString() : HDSConstants.SPACE_STR);
            log.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("detachSingleVolumeMirror",
                    e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
    }

    /**
     * Deletes mirror instance from StorageSystem
     */
    @Override
    public void deleteSingleVolumeMirror(StorageSystem storageSystem, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Delete Mirror Start - Array:%s", storageSystem.getSerialNumber()));
            Set<String> thickLogicalUnitIdList = new HashSet<String>();
            Set<String> thinLogicalUnitIdList = new HashSet<String>();
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            String systemObjectID = HDSUtils.getSystemObjectID(storageSystem);
            BlockMirror mirrorObj = dbClient.queryObject(BlockMirror.class, mirror);

            logMsgBuilder.append(String.format("%nMirror:%s", mirrorObj.getLabel()));
            String logicalUnitObjectId = HDSUtils.getLogicalUnitObjectId(
                    mirrorObj.getNativeId(), storageSystem);
            LogicalUnit logicalUnit = hdsApiClient.getLogicalUnitInfo(systemObjectID,
                    logicalUnitObjectId);
            if (logicalUnit == null) {
                // related volume state (if any) has been deleted. skip
                // processing, if already deleted from array.
                log.info(String.format("Mirror %s already deleted: ",
                        mirrorObj.getNativeId()));
                // HDSMirrorOperations.removeReferenceFromSourceVolume(dbClient, mirrorObj);
                dbClient.markForDeletion(mirrorObj);
            } else {
                if (mirrorObj.getThinlyProvisioned()) {
                    thinLogicalUnitIdList.add(logicalUnitObjectId);
                } else {
                    thickLogicalUnitIdList.add(logicalUnitObjectId);
                }
                log.info(logMsgBuilder.toString());
                if (!thickLogicalUnitIdList.isEmpty()) {
                    String asyncThickLUsJobId = hdsApiClient.deleteThickLogicalUnits(systemObjectID,
                            thickLogicalUnitIdList, storageSystem.getModel());
                    if (null != asyncThickLUsJobId) {
                        ControllerServiceImpl.enqueueJob(new QueueJob(new HDSBlockMirrorDeleteJob(
                                asyncThickLUsJobId, mirrorObj.getStorageController(),
                                taskCompleter)));
                    }
                }

                if (!thinLogicalUnitIdList.isEmpty()) {
                    String asyncThinHDSJobId = hdsApiClient.deleteThinLogicalUnits(
                            systemObjectID, thinLogicalUnitIdList, storageSystem.getModel());

                    if (null != asyncThinHDSJobId) {
                        ControllerServiceImpl.enqueueJob(new QueueJob(
                                new HDSBlockMirrorDeleteJob(asyncThinHDSJobId, mirrorObj
                                        .getStorageController(), taskCompleter)));
                    }
                }

            }
            log.info("Delete Mirror End - Array: {} Mirror: {}", storageSystem.getSerialNumber(), mirror);
        } catch (Exception e) {
            log.error("Problem in deleteSingleVolumeMirror: ", e);
            ServiceError error = DeviceControllerErrors.hds.methodFailed(
                    "deleteSingleVolumeMirror", e.getMessage());
            taskCompleter.error(dbClient, error);
        }

    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    public void setHdsCommandHelper(HDSCommandHelper hdsCommandHelper) {
        this.hdsCommandHelper = hdsCommandHelper;
    }

    public void setHdsProtectionOperations(
            HDSProtectionOperations hdsProtectionOperations) {
        this.hdsProtectionOperations = hdsProtectionOperations;
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
    public void deleteGroupMirrors(StorageSystem storage, List<URI> mirrorList, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void removeMirrorFromDeviceMaskingGroup(StorageSystem system, List<URI> mirrorList,
            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

}
