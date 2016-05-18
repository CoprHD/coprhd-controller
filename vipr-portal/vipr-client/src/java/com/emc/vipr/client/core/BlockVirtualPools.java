/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.PathConstants.ID_URL_FORMAT;
import static com.emc.vipr.client.core.impl.PathConstants.VARRAY_URL;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;
import static com.emc.vipr.client.core.util.VirtualPoolUtils.blockVpools;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolBulkRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.CapacityResponse;
import com.emc.storageos.model.vpool.NamedRelatedVirtualPoolRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.model.vpool.VirtualPoolChangeRep;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.impl.SearchConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Block Virtual Pools resource.
 * <p>
 * Base URL: <tt>/block/vpools</tt>
 */
public class BlockVirtualPools extends AbstractCoreBulkResources<BlockVirtualPoolRestRep> implements
        TopLevelResources<BlockVirtualPoolRestRep>, ACLResources, QuotaResources {
    public BlockVirtualPools(ViPRCoreClient parent, RestClient client) {
        super(parent, client, BlockVirtualPoolRestRep.class, PathConstants.BLOCK_VPOOL_URL);
    }

    @Override
    public BlockVirtualPools withInactive(boolean inactive) {
        return (BlockVirtualPools) super.withInactive(inactive);
    }

    @Override
    public BlockVirtualPools withInternal(boolean internal) {
        return (BlockVirtualPools) super.withInternal(internal);
    }

    @Override
    protected List<BlockVirtualPoolRestRep> getBulkResources(BulkIdParam input) {
        BlockVirtualPoolBulkRep response = client.post(BlockVirtualPoolBulkRep.class, input, getBulkUrl());
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
    protected List<NamedRelatedVirtualPoolRep> getList(String url, Object... args) {
        VirtualPoolList response = client.get(VirtualPoolList.class, url, args);
        return ResourceUtils.defaultList(response.getVirtualPool());
    }

    /**
     * Lists all block virtual pools.
     * <p>
     * API Call: <tt>GET /block/vpools</tt>
     * 
     * @return the list of block virtual pool references.
     */
    @Override
    public List<NamedRelatedVirtualPoolRep> list() {
        return getList(baseUrl);
    }

    /**
     * Lists all virtual pools in a virtual data center
     * <p>
     * API Call: <tt>GET /block/vpools</tt>
     * 
     * @return the list of virtual pool references in a virtual data center
     */
    public List<NamedRelatedVirtualPoolRep> listByVDC(String shortVdcId) {
        UriBuilder builder = client.uriBuilder(baseUrl);
        builder.queryParam(SearchConstants.VDC_ID_PARAM, shortVdcId);
        VirtualPoolList response = client.getURI(VirtualPoolList.class, builder.build());
        return ResourceUtils.defaultList(response.getVirtualPool());
    }

    /**
     * Lists all virtual pools of specific tenant
     * <p>
     * API Call: <tt>GET /block/vpools</tt>
     *
     * @return the list of virtual pool references of specific tenant
     */
    public List<NamedRelatedVirtualPoolRep> listByTenant(URI tenantId) {
        UriBuilder builder = client.uriBuilder(baseUrl);
        builder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        VirtualPoolList response = client.getURI(VirtualPoolList.class, builder.build());
        return ResourceUtils.defaultList(response.getVirtualPool());
    }

    public List<BlockVirtualPoolRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    public List<BlockVirtualPoolRestRep> getByTenant(URI tenantId, ResourceFilter<BlockVirtualPoolRestRep> filter) {
        List<NamedRelatedVirtualPoolRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    /**
     * Gets all block virtual pools.
     * 
     * @return the list of block virtual pools.
     * 
     * @see #list()
     * @see #getByRefs(java.util.Collection)
     */
    @Override
    public List<BlockVirtualPoolRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets all block virtual pools, optionally filtering the results.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of block virtual pools.
     */
    @Override
    public List<BlockVirtualPoolRestRep> getAll(ResourceFilter<BlockVirtualPoolRestRep> filter) {
        List<NamedRelatedVirtualPoolRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Creates a new block virtual pool.
     * <p>
     * API Call: <tt>POST /block/vpools</tt>
     * 
     * @param input
     *            the create configuration.
     * @return the newly created block virtual pool.
     */
    public BlockVirtualPoolRestRep create(BlockVirtualPoolParam input) {
        return client.post(BlockVirtualPoolRestRep.class, input, baseUrl);
    }

    /**
     * Updates a block virtual pool.
     * <p>
     * API Call: <tt>PUT /block/vpools/{id}</tt>
     * 
     * @param id
     *            the ID of the block virtual pool to update.
     * @param input
     *            the update configuration.
     * @return the updated block virtual pool.
     */
    public BlockVirtualPoolRestRep update(URI id, BlockVirtualPoolUpdateParam input) {
        return client.put(BlockVirtualPoolRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates a block virtual pool.
     * <p>
     * API Call: <tt>POST /block/vpools/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the block virtual pool to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    public void deactivate(BlockVirtualPoolRestRep pool) {
        deactivate(ResourceUtils.id(pool));
    }

    /**
     * Lists the virtual pools that are associated with the given virtual array.
     * <p>
     * API Call: <tt>GET /vdc/varrays/{id}/vpools</tt>
     * 
     * @param varrayId
     *            the ID of the virtual array.
     * @return the list of virtual pool references.
     */
    public List<NamedRelatedVirtualPoolRep> listByVirtualArray(URI varrayId) {
        VirtualPoolList response = client.get(VirtualPoolList.class, String.format(ID_URL_FORMAT, VARRAY_URL) + "/vpools", varrayId);
        return defaultList(response.getVirtualPool());
    }

    public List<NamedRelatedVirtualPoolRep> listByVirtualArrayAndTenant(URI varrayId, URI tenantId) {
        UriBuilder builder = client.uriBuilder(String.format(ID_URL_FORMAT, VARRAY_URL) + "/vpools");
        builder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        VirtualPoolList response = client.getURI(VirtualPoolList.class, builder.build(varrayId));
        return defaultList(response.getVirtualPool());
    }

    /**
     * Gets the storage pools that are associated with the given block virtual pool.
     * Convenience method for calling getByRefs(listByVirtualArray(varrayId)).
     * 
     * @param varrayId
     *            the ID of the virtual array.
     * @return the list of virtual pools.
     * 
     * @see #listByVirtualArray(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<BlockVirtualPoolRestRep> getByVirtualArray(URI varrayId) {
        return getByVirtualArray(varrayId, null);
    }

    /**
     * Gets the storage pools that are associated with the given block virtual pool and tenant.
     *
     * @param varrayId
     *            the ID of the virtual array.
     * @param tenantId
     *            the ID of tenant
     * @return the list of virtual pools.
     *
     * @see #listByVirtualArray(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<BlockVirtualPoolRestRep> getByVirtualArrayAndTenant(URI varrayId, URI tenantId) {
        return getByVirtualArray(varrayId, tenantId, null);
    }

    /**
     * Gets the storage pools that are associated with the given block virtual pool.
     * Convenience method for calling getByRefs(listByVirtualArray(varrayId)).
     * 
     * @param varrayId
     *            the ID of the virtual array.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * 
     * @return the list of virtual pools.
     * 
     * @see #listByVirtualArray(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<BlockVirtualPoolRestRep> getByVirtualArray(URI varrayId, ResourceFilter<BlockVirtualPoolRestRep> filter) {
        List<NamedRelatedVirtualPoolRep> refs = listByVirtualArray(varrayId);
        return getByRefs(blockVpools(refs), filter);
    }


    public List<BlockVirtualPoolRestRep> getByVirtualArray(URI varrayId, URI tenantId, ResourceFilter<BlockVirtualPoolRestRep> filter) {
        List<NamedRelatedVirtualPoolRep> refs = listByVirtualArrayAndTenant(varrayId, tenantId);
        return getByRefs(blockVpools(refs), filter);
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

    /**
     * Gets the virtual pool capacity on a given virtual array.
     * <p>
     * API Call: <tt>GET /block/vpools/{id}/varrays/{varrayId}/capacity</tt>
     * 
     * @param id
     *            the ID of the block virtual pool.
     * @param virtualArrayId
     *            the ID of the virtual array.
     * @return the capacity information.
     */
    public CapacityResponse getCapacityOnVirtualArray(URI id, URI virtualArrayId) {
        return client.get(CapacityResponse.class, getIdUrl() + "/varrays/{varrayId}/capacity", id, virtualArrayId);
    }

    /**
     * Lists the storage pools that are associated with the given block virtual pool.
     * <p>
     * API Call: <tt>GET /block/vpools/{id}/storage-pools</tt>
     * 
     * @param id
     *            the ID of the block virtual pool.
     * @return the list of storage pool references.
     */
    public List<NamedRelatedResourceRep> listStoragePools(URI id) {
        StoragePoolList response = client.get(StoragePoolList.class, getIdUrl() + "/storage-pools", id);
        return defaultList(response.getPools());
    }

    /**
     * Gets the storage pools that are associated with the given block virtual pool
     * 
     * @param id
     *            the ID of the block virtual pool.
     * @return the list of storage pools.
     * 
     * @see #listStoragePools(URI)
     * @see StoragePools#getByRefs(java.util.Collection)
     */
    public List<StoragePoolRestRep> getStoragePools(URI id) {
        List<NamedRelatedResourceRep> refs = listStoragePools(id);
        return parent.storagePools().getByRefs(refs);
    }

    /**
     * Lists the storage pools that would match a block virtual pool with the given configuration.
     * <p>
     * API Call: <tt>POST /block/vpools/matching-pools</tt>
     * 
     * @param input
     *            the configuration for a potential block virtual pool.
     * @return the list of matching storage pool references.
     */
    public List<NamedRelatedResourceRep> listMatchingStoragePools(BlockVirtualPoolParam input) {
        StoragePoolList response = client.post(StoragePoolList.class, input, baseUrl + "/matching-pools");
        return defaultList(response.getPools());
    }

    /**
     * Get the storage pools that would match a block virtual pool with the given configuration.
     * 
     * @param input
     *            the configuration for potential block virtual pool.
     * @return the list of matching storage pools.
     */
    public List<StoragePoolRestRep> getMatchingStoragePools(BlockVirtualPoolParam input) {
        List<NamedRelatedResourceRep> refs = listMatchingStoragePools(input);
        return parent.storagePools().getByRefs(refs);
    }

    /**
     * Refreshes the storage pools that match the given block virtual pool.
     * <p>
     * API Call: <tt>GET /block/vpools/{id}/refresh-matched-pools</tt>
     * 
     * @param id
     *            the ID of the block virtual pool.
     * @return the list of currently matching storage pool references.
     */
    public List<NamedRelatedResourceRep> refreshMatchingStoragePools(URI id) {
        StoragePoolList response = client.get(StoragePoolList.class, getIdUrl() + "/refresh-matched-pools", id);
        return defaultList(response.getPools());
    }

    /**
     * Manually updates storage pool assignments for a given block virtual pool.
     * <p>
     * API Call: <tt>PUT /block/vpools/{id}/assign-matched-pools</tt>
     * 
     * @param id
     *            the ID of the block virtual pool.
     * @param input
     *            the configuration of the storage pool assignments.
     * @return the updated block virtual pool.
     */
    public BlockVirtualPoolRestRep assignStoragePools(URI id, VirtualPoolPoolUpdateParam input) {
        return client.put(BlockVirtualPoolRestRep.class, input, getIdUrl() + "/assign-matched-pools", id);
    }

    /**
     * Lists virtual pool change candidates for the given block virtual pool.
     * <p>
     * API Call: <tt>GET /block/vpools/{id}/vpool-change/vpool</tt>
     * 
     * @param id
     *            the ID of the block virtual pool.
     * @return the list of virtual pool candidates.
     */
    public List<VirtualPoolChangeRep> listVirtualPoolChangeCandidates(URI id, BulkIdParam input) {
        VirtualPoolChangeList response = client.post(VirtualPoolChangeList.class, input, getIdUrl() + "/vpool-change/vpool", id);
        return defaultList(response.getVirtualPools());
    }

}