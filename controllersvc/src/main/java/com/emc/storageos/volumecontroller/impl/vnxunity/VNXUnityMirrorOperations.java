/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.RemoteSystem;
import com.emc.storageos.vnxe.models.ReplicationSession;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;

public class VNXUnityMirrorOperations extends VNXUnityOperations implements FileMirrorOperations {
    private static final Logger _log = LoggerFactory.getLogger(VNXUnityMirrorOperations.class);

    /**
     * Create Mirror between source and target fileshare
     */
    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {

        FileShare sourceFileShare = dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceStorageSystem = dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        StorageSystem targetStorageSystem = dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        VirtualPool virtualPool = dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());
        VNXeApiClient apiClient = getVnxUnityClient(sourceStorageSystem);

        int maxTimeOutOfSync = convertToMinutes(virtualPool.getFrRpoValue(), virtualPool.getFrRpoType());
        if (virtualPool != null) {
            if (virtualPool.getFrRpoValue() == 0) {
                // Zero RPO value means policy has to be started manually-NO Schedule
                maxTimeOutOfSync = -1;
            }
        }
        VNXeCommandResult result = apiClient.createReplicationSession(getNativeIdByFileShareURI(source), getNativeIdByFileShareURI(target),
                maxTimeOutOfSync, getRemoteSystem(apiClient, targetStorageSystem));
        _log.info("Creating File Share Replication Session");
        if (result.getSuccess()) {
            completer.ready(dbClient);
        } else {
            VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to create a Replication Session");
            completer.error(dbClient, ex);
        }
    }

    @Override
    public void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            ReplicationSession session = apiClient.getReplicationSession(
                    dbClient.queryObject(FileShare.class, target.getParentFileShare()).getNativeId(), target.getNativeId());
            try {
                VNXeCommandResult result = apiClient.syncReplicationSession(session.getId());
                if (result.getSuccess()) {
                    completer.ready(dbClient);
                } else {
                    VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to Start a Replication Session");
                    completer.error(dbClient, ex);
                }
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            }
        }
    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            ReplicationSession session = apiClient.getReplicationSession(
                    dbClient.queryObject(FileShare.class, target.getParentFileShare()).getNativeId(), target.getNativeId());
            try {
                VNXeCommandResult result = apiClient.pauseReplicationSession(session.getId());
                if (result.getSuccess()) {
                    completer.ready(dbClient);
                } else {
                    VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to Pause a Replication Session");
                    completer.error(dbClient, ex);
                }
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            }

        }
    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            ReplicationSession session = apiClient.getReplicationSession(
                    dbClient.queryObject(FileShare.class, target.getParentFileShare()).getNativeId(), target.getNativeId());
            try {
                VNXeCommandResult result = apiClient.resumeReplicationSession(session.getId(), false);
                if (result.getSuccess()) {
                    completer.ready(dbClient);
                } else {
                    VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to Resume a Replication Session");
                    completer.error(dbClient, ex);
                }
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            }

        }
    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        VNXeApiClient apiClient = getVnxUnityClient(system);
        try {
            ReplicationSession session = apiClient.getReplicationSession(getNativeIdByFileShareURI(source),
                    getNativeIdByFileShareURI(target));
            VNXeCommandResult result = apiClient.deleteReplicationSession(session.getId());
            if (result.getSuccess()) {
                completer.ready(dbClient);
            } else {
                VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to Delete a Replication Session");
                completer.error(dbClient, ex);
            }
        } catch (VNXeException ex) {
            completer.error(dbClient, ex);
        }
    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem systemTarget, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {

        if (target.getParentFileShare() != null) {
            FileShare sourceFileShare = dbClient.queryObject(FileShare.class, target.getParentFileShare());
            StorageSystem sourceStorageSystem = dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
            VNXeApiClient apiClient = getVnxUnityClient(sourceStorageSystem);
            ReplicationSession session = apiClient.getReplicationSession(sourceStorageSystem.getNativeId(), target.getNativeId());
            try {
                VNXeCommandResult result = apiClient.failoverReplicationSession(session.getId(), true);
                if (result.getSuccess()) {
                    completer.ready(dbClient);
                } else {
                    VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to sync a Replication Session");
                    completer.error(dbClient, ex);
                }
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            }
        }
    }

    @Override
    public void failbackMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            FileShare sourceFileShare = dbClient.queryObject(FileShare.class, target.getParentFileShare());
            StorageSystem sourceStorageSystem = dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
            VNXeApiClient apiClient = getVnxUnityClient(sourceStorageSystem);
            ReplicationSession session = apiClient.getReplicationSession(sourceStorageSystem.getNativeId(), target.getNativeId());
            try {
                VNXeCommandResult result = apiClient.failbackReplicationSession(session.getId(), false);
                if (result.getSuccess()) {
                    completer.ready(dbClient);
                } else {
                    VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to sync a Replication Session");
                    completer.error(dbClient, ex);
                }
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            }
        }
    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target,
            TaskCompleter completer, String policyName) {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(primarySystem);
            ReplicationSession session = apiClient.getReplicationSession(
                    dbClient.queryObject(FileShare.class, target.getParentFileShare()).getNativeId(), target.getNativeId());
            try {
                VNXeCommandResult result = apiClient.syncReplicationSession(session.getId());
                if (result.getSuccess()) {
                    completer.ready(dbClient);
                } else {
                    VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to sync a Replication Session");
                    completer.error(dbClient, ex);
                }
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            }
        }
    }

    @Override
    public void doModifyReplicationRPO(StorageSystem system, Long rpoValue, String rpoType, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        if (target.getParentFileShare() != null) {
            VNXeApiClient apiClient = getVnxUnityClient(system);
            ReplicationSession session = apiClient.getReplicationSession(
                    dbClient.queryObject(FileShare.class, target.getParentFileShare()).getNativeId(), target.getNativeId());
            try {
                VNXeCommandResult result = apiClient.modifyReplicationSession(session.getId(), convertToMinutes(rpoValue, rpoType));
                if (result.getSuccess()) {
                    completer.ready(dbClient);
                } else {
                    VNXeException ex = VNXeException.exceptions.vnxeCommandFailed("Failed to modify a Replication Session");
                    completer.error(dbClient, ex);
                }
            } catch (VNXeException ex) {
                completer.error(dbClient, ex);
            }
        }
    }

    @Override
    public void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Unity not supported
    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        if (target.getParentFileShare() != null) {
            // TODO Unity not supported
            if (cmdResult.getCommandSuccess()) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, cmdResult.getServiceCoded());
            }
        }
    }

    @Override
    public void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String devPolicyName)
            throws DeviceControllerException {
        FileShare sourceFileShare = dbClient.queryObject(FileShare.class, target.getParentFileShare().getURI());
        BiosCommandResult cmdResult = null;
        // TODO Unity not supported
        if (cmdResult.getCommandSuccess()) {
            completer.ready(dbClient);
        } else {
            completer.error(dbClient, cmdResult.getServiceCoded());
        }
    }

    private String getNativeIdByFileShareURI(URI fsId) {
        return dbClient.queryObject(FileShare.class, fsId).getNativeId();
    }

    private RemoteSystem getRemoteSystem(VNXeApiClient client, StorageSystem system) {
        VNXeApiClient remoteClient = getVnxUnityClient(system);
        return client.getRemoteSystemBySerial(remoteClient.getStorageSystem().getSerialNumber());
    }

    private int convertToMinutes(Long fsRpoValue, String fsRpoType) {
        int multiplier = 1;
        switch (fsRpoType.toUpperCase()) {
            case "MINUTES":
                multiplier = 1;
                break;
            case "HOURS":
                multiplier = 60;
                break;
            case "DAYS":
                multiplier = 60 * 24;
                break;
        }
        return fsRpoValue.intValue() * multiplier;
    }
}
