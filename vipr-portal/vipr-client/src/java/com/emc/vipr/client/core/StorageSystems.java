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
import com.emc.storageos.model.smis.StorageSystemSMISCreateParam;
import com.emc.storageos.model.systems.StorageSystemBulkRep;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.systems.StorageSystemConnectivityRestRep;
import com.emc.storageos.model.systems.StorageSystemList;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemUpdateRequestParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.util.ResourceUtils;

import javax.ws.rs.core.UriBuilder;

/**
 * Storage Systems resources.
 * <p>
 * Base URL: <tt>/vdc/storage-systems</tt>
 */
public class StorageSystems extends AbstractCoreBulkResources<StorageSystemRestRep> implements
        TopLevelResources<StorageSystemRestRep>, TaskResources<StorageSystemRestRep> {
    public StorageSystems(ViPRCoreClient parent, RestClient client) {
        super(parent, client, StorageSystemRestRep.class, PathConstants.STORAGE_SYSTEM_URL);
    }

    @Override
    public StorageSystems withInactive(boolean inactive) {
        return (StorageSystems) super.withInactive(inactive);
    }

    @Override
    public StorageSystems withInternal(boolean internal) {
        return (StorageSystems) super.withInternal(internal);
    }

    @Override
    protected List<StorageSystemRestRep> getBulkResources(BulkIdParam input) {
        StorageSystemBulkRep response = client.post(StorageSystemBulkRep.class, input, getBulkUrl());
        return defaultList(response.getStorageSystems());
    }

    @Override
    public Tasks<StorageSystemRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<StorageSystemRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Lists all storage systems.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems</tt>
     * 
     * @return the list of storage system references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        StorageSystemList response = client.get(StorageSystemList.class, baseUrl);
        return ResourceUtils.defaultList(response.getStorageSystems());
    }

    /**
     * Gets the list of all storage systems. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @return the list of all storage systems.
     */
    @Override
    public List<StorageSystemRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all storage systems, optionally filering the results. This is a convenience method for:
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the list of all storage systems.
     */
    @Override
    public List<StorageSystemRestRep> getAll(ResourceFilter<StorageSystemRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a storage system.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems</tt>
     * 
     * @param input
     *        the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<StorageSystemRestRep> create(StorageSystemRequestParam input) {
        return postTask(input, baseUrl);
    }

    /**
     * Begins updating the given storage system by ID.
     * <p>
     * API Call: <tt>PUT /vdc/storage-systems/{id}</tt>
     * 
     * @param id
     *        the ID of the storage system.
     * @param input
     *        the update configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<StorageSystemRestRep> update(URI id, StorageSystemUpdateRequestParam input) {
        return putTask(input, getIdUrl(), id);
    }

    /**
     * Begins deactivating the given storage system by ID.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{id}/deactivate</tt>
     * 
     * @param id
     *        the ID of the storage system.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<StorageSystemRestRep> deactivate(URI id) {
        return doDeactivateWithTask(id);
    }

    /**
     * Adds an SMI-S storage system.
     * <p>
     * API Call: <tt>POST /vdc/storage-providers/storage-systems</tt>
     * 
     * @param input
     *        the SMI-S storage system configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<StorageSystemRestRep> add(StorageSystemSMISCreateParam input) {
        return postTask(input, PathConstants.STORAGE_PROVIDER_URL + "/storage-systems");
    }

    /**
     * Begins discovery on all storage systems.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/discover</tt>
     * 
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<StorageSystemRestRep> discoverAll() {
        return postTasks(baseUrl + "/discover");
    }

    /**
     * Begins discovery on the given storage system.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{id}/discover</tt>
     *
     * @param id
     *        the ID of the storage system.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<StorageSystemRestRep> discover(URI id) {
        return discover(id, null);
    }

    /**
     * Begins discovery on the given storage system.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{id}/discover</tt>
     * 
     * @param id
     *        the ID of the storage system.
     * @param type
     *        the type of discovery to perform.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<StorageSystemRestRep> discover(URI id, String type) {
        UriBuilder builder = client.uriBuilder(getIdUrl() + "/discover");
        if (type != null && !type.equals("")) {
            builder = builder.queryParam("namespace", type);
        }
        return postTaskURI(builder.build(id));
    }

    /**
     * Registers the given storage system by ID.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{id}/register</tt>
     * 
     * @param id
     *        the ID of the storage system.
     * @return the updated storage system.
     */
    public StorageSystemRestRep register(URI id) {
        return client.post(StorageSystemRestRep.class, getIdUrl() + "/register", id);
    }

    /**
     * De-registers the given storage system by ID.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{id}/deregister</tt>
     * 
     * @param id
     *        the ID of the storage system.
     * @return the updated storage system.
     */
    public StorageSystemRestRep deregister(URI id) {
        return client.post(StorageSystemRestRep.class, getIdUrl() + "/deregister", id);
    }

    /**
     * Gets the connectivity information for the given storage system by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems/{id}/connectivity</tt>
     * 
     * @param id
     *        the ID of the storage system.
     * @return the list of storage system connectivity.
     */
    public List<StorageSystemConnectivityRestRep> getConnectivity(URI id) {
        StorageSystemConnectivityList response = client.get(StorageSystemConnectivityList.class, getIdUrl()
                + "/connectivity", id);
        return defaultList(response.getConnections());
    }

    /**
     * Lists the storage systems for the given SMI-S provider by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-providers/{smisProviderId}/storage-systems</tt>
     * 
     * @param smisProviderId
     *        the ID of the SMI-S provider.
     * @return the list of storage system references.
     */
    public List<NamedRelatedResourceRep> listBySmisProvider(URI smisProviderId) {
        StorageSystemList response = client.get(StorageSystemList.class,
                PathConstants.STORAGE_SYSTEM_BY_PROVIDER_URL, smisProviderId);
        return defaultList(response.getStorageSystems());
    }

    /**
     * Gets the list of storage systems for the given SMI-S provider by ID. This is a convenience method for
     * <tt>getByRefs(listBySmisProvider(smisProviderId))</tt>
     * 
     * @param smisProviderId
     *        the ID of the SMI-S provider.
     * @return the list of storage systems.
     * 
     * @see #getByRefs(java.util.Collection)
     * @see #listBySmisProvider(URI)
     */
    public List<StorageSystemRestRep> getBySmisProvider(URI smisProviderId) {
        List<NamedRelatedResourceRep> refs = listBySmisProvider(smisProviderId);
        return getByRefs(refs);
    }
}
