/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.TaskCompleter;

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
    void doStartMirrorLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName);

    /**
     * Starts a replication link.
     *
     * @param system
     * @param target
     * @param completer
     */
    void doRefreshMirrorLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer);

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
     * @param opId
     */
    void doRollbackMirrorLink(StorageSystem system, List<URI> sources, List<URI> targets, TaskCompleter completer, String opId);

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
    void doFailoverLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName);

    /**
     * Failback replication links.
     *
     * @param system
     * @param target
     * @param completer
     */
    void doFailbackLink(StorageSystem system, FileShare target, TaskCompleter completer);

    /**
     * Resync replication links
     * 
     * @param primarySystem
     * @param secondarySystem
     * @param Target
     * @param completer
     */
    void doResyncLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare Target, TaskCompleter completer,
            String policyName);

    /**
     * Modify replication RPO.
     *
     * @param system
     * @param rpoValue
     * @param rpoType
     * @param target
     * @param completer
     */
    void doModifyReplicationRPO(StorageSystem system, Long rpoValue, String rpoType, FileShare target, TaskCompleter completer);

}
