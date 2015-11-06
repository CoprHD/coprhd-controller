/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.api.HDSApiProtectionManager;
import com.emc.storageos.hds.model.ReplicationInfo;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSReplicationSyncJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSReplicationSyncJob.ReplicationStatus;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;

public class HDSCloneOperations implements CloneOperations {
    private static final Logger log = LoggerFactory.getLogger(HDSCloneOperations.class);
    private DbClient dbClient;
    private HDSApiFactory hdsApiFactory;
    private HDSProtectionOperations hdsProtectionOperations;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * @param hdsApiFactory the hdsApiFactory to set
     */
    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    /**
     * 1. Find ReplicationGroup objId from Device Manager
     * 2. Check dummy Host Group available on Storage System. if not available create a dummy Host Group name.
     * 3. Create a secondary volume and add dummy host group on it.
     * 4. create a SI pair
     * 
     * Note that if createInactive is false, then a subsequent step in the
     * full copy creation workflow will do a wait for synchronization. This
     * will split the pair, which makes the clone active.
     * 
     * @param storageSystem {@link StorageSystem}
     * @param sourceVolumeURI {@link URI}
     * @param cloneVolumeURI {@link URI}
     * @param createInactive {@link Boolean}
     * @param taskCompleter {@link TaskCompleter}
     */
    @Override
    public void createSingleClone(StorageSystem storageSystem,
            URI sourceVolumeURI, URI cloneVolumeURI, Boolean createInactive,
            TaskCompleter taskCompleter) {
        log.info("START createSingleClone operation");

        Volume cloneVolume = null;
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
            cloneVolume = dbClient.queryObject(Volume.class, cloneVolumeURI);
            hdsProtectionOperations.createSecondaryVolumeForClone(storageSystem, sourceVolumeURI, cloneVolume);

            // Need to fetch clone volume from db to get volume's nativeId
            cloneVolume = dbClient.queryObject(Volume.class, cloneVolumeURI);

            hdsProtectionOperations.addDummyLunPath(hdsApiClient, cloneVolume);

            BlockObject source = BlockObject.fetch(dbClient, sourceVolumeURI);

            String pairName = hdsProtectionOperations.generatePairName(source, cloneVolume);
            log.info("Pair Name :{}", pairName);
            ReplicationInfo replicationInfo = hdsApiProtectionManager.
                    createShadowImagePair(replicationGroupObjectID, pairName,
                            HDSUtils.getSystemArrayType(storageSystem), HDSUtils.getSystemSerialNumber(storageSystem),
                            source.getNativeId(), cloneVolume.getNativeId(), storageSystem.getModel());
            log.info("Replication Info object :{}", replicationInfo.toXMLString());
            log.info("createInactive :{}", createInactive);
            cloneVolume.setSyncActive(false);
            cloneVolume.setReplicaState(ReplicationState.INACTIVE.name());
            dbClient.persistObject(cloneVolume);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            String errorMsg = String.format(CREATE_ERROR_MSG_FORMAT, sourceVolumeURI, cloneVolumeURI);
            log.error(errorMsg, e);
            Volume clone = dbClient.queryObject(Volume.class, cloneVolumeURI);
            if (clone != null) {
                clone.setInactive(true);
                dbClient.persistObject(clone);
            }
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("createSingleClone",
                    e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
    }

    /**
     * Detaches clone volume relationship from source volume.
     * 1. Delete ShadowImage pair from replicationGroup.
     * 2. Delete dummyLunPath from secondary volume.
     * 
     * @param storageSystem {@link StorageSystem}
     * @param cloneVolumeURI {@link URI}
     * @param taskCompleter {@link TaskCompleter}
     */
    @Override
    public void detachSingleClone(StorageSystem storageSystem, URI cloneVolumeURI,
            TaskCompleter taskCompleter) {
        URI sourceVolumeURI = null;
        try {
            Volume targetVolume = dbClient.queryObject(Volume.class, cloneVolumeURI);
            sourceVolumeURI = targetVolume.getAssociatedSourceVolume();
            Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolumeURI);
            hdsProtectionOperations.deleteShadowImagePair(storageSystem, sourceVolume, targetVolume);
            hdsProtectionOperations.removeDummyLunPath(storageSystem, cloneVolumeURI);
            ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(targetVolume, dbClient);
            targetVolume.setReplicaState(ReplicationState.DETACHED.name());
            targetVolume.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
            dbClient.persistObject(targetVolume);
            if (taskCompleter != null) {
                taskCompleter.ready(dbClient);
            }
        } catch (Exception e) {
            String errorMsg = String.format(DETACH_ERROR_MSG_FORMAT, cloneVolumeURI, sourceVolumeURI);
            log.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerErrors.hds.methodFailed("detachSingleClone",
                    e.getMessage());
            if (taskCompleter != null) {
                taskCompleter.error(dbClient, serviceError);
            }
        }
    }

