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
import com.emc.storageos.model.compute.ComputeImageServerBulkRep;
import com.emc.storageos.model.compute.ComputeImageServerCreate;
import com.emc.storageos.model.compute.ComputeImageServerList;
import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.storageos.model.compute.ComputeImageServerUpdate;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Storage Systems resources.
 * <p>
 * Base URL: <tt>/compute/compute-imageservers</tt>
 */
public class ComputeImageServers extends AbstractCoreBulkResources<ComputeImageServerRestRep> implements
        TopLevelResources<ComputeImageServerRestRep>, TaskResources<ComputeImageServerRestRep> {
    public ComputeImageServers(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ComputeImageServerRestRep.class, PathConstants.COMPUTE_IMAGE_SERVER_URL);
    }

    @Override
    public ComputeImageServers withInactive(boolean inactive) {
        return (ComputeImageServers) super.withInactive(inactive);
    }

    @Override
    protected List<ComputeImageServerRestRep> getBulkResources(BulkIdParam input) {
        ComputeImageServerBulkRep response = client.post(ComputeImageServerBulkRep.class, input, getBulkUrl());
        return defaultList(response.getComputeImageServers());
    }

    @Override
    public Tasks<ComputeImageServerRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<ComputeImageServerRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Lists all compute image servers.
     * <p>
     * API Call: <tt>GET /compute/compute-imageservers</tt>
     * 
     * @return the list of compute image server references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        ComputeImageServerList response = client.get(ComputeImageServerList.class, baseUrl);
        return ResourceUtils.defaultList(response.getComputeImageServers());
    }

    /**
     * Gets the list of all compute image servers. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @return the list of all compute image servers.
     */
    @Override
    public List<ComputeImageServerRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all compute image servers, optionally filtering the results. This is a convenience method for:
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of all compute image servers.
     */
    @Override
    public List<ComputeImageServerRestRep> getAll(ResourceFilter<ComputeImageServerRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a compute image server.
     * <p>
     * API Call: <tt>POST /compute/computeimageservers</tt>
     * 
     * @param input
     *            the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ComputeImageServerRestRep> create(ComputeImageServerCreate input) {
        return postTask(input, baseUrl);
    }

    /**
     * Begins updating the given compute image servers by ID.
     * <p>
     * API Call: <tt>PUT /compute/compute-imageservers/{id}</tt>
     * 
     * @param id
     *            the ID of the compute image server.
     * @param input
     *            the update configuration.
     * @return ComputeImageServerRestRep.
     */
    public ComputeImageServerRestRep update(URI id, ComputeImageServerUpdate input) {
        return client.put(ComputeImageServerRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deletes the given compute image server by ID.
     * <p>
     * API Call: <tt>POST /compute-imageservers/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the compute image server to delete.
     * @return Task<ComputeImageServerRestRep> the task to delete the compute image server.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

}
