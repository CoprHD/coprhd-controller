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
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolBulkRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolCreateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolElementUpdateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolList;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolUpdateParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Compute Virtual Pools resource.
 * <p>
 * Base URL: <tt>/compute/vpools</tt>
 */
public class ComputeVirtualPools extends AbstractCoreBulkResources<ComputeVirtualPoolRestRep> implements
        TopLevelResources<ComputeVirtualPoolRestRep>, ACLResources, QuotaResources {
    public ComputeVirtualPools(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ComputeVirtualPoolRestRep.class, PathConstants.COMPUTE_VPOOL_URL);
    }

    @Override
    public ComputeVirtualPools withInactive(boolean inactive) {
        return (ComputeVirtualPools) super.withInactive(inactive);
    }

    @Override
    protected List<ComputeVirtualPoolRestRep> getBulkResources(BulkIdParam input) {
        ComputeVirtualPoolBulkRep response = client.post(ComputeVirtualPoolBulkRep.class, input, getBulkUrl());
        return defaultList(response.getVirtualPools());
    }

    /**
     * Gets a list of virtual pool references from the given url.
     * 
     * @param url
     *            the URL to retrieve.
     * @param args
     *            the arguments for the URL.
     * @return the list of virtual pool references.
     */
    protected List<NamedRelatedResourceRep> getList(String url, Object... args) {
        ComputeVirtualPoolList response = client.get(ComputeVirtualPoolList.class, url, args);
        return ResourceUtils.defaultList(response.getComputeVirtualPool());
    }

    /**
     * Lists all compute virtual pools.
     * <p>
     * API Call: <tt>GET /compute/vpools</tt>
     * 
     * @return the list of compute virtual pool references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        return getList(baseUrl);
    }

    /**
     * Gets all compute virtual pools.
     * 
     * @return the list of compute virtual pools.
     * 
     * @see #list()
     * @see #getByRefs(java.util.Collection)
     */
    @Override
    public List<ComputeVirtualPoolRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets all compute virtual pools, optionally filtering the results.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of compute virtual pools.
     */
    @Override
    public List<ComputeVirtualPoolRestRep> getAll(ResourceFilter<ComputeVirtualPoolRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Creates a new compute virtual pool.
     * <p>
     * API Call: <tt>POST /compute/vpools</tt>
     * 
     * @param input
     *            the create configuration.
     * @return the newly created compute virtual pool.
     */
    public ComputeVirtualPoolRestRep create(ComputeVirtualPoolCreateParam input) {
        return client.post(ComputeVirtualPoolRestRep.class, input, baseUrl);
    }

    /**
     * Updates a compute virtual pool.
     * <p>
     * API Call: <tt>PUT /compute/vpools/{id}</tt>
     * 
     * @param id
     *            the ID of the compute virtual pool to update.
     * @param input
     *            the update configuration.
     * @return the updated compute virtual pool.
     */
    public ComputeVirtualPoolRestRep update(URI id, ComputeVirtualPoolUpdateParam input) {
        return client.put(ComputeVirtualPoolRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates a compute virtual pool.
     * <p>
     * API Call: <tt>POST /compute/vpools/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the compute virtual pool to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    public void deactivate(ComputeVirtualPoolRestRep pool) {
        deactivate(ResourceUtils.id(pool));
    }

    @Override
    public List<ACLEntry> getACLs(URI id) {
        return doGetACLs(id);
    }

    @Override
    public List<ACLEntry> updateACLs(URI id, ACLAssignmentChanges aclChanges) {
        return doUpdateACLs(id, aclChanges);
    }

    @Override
    public QuotaInfo getQuota(URI id) {
        return doGetQuota(id);
    }

    @Override
    public QuotaInfo updateQuota(URI id, QuotaUpdateParam quota) {
        return doUpdateQuota(id, quota);
    }

    public List<NamedRelatedResourceRep> listComputeElements(URI id) {
        // TODO implement
        return defaultList(null);
    }

    /**
     * Lists the compute elements that would match a virtual compute pool with the given configuration.
     * <p>
     * API Call: <tt>POST /compute/vpools/matching-elements</tt>
     * 
     * @param input
     *            the configuration for a potential virtual compute pool.
     * @return the list of matching compute element references.
     */
    public List<ComputeElementRestRep> listMatchingComputeElements(ComputeVirtualPoolCreateParam input) {
        ComputeElementListRestRep response = client.post(ComputeElementListRestRep.class, input, baseUrl + "/matching-compute-elements");
        return defaultList(response.getList());
    }

    /**
     * Updates a compute virtual pool.
     * <p>
     * API Call: <tt>PUT /compute/vpools/{id}/assign-matched-elements</tt>
     * 
     * @param id
     *            the ID of the compute virtual pool to update.
     * @param input
     *            the update configuration.
     * @return the updated compute virtual pool.
     */
    public ComputeVirtualPoolRestRep assignComputeElements(URI id, ComputeVirtualPoolElementUpdateParam input) {
        return client.put(ComputeVirtualPoolRestRep.class, input, getIdUrl() + "/assign-matched-elements", id);
    }

    /**
     * Lists elements of a compute virtual pool.
     * <p>
     * API Call: <tt>GET /compute/vpools/{id}/matched-elements</tt>
     * 
     * @param id
     *            the ID of the compute virtual pool .
     * @return the list of elements from the compute virtual pool.
     */
    public ComputeElementListRestRep getMatchedComputeElements(URI id) {
        return client.get(ComputeElementListRestRep.class, getIdUrl() + "/compute-elements", id);
    }

    /**
     * Get a compute virtual pool.
     * <p>
     * API Call: <tt>GET /compute/vpools/{id}</tt>
     * 
     * @param id
     *            the ID of the compute virtual pool .
     * @return the list of elements from the compute virtual pool.
     */
    public ComputeVirtualPoolRestRep getComputeVirtualPool(URI id) {
        return client.get(ComputeVirtualPoolRestRep.class, getIdUrl(), id);
    }

}
