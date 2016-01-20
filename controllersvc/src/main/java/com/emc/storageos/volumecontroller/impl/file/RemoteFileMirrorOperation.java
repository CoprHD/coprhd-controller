/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;

public interface RemoteFileMirrorOperation {
	 /**
     * Create and establish a replication link between the given source and target fileshare.
     *
     * @param system
     * @param source
     * @param target
     * @param completer
     */
    void doCreateMirrorLink(StorageSystem system, URI source, URI target, TaskCompleter completer);

    /**
     * Detach a source and target from their replication link.
     *
     * @param system
     * @param source
     * @param target
     * @param completer
     */
    void doDetachMirrorLink(StorageSystem system, URI source, URI target, TaskCompleter completer);

    /**
     * Starts a replication link.
     *
     * @param system
     * @param target
     * @param completer
     */
    void doStartMirrorLink(StorageSystem system, FileShare target, TaskCompleter completer);

    /**
     * stop a replication link.
     *
     * @param system
     * @param target
     * @param completer
     */
    void doStopMirrorLink(StorageSystem system, FileShare target, TaskCompleter completer);
    
    /**
     * Cancel a replication link.
     *
     * @param system
     * @param target
     * @param completer
     */
    void doCancelMirrorLink(StorageSystem system, FileShare target, TaskCompleter completer);
    
    /**
     * Rollback replication links.
     *
     * @param system
     * @param sources
     * @param targets
     * @param completer
     */
    void doRollbackMirrorLink(StorageSystem system, List<URI> sources, List<URI> targets, TaskCompleter completer);

    /**
     * Suspend replication links.
     *
     * @param system
     * @param target
     *
     * @param completer
     */
    void doSuspendLink(StorageSystem system, FileShare target, TaskCompleter completer);


    /**
     * Resume replication links.
     *
     * @param system
     * @param target
     * @param completer
     */
    void doResumeLink(StorageSystem system, FileShare target, TaskCompleter completer);

    /**
     * Failover replication links.
     *
     * @param system
     * @param target
     * @param completer
     */
    void doFailoverLink(StorageSystem system, FileShare target, TaskCompleter completer);

}
