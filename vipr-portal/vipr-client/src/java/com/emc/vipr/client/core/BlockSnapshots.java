/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.SnapshotList;
import com.emc.storageos.model.block.BlockConsistencyGroupSnapshotCreate;
import com.emc.storageos.model.block.BlockSnapshotBulkRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.block.VolumeSnapshotParam;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.block.export.ITLRestRepList;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Block snapshot resources.
 * <p>
 * Base URL: <tt>/block/snapshots</tt>
 */
public class BlockSnapshots extends ProjectResources<BlockSnapshotRestRep> implements
        TaskResources<BlockSnapshotRestRep> {
    public BlockSnapshots(ViPRCoreClient parent, RestClient client) {
        super(parent, client, BlockSnapshotRestRep.class, PathConstants.BLOCK_SNAPSHOT_URL);
    }

    @Override
    public BlockSnapshots withInactive(boolean inactive) {
        return (BlockSnapshots) super.withInactive(inactive);
    }

    @Override
    public BlockSnapshots withInternal(boolean internal) {
        return (BlockSnapshots) super.withInternal(internal);
    }

    @Override
    protected List<BlockSnapshotRestRep> getBulkResources(BulkIdParam input) {
        BlockSnapshotBulkRep response = client.post(BlockSnapshotBulkRep.class, input, getBulkUrl());
        return defaultList(response.getBlockSnapshots());
    }

    /**
     * Gets a list of block snapshot references from the given URL (path + args).
     * 
     * @param path
     *            the path to get.
     * @param args
     *            the arguments for the path.
     * @return the list of snapshot references.
     */
    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
        SnapshotList response = client.get(SnapshotList.class, path, args);
        return defaultList(response.getSnapList());
    }

    @Override
    public Tasks<BlockSnapshotRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<BlockSnapshotRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Begins activating a given block snapshot by ID.
     * <p>
     * API Call: <tt>POST /block/snapshots/{id}/activate</tt>
     * 
     * @param id
     *            the ID of the snapshot to activate.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<BlockSnapshotRestRep> activate(URI id) {
        return postTask(getIdUrl() + "/activate", id);
    }

    /**
     * Begins restoring a given block snapshot by ID.
     * <p>
     * API Call: <tt>POST /block/snapshots/{id}/restore</tt>
     * 
     * @param id
     *            the ID of the snapshot to restore.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<BlockSnapshotRestRep> restore(URI id) {
        return postTask(getIdUrl() + "/restore", id);
    }

    /**
     * Begins creating group synchronization between
     * volume group and snapshot group.
     * <p>
     * API Call: <tt>POST /block/snapshots/{id}/start</tt>
     * 
     * @param id
     *            the ID of the snapshot.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<BlockSnapshotRestRep> start(URI id) {
        return postTask(getIdUrl() + "/start", id);
    }

    /**
     * Begins deactivating a given block snapshot by ID.
     * <p>
     * API Call: <tt>POST /block/snapshots/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the snapshot to deactivate.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<BlockSnapshotRestRep> deactivate(URI id) {
        return doDeactivateWithTasks(id);
    }

    /**
     * Gets the list of exports (initiator-target-lun) for a given block snapshot by ID.
     * <p>
     * API Call: <tt>GET /block/snapshots/{id}/exports</tt>
     * 
     * @param id
     *            the ID of the snapshot.
     * @return the list of exports for a snapshot.
     */
    public List<ITLRestRep> listExports(URI id) {
        ITLRestRepList response = client.get(ITLRestRepList.class, getIdUrl() + "/exports", id);
        return defaultList(response.getExportList());
    }

    /**
     * Gets the URL for looking up block snapshots by volume: <tt>/block/volumes/{volumeId}/protection/snapshots</tt>
     * 
     * @return the URL for snapshots by volume.
     */
    protected String getByVolumeUrl() {
        return PathConstants.BLOCK_VOLUMES_URL + "/{volumeId}/protection/snapshots";
    }

    /**
     * Lists the block snapshots for a given block volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{volumeId}/protection/snapshots</tt>
     * 
     * @param volumeId
     *            the ID of the block volume.
     * @return the list of snapshot references for the volume.
     */
    public List<NamedRelatedResourceRep> listByVolume(URI volumeId) {
        return getList(getByVolumeUrl(), volumeId);
    }

    /**
     * Gets the block snapshots for a given block volume.
     * 
     * @param volumeId
     *            the ID of the block volume.
     * @return the list of block snapshots for the volume.
     * 
     * @see #listByVolume(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<BlockSnapshotRestRep> getByVolume(URI volumeId) {
        return getByVolume(volumeId, null);
    }

    /**
     * Gets the block snapshots for a given block volume, optionally filtering the results.
     * 
     * @param volumeId
     *            the ID of the block volume.
     * @param filter
     *            the filter to apply (may be null, for no filtering).
     * @return the list of block snapshots for the volume.
     * 
     * @see #listByVolume(URI)
     * @see #getByRefs(java.util.Collection, ResourceFilter)
     */
    public List<BlockSnapshotRestRep> getByVolume(URI volumeId, ResourceFilter<BlockSnapshotRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByVolume(volumeId);
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a snapshot (or snapshots) of a given block volume by ID.
     * <p>
     * API Call: <tt>POST /block/volumes/{volumeId}/protection/snapshots</tt>
     * 
     * @param volumeId
     *            the ID of the block volume to snapshot.
     * @param input
     *            the snapshot configuration.
     * @return tasks for monitoring the progress each snapshot creation.
     */
    public Tasks<BlockSnapshotRestRep> createForVolume(URI volumeId, VolumeSnapshotParam input) {
        return postTasks(input, getByVolumeUrl(), volumeId);
    }

    /**
     * Gets the URL for listing block snapshots by consistency group:
     * <tt>/block/consistency-groups/{consistencyGroupId}/protection/snapshots</tt>
     * 
     * @return the URL for listing by consistency group.
     */
    protected String getByConsistencyGroupUrl() {
        return PathConstants.BLOCK_CONSISTENCY_GROUP_URL + "/{consistencyGroupId}/protection/snapshots";
    }

    /**
     * Lists the block snapshots for a consistency group by ID.
     * <p>
     * <API Call: <tt>GET /block/consistency-groups/{consistencyGroupId}/protection/snapshots</tt>
     * 
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @return the list of block snapshot references.
     */
    public List<NamedRelatedResourceRep> listByConsistencyGroup(URI consistencyGroupId) {
        return getList(getByConsistencyGroupUrl(), consistencyGroupId);
    }

    /**
     * Gets the block snapshots for a consistency group by ID.
     * 
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @return the list of consistency groups.
     * 
     * @see #listByConsistencyGroup(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<BlockSnapshotRestRep> getByConsistencyGroup(URI consistencyGroupId) {
        return getByConsistencyGroup(consistencyGroupId, null);
    }

    /**
     * Gets the block snapshots for a consistency group by ID, optionally filtering the results.
     * 
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param filter
     *            the filter to apply (may be null, for no filtering).
     * @return the list of consistency groups.
     * 
     * @see #listByConsistencyGroup(URI)
     * @see #getByRefs(java.util.Collection, ResourceFilter)
     */
    public List<BlockSnapshotRestRep> getByConsistencyGroup(URI consistencyGroupId,
            ResourceFilter<BlockSnapshotRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByConsistencyGroup(consistencyGroupId);
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a block snapshot for the given consistency group by ID.
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{consistencyGroupId}/protection/snapshots</tt>
     * 
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param input
     *            the create configuration.
     * @return tasks for monitoring the progress of the block snapshot creation.
     */
    public Tasks<BlockSnapshotRestRep> createForConsistencyGroup(URI consistencyGroupId,
            BlockConsistencyGroupSnapshotCreate input) {
        return postTasks(input, getByConsistencyGroupUrl(), consistencyGroupId);
    }

    /**
     * Gets a particular block snapshot for a given consistency group by ID.
     * <p>
     * API Call: <tt>GET /block/consistency-groups/{consistencyGroupId}/protection/snapshots/{id}</tt>
     * 
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param id
     *            the ID of the block snapshot.
     * @return the block snapshot.
     */
    public BlockSnapshotRestRep getForConsistencyGroup(URI consistencyGroupId, URI id) {
        return client.get(BlockSnapshotRestRep.class, getByConsistencyGroupUrl() + "/{id}", consistencyGroupId, id);
    }

    /**
     * Begins activating a block snapshot for a given consistency group by ID.
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{consistencyGroupId}/protection/snapshots/{id}/activate</tt>
     * 
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param id
     *            the ID of the block snapshot to activate.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<BlockSnapshotRestRep> activateForConsistencyGroup(URI consistencyGroupId, URI id) {
        return postTask(getByConsistencyGroupUrl() + "/{id}/activate", consistencyGroupId, id);
    }

    /**
     * Begins deactivating a block snapshot for a consistency group by ID.
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{consistencyGroupId}/protection/snapshots/{id}/deactivate</tt>
     * 
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param id
     *            the ID of the block snapshot to deactivate.
     * @return tasks for monitoring the progress of the operation.
     */
    public Tasks<BlockSnapshotRestRep> deactivateForConsistencyGroup(URI consistencyGroupId, URI id) {
        return postTasks(getByConsistencyGroupUrl() + "/{id}/deactivate", consistencyGroupId, id);
    }

    /**
     * Begins restoring a block snapshot for a consistency group by ID
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{consistencyGroupId}/protection/snapshots/{id}/restore</tt>
     * 
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param id
     *            the ID of the block snapshot to restore.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<BlockSnapshotRestRep> restoreForConsistencyGroup(URI consistencyGroupId, URI id) {
        return postTask(getByConsistencyGroupUrl() + "/{id}/restore", consistencyGroupId, id);
    }

    /**
     * Creates a new VPLEX volume using the target volume associated with
     * BlockSnapshot instance with the passed id as the source side
     * backend volume for the VPLEX volume.
     * <p>
     * API Call: <tt>POST /block/snapshots/{id}/expose</tt>
     * 
     * @param id the URI of the block snapshot
     * @return a task for monitoring the progress of the operation.
     */
    public Task<BlockSnapshotRestRep> expose(URI id) {
        return postTask(getIdUrl() + "/expose", id);
    }

    public Tasks<BlockSnapshotRestRep> createFullCopy(URI id, VolumeFullCopyCreateParam input) {
        return postTasks(input, getFullCopyUrl(), id);
    }

    /**
     * Gets the base URL for full copies for a single block snapshot: <tt>/block/snapshots/{id}/protection/full-copies</tt>
     * 
     * @return the base full copy URL.
     */
    protected String getFullCopyUrl() {
        return getIdUrl() + "/protection/full-copies";
    }

}
