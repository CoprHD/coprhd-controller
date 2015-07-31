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
import com.emc.storageos.model.host.IpInterfaceBulkRep;
import com.emc.storageos.model.host.IpInterfaceCreateParam;
import com.emc.storageos.model.host.IpInterfaceList;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.host.IpInterfaceUpdateParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * IP Interfaces resources.
 * <p>
 * Base URL: <tt>/compute/ip-interfaces</tt>
 */
public class IpInterfaces extends AbstractCoreBulkResources<IpInterfaceRestRep> {
    public IpInterfaces(ViPRCoreClient parent, RestClient client) {
        super(parent, client, IpInterfaceRestRep.class, PathConstants.IPINTERFACE_URL);
    }

    @Override
    public IpInterfaces withInactive(boolean inactive) {
        return (IpInterfaces) super.withInactive(inactive);
    }

    @Override
    public IpInterfaces withInternal(boolean internal) {
        return (IpInterfaces) super.withInternal(internal);
    }

    @Override
    protected List<IpInterfaceRestRep> getBulkResources(BulkIdParam input) {
        IpInterfaceBulkRep response = client.post(IpInterfaceBulkRep.class, input, getBulkUrl());
        return defaultList(response.getIpInterfaces());
    }

    /**
     * Gets a list of IP interface references from the given path.
     * 
     * @param path
     *            the path to get.
     * @param args
     *            the path arguments.
     * @return the list of IP interface references.
     */
    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
        IpInterfaceList response = client.get(IpInterfaceList.class, path, args);
        return defaultList(response.getIpInterfaces());
    }

    /**
     * Creates an IP interface for the given host.
     * <p>
     * API Call: <tt>POST /compute/hosts/{hostId}/ip-interfaces</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @param input
     *            the create configuration.
     * @return the created IP interface.
     */
    public IpInterfaceRestRep create(URI hostId, IpInterfaceCreateParam input) {
        return client.post(IpInterfaceRestRep.class, input, PathConstants.IPINTERFACE_BY_HOST_URL, hostId);
    }

    /**
     * Updates the given IP interface.
     * <p>
     * API Call: <tt>PUT /compute/ip-interfaces/{id}</tt>
     * 
     * @param id
     *            the ID of the IP interface.
     * @param input
     *            the updated configuration.
     * @return the updated IP interface.
     */
    public IpInterfaceRestRep update(URI id, IpInterfaceUpdateParam input) {
        return client.put(IpInterfaceRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given IP interface.
     * <p>
     * API Call: <tt>POST /compute/ip-interfaces/{id}</tt>
     * 
     * @param id
     *            the ID of the IP interface.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    /**
     * Lists the IP interfaces for the given host.
     * <p>
     * API Call: <tt>GET /compute/hosts/{hostId}/ip-interfaces</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of IP interface references.
     */
    public List<NamedRelatedResourceRep> listByHost(URI hostId) {
        return getList(PathConstants.IPINTERFACE_BY_HOST_URL, hostId);
    }

    /**
     * Gets the list of IP interfaces for the given host.
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of IP interfaces.
     */
    public List<IpInterfaceRestRep> getByHost(URI hostId) {
        return getByHost(hostId, null);
    }

    /**
     * Gets the list of IP interfaces for the given host, optionally filtering the results.
     * 
     * @param hostId
     *            the ID of the host.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of IP interfaces.
     */
    public List<IpInterfaceRestRep> getByHost(URI hostId, ResourceFilter<IpInterfaceRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByHost(hostId);
        return getByRefs(refs, filter);
    }

    /**
     * Lists the IP interfaces in the given network.
     * <p>
     * API Call: <tt>GET /vdc/networks/{networkId}/ip-interfaces</tt>
     * 
     * @param networkId
     *            the ID of the network.
     * @return the list of IP interface references in the given network.
     */
    public List<NamedRelatedResourceRep> listByNetwork(URI networkId) {
        return getList(PathConstants.IP_INTERFACES_BY_NETWORK_URL, networkId);
    }

    /**
     * Gets the list of IP interfaces in the given network.
     * 
     * @param networkId
     *            the ID of the network.
     * @return the list of IP interfaces in the given network.
     */
    public List<IpInterfaceRestRep> getByNetwork(URI networkId) {
        return getByNetwork(networkId, null);
    }

    /**
     * Gets the list of IP interfaces in the given network, optionally filtering the results.
     * 
     * @param networkId
     *            the ID of the network.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of IP interfaces in the given network.
     */
    public List<IpInterfaceRestRep> getByNetwork(URI networkId, ResourceFilter<IpInterfaceRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByNetwork(networkId);
        return getByRefs(refs, filter);
    }

    /**
     * Registers the given IP interface by ID.
     * <p>
     * API Call: <tt>POST /compute/ip-interfaces/{id}/register</tt>
     * 
     * @param id
     *            the ID of the IP interface.
     * @return the updated IP interface.
     */
    public IpInterfaceRestRep register(URI id) {
        return client.post(IpInterfaceRestRep.class, getIdUrl() + "/register", id);
    }

    /**
     * De-registers the given IP interface by ID.
     * <p>
     * API Call: <tt>POST /computer/ip-interfaces/{id}/deregister</tt>
     * 
     * @param id
     *            the ID of the IP interface.
     * @return the updated IP interface.
     */
    public IpInterfaceRestRep deregister(URI id) {
        return client.post(IpInterfaceRestRep.class, getIdUrl() + "/deregister", id);
    }
}