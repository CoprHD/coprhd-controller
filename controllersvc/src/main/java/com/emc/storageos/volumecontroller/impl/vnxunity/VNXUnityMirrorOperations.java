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
        BiosCommandResult cmdResult = null;

        FileShare sourceFileShare = dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceStorageSystem = dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        StorageSystem targetStorageSystem = dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        String policyName = targetFileShare.getLabel();

        VirtualPool virtualPool = dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());

        String schedule = null;
        if (virtualPool != null) {
            if (virtualPool.getFrRpoValue() == 0) {
                // Zero RPO value means policy has to be started manually-NO Schedule
                schedule = "";
            } else {
                // TODO
            }
        }
        // TODO:
        _log.info("Creating File Share Mirror Link");
        if (cmdResult.getCommandSuccess()) {
            completer.ready(dbClient);
        } else {
            completer.error(dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        if (target.getParentFileShare() != null) {
            // TODO
            if (cmdResult.getCommandSuccess()) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, cmdResult.getServiceCoded());
            }
        }
    }

    @Override
    public void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        // TODO
        if (cmdResult.getCommandSuccess()) {
            completer.ready(dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(dbClient, cmdResult.getMessage());
        } else {
            completer.error(dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        if (target.getParentFileShare() != null) {
            // TODO
            if (cmdResult.getCommandSuccess()) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, cmdResult.getServiceCoded());
            }
        }
    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        if (target.getParentFileShare() != null) {
            // TODO
            if (cmdResult.getCommandSuccess()) {
                completer.ready(dbClient);
            } else if (cmdResult.getCommandPending()) {
                completer.statusPending(dbClient, cmdResult.getMessage());
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
        // TODO
        if (cmdResult.getCommandSuccess()) {
            completer.ready(dbClient);
        } else {
            completer.error(dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        FileShare targetFileShare = dbClient.queryObject(FileShare.class, target);
        BiosCommandResult cmdResult = null;
        // TODO

        // Check if mirror policy exists on target system if yes, delete it..
        if (cmdResult.getCommandSuccess()) {
            completer.ready(dbClient);
        } else {
            completer.error(dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem systemTarget, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        // TODO
        if (cmdResult.getCommandSuccess()) {
            completer.ready(dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(dbClient, cmdResult.getMessage());
        } else {
            completer.error(dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target,
            TaskCompleter completer, String policyName) {
        BiosCommandResult cmdResult = null;
        // TODO
        if (cmdResult.getCommandSuccess()) {
            completer.ready(dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(dbClient, cmdResult.getMessage());
        } else {
            completer.error(dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void doModifyReplicationRPO(StorageSystem system, Long rpoValue, String rpoType, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO
    }

    @Override
    public void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO
    }
}
