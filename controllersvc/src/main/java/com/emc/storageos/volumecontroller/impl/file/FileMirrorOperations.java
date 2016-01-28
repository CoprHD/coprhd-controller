/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;

import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;

public interface FileMirrorOperations {
    /**
     * Create a local mirror for a filesystem
     * 
     * @param storage - URI of storage controller.
     * @param mirror
     * @param createInactive
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    void createMirrorFileShare(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException;

    
    void stopMirrorFileShare(StorageSystem system, FileShare target, TaskCompleter completer)throws DeviceControllerException;
    /**
     * Delete Mirror of a filesystem
     * 
     * @param storage
     * @param mirror
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    void deleteMirrorFileShare(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException;

    //add management operation (pause, resume, failover, failback)

}
