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
import com.emc.storageos.model.block.BlockConsistencyGroupBulkRep;
import com.emc.storageos.model.block.BlockConsistencyGroupCreate;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupSnapshotCreate;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Block Consistency Group resources.
 * <p>
 * Base URL: <tt>/block/consistency-groups</tt>
 *
 * @see BlockConsistencyGroupRestRep
 */
public class BlockConsistencyGroups extends ProjectResources<BlockConsistencyGroupRestRep> implements
        TaskResources<BlockConsistencyGroupRestRep> {
    public BlockConsistencyGroups(ViPRCoreClient parent, RestClient client) {
        super(parent, client, BlockConsistencyGroupRestRep.class, PathConstants.BLOCK_CONSISTENCY_GROUP_URL);
    }

    @Override
    public BlockConsistencyGroups withInactive(boolean inactive) {
        return (BlockConsistencyGroups) super.withInactive(inactive);
    }

    @Override
    public BlockConsistencyGroups withInternal(boolean internal) {
        return (BlockConsistencyGroups) super.withInternal(internal);
    }

    @Override
    protected List<BlockConsistencyGroupRestRep> getBulkResources(BulkIdParam input) {
        BlockConsistencyGroupBulkRep response = client.post(BlockConsistencyGroupBulkRep.class, input, getBulkUrl());
        return defaultList(response.getConsistencyGroups());
    }

    @Override
    public Tasks<BlockConsistencyGroupRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<BlockConsistencyGroupRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Begins creating a full copy of the given block volume.
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/full-copies</tt>
     *
     * @param id
     *            the ID of the consistency group.
     * @param input
     *            the create configuration.
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<BlockConsistencyGroupRestRep> createFullCopy(URI consistencyGroupId, VolumeFullCopyCreateParam input) {
        final String url = getIdUrl() + "/protection/full-copies";
        return postTasks(input, url, consistencyGroupId);
    }

    /**
     * List full copies for a consistency group
     * <p>
     * API Call: <tt>GET /block/consistency-groups/{id}/protection/full-copies</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group
     * @return The list of full copies for the consistency group
     */
    public List<NamedRelatedResourceRep> getFullCopies(URI consistencyGroupId) {
        final String url = getIdUrl() + "/protection/full-copies";
        NamedVolumesList response = client.get(NamedVolumesList.class, url, consistencyGroupId);
        return defaultList(response.getVolumes());
    }

    /**
     * Activate consistency group full copy
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/full-copies/{fcid}/activate</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param fullCopyId
     *            the Id of the full copy
     * @return An asychronous operation realized as a <code>Tasks</code> object
     */
    public Tasks<BlockConsistencyGroupRestRep> activateFullCopy(URI consistencyGroupId, URI fullCopyId) {
        final String url = getIdUrl() + "/protection/full-copies/{fcid}/activate";
        return postTasks(url, consistencyGroupId, fullCopyId);
    }

    /**
     * Detach consistency group full copy
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/full-copies/{fcid}/detach</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param fullCopyId
     *            the Id of the full copy
     * @return An asychronous operation realized as a <code>Tasks</code> object
     */
    public Tasks<BlockConsistencyGroupRestRep> detachFullCopy(URI consistencyGroupId, URI fullCopyId) {
        final String url = getIdUrl() + "/protection/full-copies/{fcid}/detach";
        return postTasks(url, consistencyGroupId, fullCopyId);
    }

    /**
     * Restore consistency group full copy
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/full-copies/{fcid}/restore</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param fullCopyId
     *            the Id of the full copy
     * @return An asychronous operation realized as a <code>Tasks</code> object
     */
    public Tasks<BlockConsistencyGroupRestRep> restoreFullCopy(URI consistencyGroupId, URI fullCopyId) {
        final String url = getIdUrl() + "/protection/full-copies/{fcid}/restore";
        return postTasks(url, consistencyGroupId, fullCopyId);
    }

    /**
     * Resynchronize consistency group full copy
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/full-copies/{fcid}/resynchronize</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param fullCopyId
     *            the Id of the full copy
     * @return An asychronous operation realized as a <code>Tasks</code> object
     */
    public Tasks<BlockConsistencyGroupRestRep> resynchronizeFullCopy(URI consistencyGroupId, URI fullCopyId) {
        final String url = getIdUrl() + "/protection/full-copies/{fcid}/resynchronize";
        return postTasks(url, consistencyGroupId, fullCopyId);
    }

    /**
     * Deactivate consistency group full copy
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/full-copies/{fcid}/deactivate</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group.
     * @param fullCopyId
     *            the Id of the full copy
     * @return An asychronous operation realized as a <code>Tasks</code> object
     */
    public Tasks<BlockConsistencyGroupRestRep> deactivateFullCopy(URI consistencyGroupId, URI fullCopyId) {
        final String url = getIdUrl() + "/protection/full-copies/{fcid}/deactivate";
        return postTasks(url, consistencyGroupId, fullCopyId);
    }

    /**
     * List snapshots in the consistency group
     * <p>
     * API Call: <tt>GET /block/consistency-groups/{id}/protection/snapshots</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group
     * @return The list of snapshots in the consistency group
     */
    public List<NamedRelatedResourceRep> getSnapshots(URI consistencyGroupId) {
        final String url = getIdUrl() + "/protection/snapshots";
        SnapshotList response = client.get(SnapshotList.class, url, consistencyGroupId);
        return response.getSnapList();
    }

    /**
     * Create consistency group snapshot
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/snapshots</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group
     * @param input
     *            the create snapshot specification
     * @return An asychronous operation realized as a <code>Tasks</code> object
     */
    public Tasks<BlockConsistencyGroupRestRep> createSnapshot(URI consistencyGroupId, BlockConsistencyGroupSnapshotCreate input) {
        final String url = getIdUrl() + "/protection/snapshots";
        return postTasks(input, url, consistencyGroupId);
    }

    /**
     * Activate consistency group snapshot
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/snapshots/{sid}/activate</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group
     * @param snapshotId
     *            the ID of the snapshot
     * @return An asychronous operation realized as a <code>Task</code> object
     */
    public Task<BlockConsistencyGroupRestRep> activateSnapshot(URI consistencyGroupId, URI snapshotId) {
        final String url = getIdUrl() + "/protection/snapshots/{fcid}/activate";
        return postTask(url, consistencyGroupId, snapshotId);
    }

    /**
     * Deactivate consistency group snapshot
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/snapshots/{sid}/deactivate</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group
     * @param snapshotId
     *            the ID of the snapshot
     * @return An asychronous operation realized as a <code>Tasks</code> object
     */
    public Tasks<BlockConsistencyGroupRestRep> deactivateSnapshot(URI consistencyGroupId, URI snapshotId) {
        final String url = getIdUrl() + "/protection/snapshots/{fcid}/deactivate";
        return postTasks(url, consistencyGroupId, snapshotId);
    }

    /**
     * Restore consistency group snapshot
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/snapshots/{sid}/restore</tt>
     *
     * @param consistencyGroupId
     *            the ID of the consistency group
     * @param snapshotId
     *            the ID of the snapshot
     * @return An asychronous operation realized as a <code>Task</code> object
     */
    public Task<BlockConsistencyGroupRestRep> restoreSnapshot(URI consistencyGroupId, URI snapshotId) {
        final String url = getIdUrl() + "/protection/snapshots/{fcid}/restore";
        return postTask(url, consistencyGroupId, snapshotId);
    }

    /**
     * Begins initiating failover for a given consistency group.
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/continuous-copies/failover</tt>
     *
     * @param id
     *            the ID of the consistency group.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<BlockConsistencyGroupRestRep> failover(URI consistencyGroupId, CopiesParam input) {
        final String url = getIdUrl() + "/protection/continuous-copies/failover";
        return postTasks(input, url, consistencyGroupId);
    }

    /**
     * Begins initiating failover cancel for a given consistency group.
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/continuous-copies/failover-cancel</tt>
     *
     * @param id
     *            the ID of the consistency group.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<BlockConsistencyGroupRestRep> failoverCancel(URI consistencyGroupId, CopiesParam input) {
        final String url = getIdUrl() + "/protection/continuous-copies/failover-cancel";
        return postTasks(input, url, consistencyGroupId);
    }

    /**
     * Begins initiating swap for a given consistency group.
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/protection/continuous-copies/swap</tt>
     *
     * @param id
     *            the ID of the consistency group.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<BlockConsistencyGroupRestRep> swap(URI consistencyGroupId, CopiesParam input) {
        final String url = getIdUrl() + "/protection/continuous-copies/swap";
        return postTasks(input, url, consistencyGroupId);
    }

    /**
     * Creates a block consistency group.
     * <p>
     * API Call: <tt>POST /block/consistency-groups</tt>
     *
     * @param input
     *            the create configuration.
     * @return the created block consistency group.
     */
    public BlockConsistencyGroupRestRep create(BlockConsistencyGroupCreate input) {
        return client.post(BlockConsistencyGroupRestRep.class, input, baseUrl);
    }

    /**
     * Begins updating a block consistency group.
     * <p>
     * API Call: <tt>PUT /block/consistency-groups/{id}</tt>
     *
     * @param id
     *            the ID of the block consistency group to update.
     * @param input
     *            the update configuration.
     * @return a task for monitoring the progress of the update operation.
     */
    public Task<BlockConsistencyGroupRestRep> update(URI id, BlockConsistencyGroupUpdate input) {
        return putTask(input, getIdUrl(), id);
    }

    /**
     * Begins deactivating a block consistency group.
     * <p>
     * API Call: <tt>POST /block/consistency-groups/{id}/deactivate</tt>
     *
     * @param id
     *            the ID of the block consistency group to deactivate.
     * @return a task for monitoring the progres of the deactivate operation.
     *
     * @see #doDeactivateWithTask(URI)
     */
    public Task<BlockConsistencyGroupRestRep> deactivate(URI id) {
        return doDeactivateWithTask(id);
    }
}