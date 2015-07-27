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
import com.emc.storageos.model.compute.ComputeElementBulkRep;
import com.emc.storageos.model.compute.ComputeElementList;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Compute Elements resources.
 * <p>
 * Base URL: <tt>/vdc/compute-elements</tt>
 */
public class ComputeElements extends AbstractCoreBulkResources<ComputeElementRestRep> implements
        TopLevelResources<ComputeElementRestRep> {
    public ComputeElements(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ComputeElementRestRep.class, PathConstants.COMPUTE_ELEMENTS_URL);
    }

    @Override
    public ComputeElements withInactive(boolean inactive) {
        return (ComputeElements) super.withInactive(inactive);
    }

    @Override
    protected List<ComputeElementRestRep> getBulkResources(BulkIdParam input) {
    	ComputeElementBulkRep response = client.post(ComputeElementBulkRep.class, input, getBulkUrl());
        return defaultList(response.getComputeElements());
    }

    /**
     * Lists all compute elements.
     * <p>
     * API Call: <tt>GET /vdc/compute-elements</tt>
     * 
     * @return the list of all compute element references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
    	ComputeElementList response = client.get(ComputeElementList.class, baseUrl);
        return defaultList(response.getComputeElements());
    }

    /**
     * Gets all compute elements. This is a convenience method for: <tt>getByRefs(list())</tt>
     * 
     * @return the list of all compute elements.
     */
    @Override
    public List<ComputeElementRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets all compute elements. This is a convenience method for: <tt>getByRefs(list(), filter)</tt>
     * 
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the list of all compute elements.
     */
    @Override
    public List<ComputeElementRestRep> getAll(ResourceFilter<ComputeElementRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Rediscover a compute element.
     * <p>
     * API Call: <tt>POST /vdc/compute-elements/{id}/discover</tt>
     * 
     * @param id
     *        the ID of the compute element.
     */
    public ComputeElementRestRep rediscover(URI id) {
        return client.post(ComputeElementRestRep.class, getIdUrl() + "/discover", id);
    }  
    
    /**
     * Registers a compute element with the given compute system.
     * <p>
     * API Call: <tt>POST /vdc/compute-systems/{computeSystemId}/compute-elements/{computeElementId}/register</tt>
     * 
     * @param computeElementId
     *        the ID of the compute element.
     * @param computeSystemId
     *        the ID of the compute system.
     * @return the compute element.
     */
    public ComputeElementRestRep register(URI computeElementId) {
        return client.post(ComputeElementRestRep.class, getIdUrl() + "/register", computeElementId);
    }

    /**
     * Deregisters a compute element.
     * <p>
     * API Call: <tt>POST /vdc/compute-elements/{id}/deregister</tt>
     * 
     * @param id
     *        the ID of the compute element.
     */
    public ComputeElementRestRep deregister(URI id) {
        return client.post(ComputeElementRestRep.class, getIdUrl() + "/deregister", id);
    }

}
