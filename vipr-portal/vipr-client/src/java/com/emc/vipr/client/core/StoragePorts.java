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
import com.emc.storageos.model.ports.StoragePortBulkRep;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.ports.StoragePortRequestParam;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.util.ResourceUtils;

/**
 * Storage Ports resources.
 * <p>
 * Base URL: <tt>/vdc/storage-ports</tt>
 */
public class StoragePorts extends AbstractCoreBulkResources<StoragePortRestRep> implements
        TopLevelResources<StoragePortRestRep> {
    public StoragePorts(ViPRCoreClient parent, RestClient client) {
        super(parent, client, StoragePortRestRep.class, PathConstants.STORAGE_PORT_URL);
    }

    @Override
    public StoragePorts withInactive(boolean inactive) {
        return (StoragePorts) super.withInactive(inactive);
    }

    @Override
    public StoragePorts withInternal(boolean internal) {
        return (StoragePorts) super.withInternal(internal);
    }

    /**
     * Gets the URL for de-registering a port.
     * 
     * @return the de-register URL.
     */
    protected String getDeregisterUrl() {
        return String.format(PathConstants.DEREGISTER_URL_FORMAT, baseUrl);
    }

    @Override
    protected List<StoragePortRestRep> getBulkResources(BulkIdParam input) {
        StoragePortBulkRep response = client.post(StoragePortBulkRep.class, input, getBulkUrl());
        return defaultList(response.getStoragePorts());
    }

    /**
     * Gets a list of storage port references from the given path.
     * 
     * @param path
     *            the path to get.
     * @param args
     *            the path arguments.
     * @return the list of storage port references.
     */
    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
        StoragePortList response = client.get(StoragePortList.class, path, args);
        return ResourceUtils.defaultList(response.getPorts());
    }

    /**
     * Lists all storage ports.
     * <p>
     * API Call: <tt>GET /vdc/storage-ports</tt>
     * 
     * @return the list of storage port references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        return getList(baseUrl);
    }

    /**
     * Gets a list of all storage ports. This is a convenience method for <tt>getByRefs(list())</tt>.
     * 
     * @return the list of all storage ports.
     */
    @Override
    public List<StoragePortRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets a list of all storage ports, optionally filtering the results. This is a convenience method for
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of all storage ports.
     */
    @Override
    public List<StoragePortRestRep> getAll(ResourceFilter<StoragePortRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Creates the given storage port.
     * <p>
     * API call: <tt>POST {@value PathConstants#STORAGE_PORT_BY_STORAGE_SYSTEM_URL}</tt>
     * </p>
     * 
     * @param id the storage system Id
     * @param input the storage port
     * @return the new storage port
     */
    public StoragePortRestRep create(URI id, StoragePortRequestParam input) {
        return client.post(StoragePortRestRep.class, input, PathConstants.STORAGE_PORT_BY_STORAGE_SYSTEM_URL, id);
    }

    /**
     * Updates the given storage port by ID.
     * <p>
     * API Call: <tt>PUT /vdc/storage-ports/{id}</tt>
     * 
     * @param id
     *            the ID of the storage port to update.
     * @param input
     *            the update configuration.
     * @return the updated storage port.
     */
    public StoragePortRestRep update(URI id, StoragePortUpdate input) {
        return client.put(StoragePortRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Registers a storage port with the given storage system by ID.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{storageSystemId}/storage-ports/{portId}/register</tt>
     * 
     * @param portId
     *            the ID of the storage port.
     * @param storageSystemId
     *            the ID of the storage system.
     * @return the updated storage port.
     */
    public StoragePortRestRep register(URI portId, URI storageSystemId) {
        String registerUrl = PathConstants.STORAGE_PORT_BY_STORAGE_SYSTEM_URL + "/{portId}/register";
        return client.post(StoragePortRestRep.class, registerUrl, storageSystemId, portId);
    }

    /**
     * De-registers a storage port.
     * <p>
     * API Call: <tt>POST /vdc/storage-ports/{id}/deregister</tt>
     * 
     * @param id
     *            the ID of the storage port to de-register.
     */
    public StoragePortRestRep deregister(URI id) {
        return client.post(StoragePortRestRep.class, getDeregisterUrl(), id);
    }

    /**
     * Lists the storage ports for the given storage system by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems/{storageSystemId}/storage-ports</tt>
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @return the list of storage port references.
     */
    public List<NamedRelatedResourceRep> listByStorageSystem(URI storageSystemId) {
        return getList(PathConstants.STORAGE_PORT_BY_STORAGE_SYSTEM_URL, storageSystemId);
    }

    /**
     * Gets the list of storage ports for the given storage system by ID. This is a convenience method for:
     * <tt>getByRefs(listByStorageSystem(storageSystemId))</tt>.
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @return the list of storage ports.
     */
    public List<StoragePortRestRep> getByStorageSystem(URI storageSystemId) {
        return getByStorageSystem(storageSystemId, null);
    }

    /**
     * Gets the list of storage ports for the given storage system by ID, optionally filtering the results. This is a
     * convenience method for: <tt>getByRefs(listByStorageSystem(storageSystemId), filter)</tt>.
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of storage ports.
     */
    public List<StoragePortRestRep> getByStorageSystem(URI storageSystemId, ResourceFilter<StoragePortRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByStorageSystem(storageSystemId);
        return getByRefs(refs, filter);
    }

    /**
     * Lists the storage ports for the given network by ID.
     * <p>
     * API Call: <tt>GET /vdc/networks/{networkId}/storage-ports</tt>
     * 
     * @param networkId
     *            the ID of the network.
     * @return the list of storage port references.
     */
    public List<NamedRelatedResourceRep> listByNetwork(URI networkId) {
        return getList(PathConstants.STORAGE_PORT_BY_NETWORK_URL, networkId);
    }

    /**
     * Gets the list of storage ports for the given network by ID. This is a convenience method for:
     * <tt>getByRefs(listByNetwork(networkId))</tt>
     * 
     * @param networkId
     *            the ID of the network.
     * @return the list of storage ports.
     */
    public List<StoragePortRestRep> getByNetwork(URI networkId) {
        return getByNetwork(networkId, null);
    }

    /**
     * Gets the list of storage ports for the given network by ID, optionally filtering the results. This is a
     * convenience method for: <tt>getByRefs(listByNetwork(networkId), filter)</tt>
     * 
     * @param networkId
     *            the ID of the network.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of storage ports.
     */
    public List<StoragePortRestRep> getByNetwork(URI networkId, ResourceFilter<StoragePortRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByNetwork(networkId);
        return getByRefs(refs, filter);
    }

    /**
     * Lists the storage ports for the given virtual array by ID.
     * <p>
     * API Call: <tt>GET /vdc/varrays/{virtualArrayId}/storage-ports</tt>
     * 
     * @param virtualArrayId
     *            the ID of the virtual array.
     * @return the list of storage port references.
     */
    public List<NamedRelatedResourceRep> listByVirtualArray(URI virtualArrayId) {
        return getList(PathConstants.STORAGE_PORT_BY_VARRAY_URL, virtualArrayId);
    }

    /**
     * Gets the list of storage ports for the given virtual array by ID. This is a convenience method for:
     * <tt>getByRefs(listByVirtualArray(virtualArrayId))</tt>
     * 
     * @param virtualArrayId
     *            the ID of the virtual array.
     * @return the list of storage ports.
     */
    public List<StoragePortRestRep> getByVirtualArray(URI virtualArrayId) {
        return getByVirtualArray(virtualArrayId, null);
    }

    /**
     * Gets the list of storage ports for the given virtual array by ID, optionally filtering the results. This is a
     * convenience method for: <tt>getByRefs(listByVirtualArray(virtualArrayId), filter)</tt>
     * 
     * @param virtualArrayId
     *            the ID of the virtual array.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of storage ports.
     */
    public List<StoragePortRestRep> getByVirtualArray(URI virtualArrayId, ResourceFilter<StoragePortRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByVirtualArray(virtualArrayId);
        return getByRefs(refs, filter);
    }
}
