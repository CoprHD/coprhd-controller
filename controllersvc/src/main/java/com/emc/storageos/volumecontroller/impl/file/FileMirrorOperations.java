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

    void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    void failoverMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target, TaskCompleter completer);

    /**
     * Delete Mirror of a filesystem
     * 
     * @param storage
     * @param mirror
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer) throws DeviceControllerException;

    void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    // add management operation (pause, resume, failover, failback)

}
