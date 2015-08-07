/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.StorageSystem;

public interface CloneOperations
{
    public static final String CREATE_ERROR_MSG_FORMAT = "Failed to create single full copy of %s to %s";
    public static final String ACTIVATE_ERROR_MSG_FORMAT = "Failed to activate single full copy %s";
    public static final String DETACH_ERROR_MSG_FORMAT = "Failed to detach full copy %s from source %s";
    public static final String RESTORE_ERROR_MSG_FORMAT = "Failed to restore full copy %s";

    public void createSingleClone(StorageSystem storageSystem, URI sourceVolume, URI cloneVolume,
            Boolean createInactive, TaskCompleter taskCompleter);

    public void detachSingleClone(StorageSystem storageSystem, URI cloneVolume,
            TaskCompleter taskCompleter);

    public void activateSingleClone(StorageSystem storageSystem, URI fullCopy, TaskCompleter completer);

    public void restoreFromSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer);

    public void fractureSingleClone(StorageSystem storageSystem, URI sourceVolume, URI clone, TaskCompleter completer);

    public void resyncSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer);

    public void createGroupClone(StorageSystem storage, List<URI> cloneList, Boolean createInactive, TaskCompleter taskCompleter);

    public void activateGroupClones(StorageSystem storage, List<URI> clone, TaskCompleter taskCompleter);

    public void restoreGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer);

    public void fractureGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer);

    public void resyncGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer);

    public void detachGroupClones(StorageSystem storageSystem, List<URI> clone, TaskCompleter completer);

}
