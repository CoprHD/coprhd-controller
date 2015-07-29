/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.network.FCEndpointRestRep;
import com.emc.storageos.model.network.FCEndpoints;
import com.emc.storageos.model.network.Fabrics;
import com.emc.storageos.model.network.NetworkSystemBulkRep;
import com.emc.storageos.model.network.NetworkSystemCreate;
import com.emc.storageos.model.network.NetworkSystemList;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.network.NetworkSystemUpdate;
import com.emc.storageos.model.network.SanZone;
import com.emc.storageos.model.network.SanZoneCreateParam;
import com.emc.storageos.model.network.SanZoneUpdateParams;
import com.emc.storageos.model.network.SanZones;
import com.emc.storageos.model.network.SanZonesDeleteParam;
import com.emc.storageos.model.network.WwnAliasParam;
import com.emc.storageos.model.network.WwnAliasesParam;
import com.emc.storageos.model.network.WwnAliasesCreateParam;
import com.emc.storageos.model.network.WwnAliasesDeleteParam;
import com.emc.storageos.model.network.WwnAliasUpdateParams;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

/**
 * Network Systems resources.
 * <p>
 * Base URL: <tt>/vdc/network-systems</tt>
 */
public class NetworkSystems extends AbstractCoreBulkResources<NetworkSystemRestRep> implements
        TopLevelResources<NetworkSystemRestRep>, TaskResources<NetworkSystemRestRep> {
    public NetworkSystems(ViPRCoreClient parent, RestClient client) {
        super(parent, client, NetworkSystemRestRep.class, PathConstants.NETWORK_SYSTEM_URL);
    }

    @Override
    public NetworkSystems withInactive(boolean inactive) {
        return (NetworkSystems) super.withInactive(inactive);
    }

    @Override
    public NetworkSystems withInternal(boolean internal) {
        return (NetworkSystems) super.withInternal(internal);
    }

    @Override
    protected List<NetworkSystemRestRep> getBulkResources(BulkIdParam input) {
        NetworkSystemBulkRep response = client.post(NetworkSystemBulkRep.class, input, getBulkUrl());
        return defaultList(response.getNetworkSystems());
    }

    @Override
    public Tasks<NetworkSystemRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<NetworkSystemRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Lists all network systems.
     * <p>
     * API Call: <tt>GET /vdc/network-systems</tt>
     * 
     * @return the list of network system references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        NetworkSystemList response = client.get(NetworkSystemList.class, baseUrl);
        return ResourceUtils.defaultList(response.getSystems());
    }

    /**
     * Gets the list of all network systems. This is a convenience for <tt>getByRefs(list())</tt>.
     * 
     * @return the list of network systems.
     */
    @Override
    public List<NetworkSystemRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all network systems, optionally filtering the results. This is a convenience for <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of network systems.
     */
    @Override
    public List<NetworkSystemRestRep> getAll(ResourceFilter<NetworkSystemRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a network system.
     * <p>
     * API Call: <tt>POST /vdc/network-systems</tt>
     * 
     * @param input
     *            the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> create(NetworkSystemCreate input) {
        return postTask(input, baseUrl);
    }

    /**
     * Begins updating a network system by ID.
     * <p>
     * API Call: <tt>PUT /vdc/network-systems/{id}</tt>
     * 
     * @param id
     *            the ID of the network system to update.
     * @param input
     *            the update configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> update(URI id, NetworkSystemUpdate input) {
        return putTask(input, getIdUrl(), id);
    }

    /**
     * Begins deactivating a network system by ID.
     * <p>
     * API Call: <tt>POST /vdc/network-systems/{id}</tt>
     * 
     * @param id
     *            the ID of the network system to deactivate.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> deactivate(URI id) {
        return doDeactivateWithTask(id);
    }

    /**
     * Registers the given network system by ID.
     * <p>
     * API Call: <tt>POST /vdc/network-systems/{id}/register</tt>
     * 
     * @param id
     *            the ID of the network system to register.
     * @return the network system.
     */
    public NetworkSystemRestRep register(URI id) {
        return client.post(NetworkSystemRestRep.class, getIdUrl() + "/register", id);
    }

    /**
     * Deregisters the given network system by ID.
     * <p>
     * API Call: <tt>POST /vdc/network-systems/{id}/deregister</tt>
     * 
     * @param id
     *            the ID of the network system to deregister.
     * @return the network system.
     */
    public NetworkSystemRestRep deregister(URI id) {
        return client.post(NetworkSystemRestRep.class, getIdUrl() + "/deregister", id);
    }

    /**
     * Begins discovery of the given network system by ID.
     * <p>
     * API Call: <tt>POST /vdc/network-systems/{id}/discover</tt>
     * 
     * @param id
     *            the ID of the network system to discover.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> discover(URI id) {
        return postTask(getIdUrl() + "/discover", id);
    }

    /**
     * Gets the list of FC endpoints in the given network system by ID.
     * <p>
     * API Call: <tt>GET {@value PathConstants#FC_ENDPOINT_URL}[?fabric-id={fabric-id}]</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param fabricId
     *            the fabric ID, or {@code null} if there is no fabric.
     * @return the list of FC endpoints.
     */
    public List<FCEndpointRestRep> getFCEndpoints(URI id, String fabricId) {
        UriBuilder builder = client.uriBuilder(PathConstants.FC_ENDPOINT_URL);
        if (fabricId != null) {
            builder.queryParam("fabric-id", fabricId);
        }
        FCEndpoints response = client.getURI(FCEndpoints.class, builder.build(id));
        return defaultList(response.getConnections());
    }

    /**
     * Gets the list of SAN fabric names in the given network system by ID.
     * <p>
     * API Call: <tt>GET {@value PathConstants#SAN_FABRIC_URL}</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @return the list of SAN fabric names.
     */
    public List<String> getSanFabrics(URI id) {
        Fabrics response = client.get(Fabrics.class, PathConstants.SAN_FABRIC_URL, id);
        return defaultList(response.getFabricIds());
    }

    /**
     * Gets the list of SAN zones in the given network system by ID and fabric name.
     * <p>
     * API Call: <tt>GET {@value PathConstants#SAN_ZONE_URL}</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param fabric
     *            the name of the fabric.
     * @return the list of SAN zones.
     */
    public List<SanZone> getSanZones(URI id, String fabric) {
        SanZones response = client.get(SanZones.class, PathConstants.SAN_ZONE_URL, id, fabric);
        return defaultList(response.getZones());
    }

    /**
     * Adds SAN zones to the given network system by ID and fabric name.
     * <p>
     * API Call: <tt>POST {@value PathConstants#SAN_ZONE_URL}</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param fabric
     *            the name of the fabric.
     * @param input
     *            the SAN zones configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> addSanZones(URI id, String fabric, SanZoneCreateParam input) {
        return postTask(input, PathConstants.SAN_ZONE_URL, id, fabric);
    }

    /**
     * Removes a SAN zone from the given network system by ID and fabric name.
     * <p>
     * API Call: <tt>POST {@value PathConstants#SAN_ZONE_URL}/remove</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param fabric
     *            the name of the fabric.
     * @param input
     *            the SAN zones configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> removeSanZones(URI id, String fabric, SanZonesDeleteParam input) {
        return postTask(input, PathConstants.SAN_ZONE_URL + "/remove", id, fabric);
    }

    /**
     * Updates a SAN zone from the given network system by ID and fabric name.
     * <p>
     * API Call: <tt>PUT {@value PathConstants#SAN_ZONE_URL}</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param fabric
     *            the name of the fabric.
     * @param input
     *            the SAN zones configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> updateSanZones(URI id, String fabric, SanZoneUpdateParams input) {
        return putTask(input, PathConstants.SAN_ZONE_URL, id, fabric);
    }

    /**
     * Activates the current active zoneset of the fabric. This API assumes an
     * active zoneset already exists. If the active zoneset became empty, this
     * API deactivates it.
     * <p>
     * API Call: <tt>POST {@value PathConstants#SAN_ZONE_URL}/activate</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param fabric
     *            the name of the fabric.
     * @param input
     *            the SAN zones configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> activateSanZones(URI id, String fabric, SanZones input) {
        return postTask(input, PathConstants.SAN_ZONE_URL + "/activate", id, fabric);
    }

    /**
     * Gets the list of WWN Aliases in the given network system by ID and fabric name.
     * <p>
     * API Call: <tt>GET {@value PathConstants#SAN_ALIAS_URL}[?fabric-id={fabric}]</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param fabric
     *            the name of the fabric, or {@code null} if there is no fabric.
     * @return the list of WWN Aliases.
     */
    public List<? extends WwnAliasParam> getAliases(URI id, String fabric) {
        UriBuilder builder = client.uriBuilder(PathConstants.SAN_ALIAS_URL);
        if (fabric != null) {
            builder.queryParam("fabric-id", fabric);
        }
        WwnAliasesParam response = client.getURI(WwnAliasesParam.class, builder.build(id));
        return defaultList(response.getAliases());
    }

    /**
     * Adds WWN Aliases to the given network system by ID and fabric name.
     * <p>
     * API Call: <tt>POST {@value PathConstants#SAN_ALIAS_URL}</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param input
     *            the WWN Aliases configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> addAliases(URI id, WwnAliasesCreateParam input) {
        return postTask(input, PathConstants.SAN_ALIAS_URL, id);
    }

    /**
     * Removes WWN Aliases from the given network system by ID.
     * <p>
     * API Call: <tt>POST {@value PathConstants#SAN_ALIAS_URL}/remove</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param input
     *            the WWN Aliases configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> deleteAliases(URI id, WwnAliasesDeleteParam input) {
        return postTask(input, PathConstants.SAN_ALIAS_URL + "/remove", id);
    }

    /**
     * Updates WWN Aliases from the given network system by ID.
     * <p>
     * API Call: <tt>PUT {@value PathConstants#SAN_ALIAS_URL}</tt>
     * 
     * @param id
     *            the ID of the network system.
     * @param input
     *            the WWN Aliases configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<NetworkSystemRestRep> updateAliases(URI id, WwnAliasUpdateParams input) {
        return putTask(input, PathConstants.SAN_ALIAS_URL, id);
    }
}