    /**
     * @param storageSystem {@link StorageSystem} StorageSystem instance
     * @param fullCopy {@link URI} clone's URI
     * @param completer {@link TaskCompleter}
     * 
     * @prereq Create full copy as inactive
     * 
     * @brief Activate Clone Volume
     */
    @Override
    public void activateSingleClone(StorageSystem storageSystem, URI fullCopy,
            TaskCompleter completer) {
        log.info("START activateSingleClone for {}", fullCopy);
        try {
            Volume clone = dbClient.queryObject(Volume.class, fullCopy);
            Volume sourceVolume = dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
            hdsProtectionOperations.modifyShadowImagePair(storageSystem, sourceVolume.getNativeId(),
                    clone.getNativeId(), HDSApiProtectionManager.ShadowImageOperationType.split);
            ControllerServiceImpl.enqueueJob(new QueueJob(new HDSReplicationSyncJob(
                    storageSystem.getId(), sourceVolume.getNativeId(),
                    clone.getNativeId(), ReplicationStatus.SPLIT, completer)));
            // Update state.
            clone.setSyncActive(true);
            clone.setReplicaState(ReplicationState.SYNCHRONIZED.name());
            dbClient.persistObject(clone);
            log.info("FINISH activateSingleClone for {}", fullCopy);
        } catch (Exception e) {
            String errorMsg = String.format(ACTIVATE_ERROR_MSG_FORMAT, fullCopy);
            log.error(errorMsg, e);
            completer.error(dbClient, DeviceControllerException.exceptions.activateVolumeFullCopyFailed(e));
        }
    }

    public void setHdsProtectionOperations(
            HDSProtectionOperations hdsProtectionOperations) {
        this.hdsProtectionOperations = hdsProtectionOperations;
    }

    @Override
    public void restoreFromSingleClone(StorageSystem storageSystem, URI cloneURI,
            TaskCompleter taskCompleter) {
        try {
            Volume cloneVolume = dbClient.queryObject(Volume.class, cloneURI);
            Volume sourceVolume = dbClient.queryObject(Volume.class,
                    cloneVolume.getAssociatedSourceVolume());
            hdsProtectionOperations.modifyShadowImagePair(storageSystem,
                    sourceVolume.getNativeId(), cloneVolume.getNativeId(),
                    HDSApiProtectionManager.ShadowImageOperationType.restore);
            ControllerServiceImpl.enqueueJob(new QueueJob(new HDSReplicationSyncJob(
                    storageSystem.getId(), sourceVolume.getNativeId(), cloneVolume
                            .getNativeId(), ReplicationStatus.PAIR, taskCompleter)));
            log.info("FINISH restoreFromSingleClone for {}", cloneURI);
        } catch (Exception e) {
            String errorMsg = String.format(RESTORE_ERROR_MSG_FORMAT, cloneURI);
            log.error(errorMsg, e);
            taskCompleter.error(dbClient,
                    DeviceControllerException.exceptions.activateVolumeFullCopyFailed(e));
        }
    }

    @Override
    public void fractureSingleClone(StorageSystem storageSystem, URI sourceVolume,
            URI clone, TaskCompleter completer) {

    }

    @Override
    public void resyncSingleClone(StorageSystem storageSystem, URI cloneURI, TaskCompleter taskCompleter) {
        try {
            Volume cloneVolume = dbClient.queryObject(Volume.class, cloneURI);
            Volume sourceVolume = dbClient.queryObject(Volume.class,
                    cloneVolume.getAssociatedSourceVolume());
            hdsProtectionOperations.modifyShadowImagePair(storageSystem,
                    sourceVolume.getNativeId(), cloneVolume.getNativeId(),
                    HDSApiProtectionManager.ShadowImageOperationType.resync);
            ControllerServiceImpl.enqueueJob(new QueueJob(new HDSReplicationSyncJob(
                    storageSystem.getId(), sourceVolume.getNativeId(), cloneVolume
                            .getNativeId(), ReplicationStatus.PAIR, taskCompleter)));
            log.info("FINISH resyncSingleClone for {}", cloneURI);
        } catch (Exception e) {
            String errorMsg = String.format(RESTORE_ERROR_MSG_FORMAT, cloneURI);
            log.error(errorMsg, e);
            taskCompleter.error(dbClient,
                    DeviceControllerException.exceptions.activateVolumeFullCopyFailed(e));
        }
    }

    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
            Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void activateGroupClones(StorageSystem storage, List<URI> clone, TaskCompleter taskCompleter) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void restoreGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void fractureGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void resyncGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void detachGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();

    }

    @Override
    public void establishVolumeCloneGroupRelation(StorageSystem storage, URI sourceVolume, URI clone, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

}
