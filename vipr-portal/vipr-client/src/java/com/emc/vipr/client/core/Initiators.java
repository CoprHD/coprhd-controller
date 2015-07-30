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
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.block.export.ITLRestRepList;
import com.emc.storageos.model.host.InitiatorBulkRep;
import com.emc.storageos.model.host.InitiatorCreateParam;
import com.emc.storageos.model.host.InitiatorList;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.InitiatorUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Initiators resources.
 * <p>
 * Base URL: <tt>/compute/initiators</tt>
 */
public class Initiators extends AbstractCoreBulkResources<InitiatorRestRep> {
    public Initiators(ViPRCoreClient parent, RestClient client) {
        super(parent, client, InitiatorRestRep.class, PathConstants.INITIATOR_URL);
    }

    @Override
    public Initiators withInactive(boolean inactive) {
        return (Initiators) super.withInactive(inactive);
    }

    @Override
    public Initiators withInternal(boolean internal) {
        return (Initiators) super.withInternal(internal);
    }

    @Override
    protected List<InitiatorRestRep> getBulkResources(BulkIdParam input) {
        InitiatorBulkRep response = client.post(InitiatorBulkRep.class, input, getBulkUrl());
        return defaultList(response.getInitiators());
    }

    /**
     * Gets a list of initiators from the given URL.
     * 
     * @param url
     *            the URL to get.
     * @param args
     *            the URL arguments.
     * @return the list of initiator references.
     */
    protected List<NamedRelatedResourceRep> getList(String url, Object... args) {
        InitiatorList response = client.get(InitiatorList.class, url, args);
        return ResourceUtils.defaultList(response.getInitiators());
    }

    /**
     * Creates an initiator for the given host.
     * <p>
     * API Call: <tt>POST /compute/hosts/{hostId}/initiators</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @param input
     *            the initiator configuration.
     * @return a task for monitoring the progress of the initiator creation.
     */
    public Task<InitiatorRestRep> create(URI hostId, InitiatorCreateParam input) {
        return postTask(input, PathConstants.INITIATOR_BY_HOST_URL, hostId);
    }

    /**
     * Updates an initiator.
     * <p>
     * API Call: <tt>PUT /compute/initiators/{id}</tt>
     * 
     * @param id
     *            the ID of the initiator.
     * @param input
     *            the updated configuration.
     * @return the updated initiator.
     */
    public InitiatorRestRep update(URI id, InitiatorUpdateParam input) {
        return client.put(InitiatorRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given initiator by ID.
     * <p>
     * API Call: <tt>POST /compute/initiators/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the initiator.
     * @return a task for monitoring the progress of the initiator de-activation.
     */
    public Task<InitiatorRestRep> deactivate(URI id) {
        return doDeactivateWithTask(id);
    }

    /**
     * Gets the exports associated with the given initiator.
     * <p>
     * API Call: <tt>GET /compute/initiators/{id}/exports</tt>
     * 
     * @param id
     *            the ID of the initiator.
     * @return the list of exports for the initiator.
     */
    public List<ITLRestRep> getExports(URI id) {
        ITLRestRepList response = client.get(ITLRestRepList.class, getIdUrl() + "/exports", id);
        return defaultList(response.getExportList());
    }

    /**
     * Lists the initiators for the given host.
     * <p>
     * API Call: <tt>GET /compute/hosts/{hostId}/initiators</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of initiator references.
     */
    public List<NamedRelatedResourceRep> listByHost(URI hostId) {
        return getList(PathConstants.INITIATOR_BY_HOST_URL, hostId);
    }

    /**
     * Gets a list of initiators for the given host.
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of initiators.
     */
    public List<InitiatorRestRep> getByHost(URI hostId) {
        return getByHost(hostId, null);
    }

    /**
     * Gets the list of initiators for the given host, optionally filtering the results.
     * 
     * @param hostId
     *            the ID of the host.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of initiators.
     */
    public List<InitiatorRestRep> getByHost(URI hostId, ResourceFilter<InitiatorRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByHost(hostId);
        return getByRefs(refs, filter);
    }

    /**
     * Lists the initiators in the given network.
     * <p>
     * API Call: <tt>GET /vdc/networks/{networkId}/initiators</tt>
     * 
     * @param networkId
     *            the ID of the network.
     * @return the list of initiator references.
     */
    public List<NamedRelatedResourceRep> listByNetwork(URI networkId) {
        return getList(PathConstants.INITIATORS_BY_NETWORK_URL, networkId);
    }

    /**
     * Gets the list of initiators in the given network.
     * 
     * @param networkId
     *            the ID of the network.
     * @return the list of initiators.
     */
    public List<InitiatorRestRep> getByNetwork(URI networkId) {
        return getByNetwork(networkId, null);
    }

    /**
     * Gets the list of initiators in the given network, optionally filtering the results.
     * 
     * @param networkId
     *            the ID of the network.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of initiators.
     */
    public List<InitiatorRestRep> getByNetwork(URI networkId, ResourceFilter<InitiatorRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByNetwork(networkId);
        return getByRefs(refs, filter);
    }

    /**
     * Registers the given initiator by ID.
     * <p>
     * API Call: <tt>POST /compute/initiators/{id}/register</tt>
     * 
     * @param id
     *            the ID of the initiator.
     * @return the updated initiator.
     */
    public InitiatorRestRep register(URI id) {
        return client.post(InitiatorRestRep.class, getIdUrl() + "/register", id);
    }

    /**
     * De-registers the given initiator by ID.
     * <p>
     * API Call: <tt>POST /computer/initiators/{id}/deregister</tt>
     * 
     * @param id
     *            the ID of the initiator.
     * @return the updated initiator.
     */
    public InitiatorRestRep deregister(URI id) {
        return client.post(InitiatorRestRep.class, getIdUrl() + "/deregister", id);
    }
}
