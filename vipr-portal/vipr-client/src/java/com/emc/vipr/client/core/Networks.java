/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.PathConstants.ID_URL_FORMAT;
import static com.emc.vipr.client.core.impl.PathConstants.VARRAY_URL;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.network.NetworkBulkRep;
import com.emc.storageos.model.varray.NetworkCreate;
import com.emc.storageos.model.varray.NetworkEndpointParam;
import com.emc.storageos.model.varray.NetworkList;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.varray.NetworkUpdate;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Networks resources.
 * <p>
 * Base URL: <tt>/vdc/networks</tt>
 */
public class Networks extends AbstractCoreBulkResources<NetworkRestRep> implements TopLevelResources<NetworkRestRep> {
    public Networks(ViPRCoreClient parent, RestClient client) {
        super(parent, client, NetworkRestRep.class, PathConstants.NETWORK_URL);
    }

    @Override
    public Networks withInactive(boolean inactive) {
        return (Networks) super.withInactive(inactive);
    }

    @Override
    public Networks withInternal(boolean internal) {
        return (Networks) super.withInternal(internal);
    }

    @Override
    protected List<NetworkRestRep> getBulkResources(BulkIdParam input) {
        NetworkBulkRep response = client.post(NetworkBulkRep.class, input, getBulkUrl());
        return defaultList(response.getNetworks());
    }

    /**
     * Lists all networks.
     * <p>
     * API Call: <tt>GET /vdc/networks</tt>
     * 
     * @return the list of network references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        NetworkList response = client.get(NetworkList.class, baseUrl);
        return ResourceUtils.defaultList(response.getNetworks());
    }

    /**
     * Gets the list of all networks. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @return the list of all networks.
     */
    @Override
    public List<NetworkRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all networks, optionally filtering the results. This is a convenience method for: <tt>getByRefs(list(), filter)</tt>
     * .
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of all networks.
     */
    @Override
    public List<NetworkRestRep> getAll(ResourceFilter<NetworkRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Lists the networks that are associated with the given virtual array.
     * <p>
     * API Call: <tt>GET /vdc/varrays/{id}/networks</tt>
     * 
     * @param varrayId
     *            the ID of the virtual array.
     * @return the list of network references.
     */
    public List<NamedRelatedResourceRep> listByVirtualArray(URI varrayId) {
        NetworkList response = client.get(NetworkList.class, String.format(ID_URL_FORMAT, VARRAY_URL) + "/networks", varrayId);
        return defaultList(response.getNetworks());
    }

    /**
     * Gets the networks that are associated with the given virtual array.
     * Convenience method for calling getByRefs(listByVirtualArray(varrayId)).
     * 
     * @param varrayId
     *            the ID of the virtual array.
     * @return the list of networks.
     * 
     * @see #listByVirtualArray(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<NetworkRestRep> getByVirtualArray(URI varrayId) {
        return getByVirtualArray(varrayId, null);
    }

    /**
     * Gets the networks that are associated with the given virtual array.
     * Convenience method for calling getByRefs(listByVirtualArray(varrayId)).
     * 
     * @param varrayId
     *            the ID of the virtual array.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * 
     * @return the list of networks.
     * 
     * @see #listByVirtualArray(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<NetworkRestRep> getByVirtualArray(URI varrayId, ResourceFilter<NetworkRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByVirtualArray(varrayId);
        return getByRefs(refs, filter);
    }

    /**
     * Creates a network.
     * <p>
     * API Call: <tt>POST /vdc/networks</tt>
     * 
     * @param input
     *            the create configuration.
     * @return the newly created network.
     */
    public NetworkRestRep create(NetworkCreate input) {
        return client.post(NetworkRestRep.class, input, PathConstants.NETWORK_URL);
    }

    /**
     * Creates a network in the given virtual array.
     * <p>
     * API Call: <tt>POST /vdc/varrays/{virtualArrayId}/networks</tt>
     * 
     * @param virtualArrayId
     *            the ID of the virtual array.
     * @param input
     *            the create configuration.
     * @return the newly created network.
     * 
     * @deprecated use {@link #create(NetworkCreate)} instead.
     */
    @Deprecated
    public NetworkRestRep create(URI virtualArrayId, NetworkCreate input) {
        return client.post(NetworkRestRep.class, input, PathConstants.NETWORK_BY_VARRAY_URL, virtualArrayId);
    }

    /**
     * Updates the given network by ID.
     * <p>
     * API Call: <tt>PUT /vdc/networks/{id}</tt>
     * 
     * @param id
     *            the ID of the network to update.
     * @param input
     *            the update configuration.
     * @return the updated network.
     */
    public NetworkRestRep update(URI id, NetworkUpdate input) {
        return client.put(NetworkRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates a network by ID.
     * <p>
     * API Call: <tt>POST /vdc/networks/{id}/deactivate?force=false</tt>
     * 
     * @param id
     *            the ID of the network to deactivate.
     */
    public void deactivate(URI id) {
        deactivate(id, false);
    }

    /**
     * Deactivates a network by ID.
     * <p>
     * API Call: <tt>POST /vdc/networks/{id}/deactivate?force={force}</tt>
     * 
     * @param id
     *            the ID of the network to deactivate.
     * @param force
     *            if true, will delete manually created network.
     */
    public void deactivate(URI id, boolean force) {
        URI deactivateUri = client.uriBuilder(getDeactivateUrl()).queryParam("force", force).build(id);
        client.postURI(String.class, deactivateUri);
    }

    public void deactivate(NetworkRestRep value) {
        deactivate(ResourceUtils.id(value));
    }

    /**
     * Registers the given network by ID.
     * <p>
     * API Call: <tt>POST /vdc/networks/{id}/register</tt>
     * 
     * @param id
     *            the ID of the network to register.
     */
    public NetworkRestRep register(URI id) {
        return client.post(NetworkRestRep.class, getIdUrl() + "/register", id);
    }

    /**
     * Deregisters the given network by ID.
     * <p>
     * API Call: <tt>POST /vdc/networks/{id}/deregister</tt>
     * 
     * @param id
     *            the ID of the network to deregister.
     */
    public NetworkRestRep deregister(URI id) {
        return client.post(NetworkRestRep.class, getIdUrl() + "/deregister", id);
    }

    /**
     * Updates an endpoint for the given network.
     * <p>
     * API Call: <tt>PUT /vdc/networks/{id}/endpoints</tt>
     * 
     * @param id
     *            the ID of the network.
     * @param input
     *            the endpoint configuration.
     * @return the updated network.
     * @deprecated Use main update() call to update endpoints
     * @see #update(java.net.URI, com.emc.storageos.model.varray.NetworkUpdate)
     */
    @Deprecated
    public NetworkRestRep updateEndpoints(URI id, NetworkEndpointParam input) {
        return client.put(NetworkRestRep.class, input, getIdUrl() + "/endpoints", id);
    }
}
