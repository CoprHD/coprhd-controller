/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;

/*
 * Common operations of all replica types (clone, mirror, snapshot).
 */
public interface ReplicaOperations {
    /**
     * Create list replica.
     *
     * @param replicaList the replicas
     * @param createInactive
     * @param taskCompleter the task completer
     * @param storage the storage system
     *
     * @throws DeviceControllerException
     */
    public void createListReplica(StorageSystem storageSystem, List<URI> replicaList, Boolean createInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Detach list replica.
     *
     * @param replicaList the replicas
     * @param taskCompleter the task completer
     * @param storage the storage system
     *
     * @throws DeviceControllerException
     */
    public void detachListReplica(StorageSystem storageSystem, List<URI> replicaList, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Fracture list replica.
     *
     * @param replicaList the replicas
     * @param sync
     * @param taskCompleter the task completer
     * @param storage the storage system
     *
     * @throws DeviceControllerException
     */
    public void fractureListReplica(StorageSystem storageSystem, List<URI> replicaList, Boolean sync, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Delete list replica.
     *
     * @param replicaList the replicas
     * @param taskCompleter the task completer
     * @param storage the storage system
     *
     * @throws DeviceControllerException
     */
    public void deleteListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter)
            throws DeviceControllerException;
}
