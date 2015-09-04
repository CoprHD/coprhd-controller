/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Interface for block mirror operations.
 * - Operations for create
 */
public interface MirrorOperations {
    public static final String CREATE_ERROR_MSG_FORMAT = "Failed to create single mirror %s";
    public static final String DETACH_ERROR_MSG_FORMAT = "Failed to detach mirror %s from source %s";

    void createSingleVolumeMirror(StorageSystem storage, URI mirror, Boolean createInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    void fractureSingleVolumeMirror(StorageSystem storage, URI mirror, Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    void resumeSingleVolumeMirror(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException;

    void establishVolumeNativeContinuousCopyGroupRelation(StorageSystem storage, URI sourceVolume,
            URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException;

    void detachSingleVolumeMirror(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException;

    void deleteSingleVolumeMirror(StorageSystem storage, URI mirror, TaskCompleter taskCompleter) throws DeviceControllerException;

    void createGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean createInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    void fractureGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    void resumeGroupMirrors(StorageSystem storage, List<URI> mirrorList, TaskCompleter taskCompleter) throws DeviceControllerException;

    void detachGroupMirrors(StorageSystem storage, List<URI> mirrorList, Boolean deleteGroup, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    void deleteGroupMirrors(StorageSystem storage, List<URI> mirrorList, TaskCompleter taskCompleter) throws DeviceControllerException;

    void removeMirrorFromDeviceMaskingGroup(StorageSystem system, List<URI> mirrorList, TaskCompleter completer)
            throws DeviceControllerException;
}
