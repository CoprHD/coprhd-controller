/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.block.BlockConsistencyGroupBulkRep;
import com.emc.storageos.model.block.BlockConsistencyGroupCreate;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate;
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
     * Creates a block consistency group.
     * <p>
     * API Call: <tt>POST /block/consistency-groups</tt>
     * 
     * @param input
     *        the create configuration.
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
     *        the ID of the block consistency group to update.
     * @param input
     *        the update configuration.
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
     *        the ID of the block consistency group to deactivate.
     * @return a task for monitoring the progres of the deactivate operation.
     * 
     * @see #doDeactivateWithTask(URI)
     */
    public Task<BlockConsistencyGroupRestRep> deactivate(URI id) {
        return doDeactivateWithTask(id);
    }
}