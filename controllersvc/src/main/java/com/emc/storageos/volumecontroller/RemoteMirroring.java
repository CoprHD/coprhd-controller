/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFMirrorCreateCompleter;

/**
 * An interface for storage devices that support remote mirrors.
 * 
 * Created by bibbyi1 on 5/15/2015.
 */
public interface RemoteMirroring {

    /**
     * Adds created source/target Volume pairs to a previously established remotely mirrored
     * consistency group.
     * 
     * @param system
     * @param sources
     * @param remoteDirectorGroup
     * @param forceAdd
     * @param completer
     */
    void doAddVolumePairsToCg(StorageSystem system, List<URI> sources, URI remoteDirectorGroup, boolean forceAdd, TaskCompleter completer);

    /**
     * Create and establish a replication link between the given source and target volume.
     * 
     * @param system
     * @param source
     * @param target
     * @param completer
     */
    void doCreateLink(StorageSystem system, URI source, URI target, TaskCompleter completer);

    /**
     * Create and establish replication links from a list of source and target volumes.
     * 
     * @param system
     * @param sources
     * @param targets
     * @param completer
     */
    void doCreateListReplicas(StorageSystem system, List<URI> sources, List<URI> targets, boolean addWaitForCopyState,
            TaskCompleter completer);

    /**
     * Detach a source and target from their replication link.
     * 
     * @param system
     * @param source
     * @param target
     * @param completer
     */
    void doDetachLink(StorageSystem system, URI source, URI target, boolean onGroup, TaskCompleter completer);

    /**
     * Removes the source and target from their device groups, which should in turn remove them.
     * 
     * @param system
     * @param source
     * @param target
     * @param completer
     */
    void doRemoveDeviceGroups(StorageSystem system, URI source, URI target, TaskCompleter completer);

    /**
     * Rollback replication links.
     * 
     * @param system
     * @param sources
     * @param targets
     * @param isGroupRollback
     * @param completer
     */
    void doRollbackLinks(StorageSystem system, List<URI> sources, List<URI> targets, boolean isGroupRollback, TaskCompleter completer);

    /**
     * Split replication links.
     * 
     * @param system
     * @param target
     * @param rollback
     * @param completer
     */
    void doSplitLink(StorageSystem system, Volume target, boolean rollback, TaskCompleter completer);

    /**
     * Suspend replication links.
     * 
     * @param system
     * @param target
     * @param consExempt
     * @param completer
     */
    void doSuspendLink(StorageSystem system, Volume target, boolean consExempt, boolean refreshVolumeProperties, TaskCompleter completer);

    /**
     * Resume replication links.
     * 
     * @param system
     * @param target
     * @param completer
     */
    void doResumeLink(StorageSystem system, Volume target, boolean refreshVolumeProperties, TaskCompleter completer);

    /**
     * Failover replication links.
     * 
     * @param system
     * @param target
     * @param completer
     */
    void doFailoverLink(StorageSystem system, Volume target, TaskCompleter completer);

    /**
     * Perform a failover-cancel on the replication links.
     * 
     * @param system
     * @param target
     * @param completer
     */
    void doFailoverCancelLink(StorageSystem system, Volume target, TaskCompleter completer);

    /**
     * Resynchronize replication links.
     * 
     * @param system
     * @param source
     * @param target
     * @param completer
     */
    void doResyncLink(StorageSystem system, URI source, URI target, TaskCompleter completer);

    /**
     * Remove a source and target pair from a remote group.
     * 
     * @param system
     * @param source
     * @param target
     * @param rollback
     * @param completer
     */
    void doRemoveVolumePair(StorageSystem system, URI source, URI target, boolean rollback, TaskCompleter completer);

    /**
     * Starts a replication link.
     * 
     * @param system
     * @param target
     * @param completer
     */
    void doStartLink(StorageSystem system, Volume target, TaskCompleter completer);

    /**
     * Stops a replication link.
     * 
     * @param system
     * @param target
     * @param completer
     */
    void doStopLink(StorageSystem system, Volume target, TaskCompleter completer);

    /**
     * Creates consistency groups from the given sources/targets and establishes
     * replication.
     * 
     * @param system
     * @param sources
     * @param targets
     * @param completer
     */
    void doCreateCgPairs(StorageSystem system, List<URI> sources, List<URI> targets, SRDFMirrorCreateCompleter completer);

    /**
     * Finds and returns the volumes that are part of a remote group.
     * 
     * @param system
     * @param rdfGroup
     * @return
     */
    Set<String> findVolumesPartOfRemoteGroup(StorageSystem system, RemoteDirectorGroup rdfGroup);

    /**
     * Swaps the personality of the existing source and target pair.
     * 
     * - The source volume becomes the target.
     * - The target volume becomes the source.
     * 
     * @param system
     * @param target
     * @param completer
     */
    void doSwapVolumePair(StorageSystem system, Volume target, TaskCompleter completer);

    /**
     * Synchronizes replication link.
     * 
     * @param system
     * @param target
     * @param completer
     * @throws Exception
     */
    void doSyncLink(StorageSystem system, Volume target, TaskCompleter completer) throws Exception;

    /**
     * Called after replication links have been established.
     * Implementations of this method should ensure that the ViPR source/target pairings
     * reflect the pairings on the storage system.
     * 
     * @param sourceURIs
     * @param targetURIs
     */
    void doUpdateSourceAndTargetPairings(List<URI> sourceURIs, List<URI> targetURIs);

    /**
     * Refresh the storage system.
     * 
     * @param targetURIs
     */
    void refreshStorageSystem(URI systemURI, List<URI> volumeURIsToCheck);

    /**
     * Refresh the volume properties
     * 
     * @param systemURI reference to storage system
     * @param volumeURIs List of volume URIs
     * @throws Exception
     */
    void refreshVolumeProperties(URI systemURI, List<URI> volumeURIs) throws Exception;

    /**
     * Change SRDF Copy Mode.
     * 
     * @param system
     * @param target
     * @param completer
     * @throws Exception
     */
    void doChangeCopyMode(StorageSystem system, Volume target, TaskCompleter completer);

}
