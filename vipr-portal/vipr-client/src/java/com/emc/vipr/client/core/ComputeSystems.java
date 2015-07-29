/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemBulkRep;
import com.emc.storageos.model.compute.ComputeSystemCreate;
import com.emc.storageos.model.compute.ComputeSystemList;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.compute.ComputeSystemUpdate;
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
 * Base URL: <tt>/vdc/compute-systems</tt>
 */
public class ComputeSystems extends AbstractCoreBulkResources<ComputeSystemRestRep> implements
        TopLevelResources<ComputeSystemRestRep>, TaskResources<ComputeSystemRestRep> {
    public ComputeSystems(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ComputeSystemRestRep.class, PathConstants.COMPUTE_SYSTEMS_URL);
    }

    @Override
    public ComputeSystems withInactive(boolean inactive) {
        return (ComputeSystems) super.withInactive(inactive);
    }

    @Override
    protected List<ComputeSystemRestRep> getBulkResources(BulkIdParam input) {
        ComputeSystemBulkRep response = client.post(ComputeSystemBulkRep.class, input, getBulkUrl());
        return defaultList(response.getComputeSystems());
    }

    @Override
    public Tasks<ComputeSystemRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<ComputeSystemRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Lists all compute systems.
     * <p>
     * API Call: <tt>GET /vdc/compute-systems</tt>
     * 
     * @return the list of compute system references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        ComputeSystemList response = client.get(ComputeSystemList.class, baseUrl);
        return ResourceUtils.defaultList(response.getComputeSystems());
    }

    /**
     * Gets the list of all compute systems. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @return the list of all compute systems.
     */
    @Override
    public List<ComputeSystemRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all compute systems, optionally filtering the results. This is a convenience method for:
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of all compute systems.
     */
    @Override
    public List<ComputeSystemRestRep> getAll(ResourceFilter<ComputeSystemRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a compute system.
     * <p>
     * API Call: <tt>POST /vdc/compute-systems</tt>
     * 
     * @param input
     *            the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ComputeSystemRestRep> create(ComputeSystemCreate input) {
        return postTask(input, baseUrl);
    }

    /**
     * Begins updating the given compute system by ID.
     * <p>
     * API Call: <tt>PUT /vdc/compute-systems/{id}</tt>
     * 
     * @param id
     *            the ID of the compute system.
     * @param input
     *            the update configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ComputeSystemRestRep> update(URI id, ComputeSystemUpdate input) {
        return putTask(input, getIdUrl(), id);
    }

    /**
     * Begins deactivating the given compute system by ID.
     * <p>
     * API Call: <tt>POST /vdc/compute-systems/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the compute system.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ComputeSystemRestRep> deactivate(URI id) {
        return doDeactivateWithTask(id);
    }

    /**
     * Begins discovery on all compute systems.
     * <p>
     * API Call: <tt>POST /vdc/compute-systems/discover</tt>
     * 
     * @return Tasks for monitoring the progress of the operation(s).
     */
    public Tasks<ComputeSystemRestRep> discoverAll() {
        return postTasks(baseUrl + "/discover");
    }

    /**
     * Begins discovery on the given compute system.
     * <p>
     * API Call: <tt>POST /vdc/compute-systems/{id}/discover</tt>
     * 
     * @param id
     *            the ID of the compute system.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ComputeSystemRestRep> discover(URI id) {
        UriBuilder builder = client.uriBuilder(getIdUrl() + "/discover");
        return postTaskURI(builder.build(id));
    }

    /**
     * Registers the given storage system by ID.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{id}/register</tt>
     * 
     * @param id
     *            the ID of the storage system.
     * @return the updated storage system.
     */
    public ComputeSystemRestRep register(URI id) {
        return client.post(ComputeSystemRestRep.class, getIdUrl() + "/register", id);
    }

    /**
     * De-registers the given storage system by ID.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{id}/deregister</tt>
     * 
     * @param id
     *            the ID of the storage system.
     * @return the updated storage system.
     */
    public ComputeSystemRestRep deregister(URI id) {
        return client.post(ComputeSystemRestRep.class, getIdUrl() + "/deregister", id);
    }

    /**
     * Gets the list of Compute Elements in the given Compute System.
     * <p>
     * API Call: <tt>GET /vdc/compute-systems/{id}/compute-elements</tt>
     * 
     * @param id
     *            the ID of the compute system.
     * @return the list of Compute Elements.
     */
    public List<ComputeElementRestRep> getComputeElements(URI id) {
        ComputeElementListRestRep response = client.get(ComputeElementListRestRep.class, getIdUrl() + "/compute-elements", id);
        return defaultList(response.getList());
    }
}
