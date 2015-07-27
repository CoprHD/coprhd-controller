/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockMigrationBulkRep;
import com.emc.storageos.model.block.MigrationList;
import com.emc.storageos.model.block.MigrationRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Block Volume Migration resources.
 * <p>
 * Base URL: <tt>/block/migrations</tt>
 * 
 * @see MigrationRestRep
 */
public class BlockMigrations extends AbstractCoreBulkResources<MigrationRestRep> implements
        TopLevelResources<MigrationRestRep>, TaskResources<MigrationRestRep> {
    public BlockMigrations(ViPRCoreClient parent, RestClient client) {
        super(parent, client, MigrationRestRep.class, PathConstants.MIGRATION_URL);
    }

    @Override
    public BlockMigrations withInactive(boolean inactive) {
        return (BlockMigrations) super.withInactive(inactive);
    }

    @Override
    public BlockMigrations withInternal(boolean internal) {
        return (BlockMigrations) super.withInternal(internal);
    }

    @Override
    protected List<MigrationRestRep> getBulkResources(BulkIdParam input) {
        BlockMigrationBulkRep response = client.post(BlockMigrationBulkRep.class, input, getBulkUrl());
        return defaultList(response.getMigrations());
    }

    @Override
    public Tasks<MigrationRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<MigrationRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Lists all block volume migrations.
     * <p>
     * API Call: <tt>GET /block/migrations</tt>
     * 
     * @return the list of block volume migration references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        MigrationList response = client.get(MigrationList.class, baseUrl);
        return defaultList(response.getMigrations());
    }

    @Override
    public List<MigrationRestRep> getAll() {
        return getAll(null);
    }

    @Override
    public List<MigrationRestRep> getAll(ResourceFilter<MigrationRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Begins pausing a block volume migration.
     * <p>
     * API Call: <tt>POST /block/migrations/{id}/pause</tt>
     * 
     * @param id
     *        the ID of the block volume migration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<MigrationRestRep> pause(URI id) {
        return postTask(getIdUrl() + "/pause", id);
    }

    /**
     * Begins resuming a block volume migration.
     * <p>
     * API Call: <tt>POST /block/migrations/{id}/resume</tt>
     * 
     * @param id
     *        the ID of the block volume migration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<MigrationRestRep> resume(URI id) {
        return postTask(getIdUrl() + "/resume", id);
    }

    /**
     * Begins committing a block volume migration.
     * <p>
     * API Call: <tt>POST /block/migrations/{id}/commit</tt>
     * 
     * @param id
     *        the ID of the block volume migration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<MigrationRestRep> commit(URI id) {
        return postTask(getIdUrl() + "/commit", id);
    }

    /**
     * Begins canceling a block volume migration.
     * <p>
     * API Call: <tt>POST /block/migrations/{id}/cancel</tt>
     * 
     * @param id
     *        the ID of the block volume migration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<MigrationRestRep> cancel(URI id) {
        return postTask(getIdUrl() + "/cancel", id);
    }
}
