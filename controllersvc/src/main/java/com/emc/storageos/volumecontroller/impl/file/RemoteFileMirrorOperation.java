/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

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
     * Rollback replication links.
     *
     * @param system
     * @param sources
     * @param targets
     * @param completer
     * @param opId
     */
    void doRollbackMirrorLink(StorageSystem system, List<URI> sources, List<URI> targets, TaskCompleter completer, String opId);

}
