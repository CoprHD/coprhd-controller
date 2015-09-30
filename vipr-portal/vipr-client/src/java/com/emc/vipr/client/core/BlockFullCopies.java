/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.block.VolumeBulkRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class BlockFullCopies extends ProjectResources<VolumeRestRep> implements TaskResources<VolumeRestRep> {

    public BlockFullCopies(ViPRCoreClient parent, RestClient client) {
        super(parent, client, VolumeRestRep.class, PathConstants.BLOCK_FULL_COPIES_URL);
    }

    @Override
    public BlockFullCopies withInactive(boolean inactive) {
        return (BlockFullCopies) super.withInactive(inactive);
    }

    @Override
    public BlockFullCopies withInternal(boolean internal) {
        return (BlockFullCopies) super.withInternal(internal);
    }

    @Override
    protected List<VolumeRestRep> getBulkResources(BulkIdParam input) {
        VolumeBulkRep response = client.post(VolumeBulkRep.class, input, getBulkUrl());
        return defaultList(response.getVolumes());
    }

    @Override
    public Tasks<VolumeRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<VolumeRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Begins activating a full copy of the given block volume.
     * <p>
     * API Call: <tt>POST /block/full-copies/{id}/activate</tt>
     * 
     * @param copyId
     *            the ID of the full copy to activate.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> activateFullCopy(URI copyId) {
        return postTasks(getIdUrl() + "/activate", copyId);
    }

    /**
     * Begins restore from a full copy of the given block volume.
     * <p>
     * API Call: <tt>POST /block/full-copies/{id}/restore</tt>
     * 
     * @param copyId
     *            the ID of the full copy to restore from.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> restoreFromFullCopy(URI copyId) {
        return postTasks(getIdUrl() + "/restore", copyId);
    }

    /**
     * Begins resynchronizing a full copy of the given block volume.
     * <p>
     * API Call: <tt>POST /block/full-copies/{id}/resynchronize</tt>
     * 
     * @param copyId
     *            the ID of the full copy to resynchronize.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> resynchronizeFullCopy(URI copyId) {
        return postTasks(getIdUrl() + "/resynchronize", copyId);
    }

    /**
     * Begins detach a full copy of the given block volume.
     * <p>
     * API Call: <tt>POST /block/full-copies/{id}/detach</tt>
     * 
     * @param copyId
     *            the ID of the full copy to detach.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> detachFullCopy(URI copyId) {
        return postTasks(getIdUrl() + "/detach", copyId);
    }

    /**
     * Begins creating group synchronization between
     * volume group and full copy group.
     * <p>
     * API Call: <tt>POST /block/full-copies/{id}/start</tt>
     * 
     * @param copyId
     *            the ID of the full copy.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> startFullCopy(URI copyId) {
        return postTasks(getIdUrl() + "/start", copyId);
    }

    /**
     * Gets the full copy volume with an updated value of the progress of the operation. The progress can be retrieved
     * from: <tt>volume.getProtection().getFullCopy().getPercentSynced()</tt>
     * <p>
     * API Call: <tt>POST /block/full-copies/{id}/check-progress</tt>
     * 
     * @param copyId
     *            the ID of the full copy.
     * @return the full copy volume.
     * 
     * @see VolumeRestRep.FullCopyRestRep#getPercentSynced()
     */
    public VolumeRestRep checkFullCopyProgress(URI copyId) {
        return client.post(VolumeRestRep.class, getIdUrl() + "/check-progress", copyId);
    }
}
