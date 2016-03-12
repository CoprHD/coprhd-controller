/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;

import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * 
 * File Mirror protection actions interface class
 *
 */
public interface FileMirrorOperations {
    /**
     * Create a mirror for a filesystem
     * 
     * @param storage - URI of storage controller.
     * @param mirror
     * @param createInactive
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException;

    /**
     * Stop Mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Start Mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException;

    /**
     * Pause Mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Resume Mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Failover Mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void failoverMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException;

    /**
     * Resync the mirror link
     * 
     * @param primarySystem
     * @param secondarySystem
     * @param target
     * @param completer
     * @param policyName
     */
    void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target, TaskCompleter completer,
            String policyName);

    /**
     * Delete Mirror of a filesystem
     * 
     * @param storage
     * @param mirror
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Cancel the mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Refresh Mirror State of a filesystem
     * 
     * @param storage
     * @param source
     * @param target
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException;

    /**
     * Update Replication Policy of a filesystem
     * 
     * @param storage
     * @param source
     * @param target
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    void doModifyReplicationRPO(StorageSystem system,FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException;
}
