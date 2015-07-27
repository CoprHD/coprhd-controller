/*
 * Copyright 2015 EMC Corporation
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
import com.emc.storageos.model.TypedRelatedResourceRep;
import com.emc.storageos.model.block.RelatedStoragePool;
import com.emc.storageos.model.pools.StoragePoolBulkRep;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.pools.StoragePoolResources;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.pools.StoragePoolUpdate;
import com.emc.storageos.model.vpool.NamedRelatedVirtualPoolRep;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.util.ResourceUtils;

/**
 * Storage Pools resources.
 * <p>
 * Base URL: <tt>/vdc/storage-pools</tt>
 */
public class StoragePools extends AbstractCoreBulkResources<StoragePoolRestRep> implements
        TopLevelResources<StoragePoolRestRep> {
    public StoragePools(ViPRCoreClient parent, RestClient client) {
        super(parent, client, StoragePoolRestRep.class, PathConstants.STORAGE_POOL_URL);
    }

    @Override
    public StoragePools withInactive(boolean inactive) {
        return (StoragePools) super.withInactive(inactive);
    }

    @Override
    public StoragePools withInternal(boolean internal) {
        return (StoragePools) super.withInternal(internal);
    }

    @Override
    protected List<StoragePoolRestRep> getBulkResources(BulkIdParam input) {
        StoragePoolBulkRep response = client.post(StoragePoolBulkRep.class, input, getBulkUrl());
        return defaultList(response.getStoragePools());
    }

    /**
     * Lists all storage pools.
     * <p>
     * API Call: <tt>GET /vdc/storage-pools</tt>
     * 
     * @return the list of all storage pool references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        StoragePoolList response = client.get(StoragePoolList.class, baseUrl);
        return defaultList(response.getPools());
    }

    /**
     * Gets all storage pools. This is a convenience method for: <tt>getByRefs(list())</tt>
     * 
     * @return the list of all storage pools.
     */
    @Override
    public List<StoragePoolRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets all storage pools. This is a convenience method for: <tt>getByRefs(list(), filter)</tt>
     * 
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the list of all storage pools.
     */
    @Override
    public List<StoragePoolRestRep> getAll(ResourceFilter<StoragePoolRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Registers a storage pool with the given storage system.
     * <p>
     * API Call: <tt>POST /vdc/storage-systems/{storageSystemId}/storage-pools/{poolId}/register</tt>
     * 
     * @param poolId
     *        the ID of the storage pool.
     * @param storageSystemId
     *        the ID of the storage system.
     * @return the storage pool.
     */
    public StoragePoolRestRep register(URI poolId, URI storageSystemId) {
        String registerUrl = PathConstants.STORAGE_POOL_BY_STORAGE_SYSTEM_URL + "/{poolId}/register";
        return client.post(StoragePoolRestRep.class, registerUrl, storageSystemId, poolId);
    }

    /**
     * Deregisters a storage pool.
     * <p>
     * API Call: <tt>POST /vdc/storage-pools/{id}/deregister</tt>
     * 
     * @param id
     *        the ID of the storage pool.
     */
    public StoragePoolRestRep deregister(URI id) {
        return client.post(StoragePoolRestRep.class, getIdUrl() + "/deregister", id);
    }

    /**
     * Updates the given storage pool by ID.
     * <p>
     * API Call: <tt>PUT /vdc/storage-pools/{id}</tt>
     * 
     * @param id
     *        the ID of the storage pool.
     * @param input
     *        the update configuration.
     * @return the updated storage pool.
     */
    public StoragePoolRestRep update(URI id, StoragePoolUpdate input) {
        return client.put(StoragePoolRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Lists the storage pools for the given storage system by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems/{storageSystemId}/storage-pools</tt>
     * 
     * @param storageSystemId
     *        the ID of the storage system.
     * @return the list of storage pool references.
     */
    public List<NamedRelatedResourceRep> listByStorageSystem(URI storageSystemId) {
        StoragePoolList response = client.get(StoragePoolList.class, PathConstants.STORAGE_POOL_BY_STORAGE_SYSTEM_URL,
                storageSystemId);
        return ResourceUtils.defaultList(response.getPools());
    }

    /**
     * Gets the list of storage pools for the given storage system by ID.
     * 
     * @param storageSystemId
     *        the ID of the storage system.
     * @return the list of storage pools for the storage system.
     */
    public List<StoragePoolRestRep> getByStorageSystem(URI storageSystemId) {
        return getByStorageSystem(storageSystemId, null);
    }

    /**
     * Gets the list of storage pools for the given storage system by ID, optionally filtering the results.
     * 
     * @param storageSystemId
     *        the ID of the storage system.
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the list of storage pools for the storage system.
     */
    public List<StoragePoolRestRep> getByStorageSystem(URI storageSystemId, ResourceFilter<StoragePoolRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByStorageSystem(storageSystemId);
        return getByRefs(refs, filter);
    }

    /**
     * Lists the storage pools that are associated with the given virtual array.
     * <p>
     * API Call: <tt>GET /vdc/varrays/{id}/storage-pools</tt>
     *
     * @param varrayId
     *        the ID of the virtual array.
     * @return the list of storage pool references.
     */
    public List<NamedRelatedResourceRep> listByVirtualArray(URI varrayId) {
        StoragePoolList response = client.get(StoragePoolList.class, String.format(ID_URL_FORMAT, VARRAY_URL) + "/storage-pools", varrayId);
        return defaultList(response.getPools());
    }

    /**
     * Gets the storage pools that are associated with the given virtual array
     *
     * @param varrayId
     *        the ID of the virtual array.
     * @return the list of storage pools.
     *
     * @see #listByVirtualArray(URI)
     * @see StoragePools#getByRefs(java.util.Collection)
     */
    public List<StoragePoolRestRep> getByVirtualArray(URI varrayId) {
        List<NamedRelatedResourceRep> refs = listByVirtualArray(varrayId);
        return getByRefs(refs);
    }

    /**
     * Lists the resources for the given storage pool by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-pools/{id}/resources</tt>
     * 
     * @param id
     *        the ID of the storage pool.
     * @return the list of resource references.
     */
    public List<TypedRelatedResourceRep> listResources(URI id) {
        StoragePoolResources response = client.get(StoragePoolResources.class, getIdUrl() + "/resources", id);
        return defaultList(response.getResources());
    }

    /**
     * Gets the storage pool that contains the given volume by ID.
     * <p>
     * API Call: <tt>GET /block/volumes/{volumeId}/storage-pool</tt>
     * 
     * @param volumeId
     *        the ID of the volume.
     * @return the storage pool for the volume.
     */
    public StoragePoolRestRep getByVolume(URI volumeId) {
        RelatedStoragePool response = client.get(RelatedStoragePool.class, PathConstants.BLOCK_VOLUMES_URL
                + "/{volumeId}/storage-pool", volumeId);
        if (response.getStoragePool() != null) {
            return get(response.getStoragePool());
        }
        else {
            return null;
        }
    }

    /**
     * Lists the virtual pools which match the given storage pool by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-pools/{id}/matched-vpools</tt>
     * 
     * @param id
     *        the ID of the storage pool.
     * @return the list of virtual pool references.
     */
    public List<NamedRelatedVirtualPoolRep> listMatchedVirtualPools(URI id) {
        VirtualPoolList response = client.get(VirtualPoolList.class, getIdUrl() + "/matched-vpools", id);
        return defaultList(response.getVirtualPool());
    }
}
