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
import com.emc.storageos.model.smis.StorageProviderBulkRep;
import com.emc.storageos.model.smis.StorageProviderCreateParam;
import com.emc.storageos.model.smis.StorageProviderList;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.smis.StorageProviderUpdateParam;
import com.emc.storageos.model.varray.DecommissionedResourceRep;
import com.emc.storageos.model.varray.DecommissionedResources;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * SMI-S Providers resources.
 * <p>
 * Base URL: <tt>/vdc/storage-providers</tt>
 */
public class StorageProviders extends AbstractCoreBulkResources<StorageProviderRestRep> implements
        TopLevelResources<StorageProviderRestRep>, TaskResources<StorageProviderRestRep> {
    public StorageProviders(ViPRCoreClient parent, RestClient client) {
        super(parent, client, StorageProviderRestRep.class, PathConstants.STORAGE_PROVIDER_URL);
    }

    @Override
    public StorageProviders withInactive(boolean inactive) {
        return (StorageProviders) super.withInactive(inactive);
    }

    @Override
    public StorageProviders withInternal(boolean internal) {
        return (StorageProviders) super.withInternal(internal);
    }

    @Override
    protected List<StorageProviderRestRep> getBulkResources(BulkIdParam input) {
        StorageProviderBulkRep response = client.post(StorageProviderBulkRep.class, input, getBulkUrl());
        return defaultList(response.getStorageProviders());
    }

    @Override
    public Tasks<StorageProviderRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<StorageProviderRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Lists all Storage providers.
     * <p>
     * API Call: <tt>GET /vdc/storage-providers</tt>
     * 
     * @return the list of Storage provider references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        StorageProviderList response = client.get(StorageProviderList.class, baseUrl);
        return ResourceUtils.defaultList(response.getStorageProviders());
    }

    /**
     * Gets a list of all SMI-S providers. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @return the list of SMI-S providers.
     */
    @Override
    public List<StorageProviderRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets a list of all SMI-S providers, optionally filtering the results. This is a convenience method for:
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the list of SMI-S providers.
     */
    @Override
    public List<StorageProviderRestRep> getAll(ResourceFilter<StorageProviderRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a new storage provider.
     * <p>
     * API Call: <tt>POST /vdc/storage-providers</tt>
     * 
     * @param input
     *        the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<StorageProviderRestRep> create(StorageProviderCreateParam input) {
        return postTask(input, baseUrl);
    }

    /**
     * Updates the given storage provider by ID.
     * <p>
     * API Call: <tt>PUT /vdc/storage-providers/{id}</tt>
     * 
     * @param id
     *        the ID of the SMI-S provider.
     * @param input
     *        the update configuration.
     * @return the updated SMI-S provider.
     */
    public StorageProviderRestRep update(URI id, StorageProviderUpdateParam input) {
        return client.put(StorageProviderRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given storage provider by ID.
     * <p>
     * API Call: <tt>POST /vdc/storage-providers/{id}/deactivate</tt>
     * 
     * @param id
     *        the ID of the SMI-S provider.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    /**
     * Scans all storage providers.
     * <p>
     * API Call: <tt>POST /vdc/storage-providers/scan</tt>
     * 
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<StorageProviderRestRep> scanAll() {
        return postTasks(baseUrl + "/scan");
    }

    /**
     * Gets all deactivated systems.
     * <p>
     * API Call: <tt>GET /vdc/storage-providers/deactivated-systems</tt>
     * 
     * @return the list of deactivates systems.
     */
    public List<DecommissionedResourceRep> listDeactivatedSystems() {
        DecommissionedResources response = client.get(DecommissionedResources.class, baseUrl + "/deactivated-systems");
        return defaultList(response.getResources());
    }
}
