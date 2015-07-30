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
import com.emc.storageos.model.protection.ProtectionSystemBulkRep;
import com.emc.storageos.model.protection.ProtectionSystemConnectivityRestRep;
import com.emc.storageos.model.protection.ProtectionSystemList;
import com.emc.storageos.model.protection.ProtectionSystemRequestParam;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;
import com.emc.storageos.model.protection.ProtectionSystemUpdateRequestParam;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Protection Systems resources.
 * <p>
 * Base URL: <tt>/vdc/protection-systems</tt>
 */
public class ProtectionSystems extends AbstractCoreBulkResources<ProtectionSystemRestRep> implements
        TopLevelResources<ProtectionSystemRestRep>, TaskResources<ProtectionSystemRestRep> {
    public ProtectionSystems(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ProtectionSystemRestRep.class, PathConstants.PROTECTION_SYSTEM_URL);
    }

    @Override
    public ProtectionSystems withInactive(boolean inactive) {
        return (ProtectionSystems) super.withInactive(inactive);
    }

    @Override
    public ProtectionSystems withInternal(boolean internal) {
        return (ProtectionSystems) super.withInternal(internal);
    }

    @Override
    protected List<ProtectionSystemRestRep> getBulkResources(BulkIdParam input) {
        ProtectionSystemBulkRep response = client.post(ProtectionSystemBulkRep.class, input, getBulkUrl());
        return defaultList(response.getProtectionSystems());
    }

    @Override
    public Tasks<ProtectionSystemRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<ProtectionSystemRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Lists all protection systems.
     * <p>
     * API Call: <tt>GET /vdc/protection-systems</tt>
     * 
     * @return the list of protection system references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        ProtectionSystemList response = client.get(ProtectionSystemList.class, baseUrl);
        return defaultList(response.getSystems());
    }

    /**
     * Get all protection systems. Convenience method for: <tt>getByRefs(list())</tt>
     * 
     * @return the list of protection systems.
     */
    @Override
    public List<ProtectionSystemRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Get all protection systems, optionally filtering the results.. Convenience method for: <tt>getByRefs(list(), filter)</tt>
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of protection systems.
     */
    @Override
    public List<ProtectionSystemRestRep> getAll(ResourceFilter<ProtectionSystemRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a protection system.
     * <p>
     * API Call: <tt>POST /vdc/protection-systems</tt>
     * 
     * @param input
     *            the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ProtectionSystemRestRep> create(ProtectionSystemRequestParam input) {
        return postTask(input, baseUrl);
    }

    /**
     * Begins updating a protection system by ID.
     * <p>
     * API Call: <tt>PUT /vdc/protection-systems/{id}</tt>
     * 
     * @param id
     *            the ID of the protection system.
     * @param input
     *            the update configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ProtectionSystemRestRep> update(URI id, ProtectionSystemUpdateRequestParam input) {
        return putTask(input, getIdUrl(), id);
    }

    /**
     * Deactivates a protection system.
     * <p>
     * API Call: <tt>POST /vdc/protection-systems/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the protection system.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    /**
     * Begins discovery on all protection systems.
     * <p>
     * API Call: <tt>POST /vdc/protection-systems/discover</tt>
     * 
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<ProtectionSystemRestRep> discoverAll() {
        return postTasks(baseUrl + "/discover");
    }

    /**
     * Begins discovery on the given protection system by ID.
     * <p>
     * API Call: <tt>POST /vdc/protection-systems/{id}/discover</tt>
     * 
     * @param id
     *            the ID of the protection system.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ProtectionSystemRestRep> discover(URI id) {
        return postTask(getIdUrl() + "/discover", id);
    }

    /**
     * Gets the protection system connectivity for the given protection system.
     * <p>
     * API Call: <tt>POST /vdc/protection-systems/{id}/connectivity</tt>
     * 
     * @param id
     *            the ID of the protection system.
     * @return the connectivity of the protection system.
     */
    public ProtectionSystemConnectivityRestRep getConnectivity(URI id) {
        return client.get(ProtectionSystemConnectivityRestRep.class, getIdUrl() + "/connectivity", id);
    }
}
