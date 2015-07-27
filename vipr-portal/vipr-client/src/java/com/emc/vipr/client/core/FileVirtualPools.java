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

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.vpool.CapacityResponse;
import com.emc.storageos.model.vpool.FileVirtualPoolBulkRep;
import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.NamedRelatedVirtualPoolRep;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.impl.SearchConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

import static com.emc.vipr.client.core.util.VirtualPoolUtils.*;

/**
 * File Virtual Pools resources.
 * <p>
 * Base URL: <tt>/file/vpools</tt>
 */
public class FileVirtualPools extends AbstractCoreBulkResources<FileVirtualPoolRestRep> implements
        TopLevelResources<FileVirtualPoolRestRep>, ACLResources, QuotaResources {
    public FileVirtualPools(ViPRCoreClient parent, RestClient client) {
        super(parent, client, FileVirtualPoolRestRep.class, PathConstants.FILE_VPOOL_URL);
    }

    @Override
    public FileVirtualPools withInactive(boolean inactive) {
        return (FileVirtualPools) super.withInactive(inactive);
    }

    @Override
    public FileVirtualPools withInternal(boolean internal) {
        return (FileVirtualPools) super.withInternal(internal);
    }

    @Override
    protected List<FileVirtualPoolRestRep> getBulkResources(BulkIdParam input) {
        FileVirtualPoolBulkRep response = client.post(FileVirtualPoolBulkRep.class, input, getBulkUrl());
        return defaultList(response.getVirtualPools());
    }

    /**
     * Gets a list of virtual pools from the given URL.
     * 
     * @param url
     *        the URL to get.
     * @param args
     *        the URL arguments.
     * @return the list of virtual pool references.
     */
    protected List<NamedRelatedVirtualPoolRep> getList(String url, Object... args) {
        VirtualPoolList response = client.get(VirtualPoolList.class, url, args);
        return ResourceUtils.defaultList(response.getVirtualPool());
    }

    /**
     * Lists all file virtual pools.
     * <p>
     * API Call: <tt>GET /file/vpools</tt>
     * 
     * @return the list of all file virtual pool references.
     */
    @Override
    public List<NamedRelatedVirtualPoolRep> list() {
        return getList(baseUrl);
    }

    /**
     * Lists all virtual pools in a virtual data center
     * <p>
     * API Call: <tt>GET /file/vpools</tt>
     * 
     * @return the list of virtual pool references in a virtual data center
     */
    public List<NamedRelatedVirtualPoolRep> listByVDC(String shortVdcId) {
        UriBuilder builder = client.uriBuilder(baseUrl);
        builder.queryParam(SearchConstants.VDC_ID_PARAM, shortVdcId);
        VirtualPoolList response =  client.getURI(VirtualPoolList.class, builder.build());
        return ResourceUtils.defaultList(response.getVirtualPool());
    }        
    
    /**
     * Gets a list of all file virtual pools.
     * <p>
     * This is a convenience method for: <tt>getByRefs(list())</tt>
     * 
     * @return the list of file virtual pools.
     */
    @Override
    public List<FileVirtualPoolRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets a list of all file virtual pools, optionally filtering the results.
     * <p>
     * This is a convenience method for: <tt>getByRefs(list(), filter)</tt>
     * 
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the list of file virtual pools.
     */
    @Override
    public List<FileVirtualPoolRestRep> getAll(ResourceFilter<FileVirtualPoolRestRep> filter) {
        List<NamedRelatedVirtualPoolRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Creates a file virtual pool.
     * <p>
     * API Call: <tt>POST /file/vpools</tt>
     * 
     * @param input
     *        the virtual pool configuration.
     * @return the newly created file virtual pool.
     */
    public FileVirtualPoolRestRep create(FileVirtualPoolParam input) {
        return client.post(FileVirtualPoolRestRep.class, input, baseUrl);
    }

    /**
     * Updates a file virtual pool.
     * <p>
     * API Call: <tt>PUT /file/vpools/{id}</tt>
     * 
     * @param id
     *        the ID of the file virtual pool to update.
     * @param input
     * @return
     */
    public FileVirtualPoolRestRep update(URI id, FileVirtualPoolUpdateParam input) {
        return client.put(FileVirtualPoolRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates a file virtual pool.
     * <p>
     * API Call: <tt>POST /file/vpools/{id}/deactivate</tt>
     * 
     * @param id
     *        the ID of the file virtual pool.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    public void deactivate(FileVirtualPoolRestRep pool) {
        deactivate(ResourceUtils.id(pool));
    }

    /**
     * Lists the virtual pools that are associated with the given virtual array.
     * <p>
     * API Call: <tt>GET /vdc/varrays/{id}/vpools</tt>
     *
     * @param varrayId
     *        the ID of the virtual array.
     * @return the list of virtual pool references.
     */
    public List<NamedRelatedVirtualPoolRep> listByVirtualArray(URI varrayId) {
        VirtualPoolList response = client.get(VirtualPoolList.class, String.format(ID_URL_FORMAT, VARRAY_URL) + "/vpools", varrayId);
        return defaultList(response.getVirtualPool());
    }

    /**
     * Gets the storage pools that are associated with the given block virtual pool.
     * Convenience method for calling getByRefs(listByVirtualArray(varrayId)).
     *
     * @param varrayId
     *        the ID of the virtual array.
     * @return the list of virtual pools.
     *
     * @see #listByVirtualArray(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<FileVirtualPoolRestRep> getByVirtualArray(URI varrayId) {
        return getByVirtualArray(varrayId, null);
    }

    /**
     * Gets the storage pools that are associated with the given block virtual pool.
     * Convenience method for calling getByRefs(listByVirtualArray(varrayId)).
     *
     * @param varrayId
     *        the ID of the virtual array.
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     *
     * @return the list of virtual pools.
     *
     * @see #listByVirtualArray(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<FileVirtualPoolRestRep> getByVirtualArray(URI varrayId, ResourceFilter<FileVirtualPoolRestRep> filter) {
        List<NamedRelatedVirtualPoolRep> refs = listByVirtualArray(varrayId);
        return getByRefs(fileVpools(refs), filter);
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
     * Get the capacity of the file virtual pool on the given virtual array.
     * <p>
     * API Call: <tt>GET /file/vpools/{id}/varrays/{virtualArrayId}/capacity</tt>
     * 
     * @param id
     *        the ID of the file virtual pool.
     * @param virtualArrayId
     *        the virtual array.
     * @return the capacity on the virtual array.
     */
    public CapacityResponse getCapacityOnVirtualArray(URI id, URI virtualArrayId) {
        return client.get(CapacityResponse.class, getIdUrl() + "/varrays/{varrayId}/capacity", id, virtualArrayId);
    }

    /**
     * Lists the storage pools for the given file virtual pool by ID.
     * <p>
     * API Call: <tt>GET /file/vpools/{id}/storage-pools</tt>
     * 
     * @param id
     *        the ID of the file virtual pool.
     * @return the list of storage pool references.
     */
    public List<NamedRelatedResourceRep> listStoragePools(URI id) {
        StoragePoolList response = client.get(StoragePoolList.class, getIdUrl() + "/storage-pools", id);
        return defaultList(response.getPools());
    }

    /**
     * Gets the list of storage pools for the given file virtual pool by ID. This is a convenience method for:
     * <tt>getByRefs(listStoragePools(id))</tt>.
     * 
     * @param id
     *        the ID of the file virtual pool.
     * @return the list of storage pools.
     */
    public List<StoragePoolRestRep> getStoragePools(URI id) {
        List<NamedRelatedResourceRep> refs = listStoragePools(id);
        return parent.storagePools().getByRefs(refs);
    }

    /**
     * Lists all storage pools that would match the given file virtual pool configuration.
     * <p>
     * API Call: <tt>POST /file/vpools/matching-pools</tt>
     * 
     * @param input
     *        the file virtual pool configuration.
     * @return the list of matching storage pool references.
     */
    public List<NamedRelatedResourceRep> listMatchingStoragePools(FileVirtualPoolParam input) {
        StoragePoolList response = client.post(StoragePoolList.class, input, baseUrl + "/matching-pools");
        return defaultList(response.getPools());
    }

    /**
     * Gets all storage pools that would match the given file virtual pool configuration.
     * 
     * @param input
     *        the file virtual pool configuration.
     * @return the list of matching storage pools.
     */
    public List<StoragePoolRestRep> getMatchingStoragePools(FileVirtualPoolParam input) {
        List<NamedRelatedResourceRep> refs = listMatchingStoragePools(input);
        return parent.storagePools().getByRefs(refs);
    }

    /**
     * Refreshes the storage pools that match the given file virtual pool.
     * <p>
     * API Call: <tt>GET /file/vpools/{id}/refresh-matched-pools</tt>
     * 
     * @param id
     *        the ID of the file virtual pool.
     * @return the list of currently matching storage pool references.
     */
    public List<NamedRelatedResourceRep> refreshMatchingStoragePools(URI id) {
        StoragePoolList response = client.get(StoragePoolList.class, getIdUrl() + "/refresh-matched-pools", id);
        return defaultList(response.getPools());
    }

    /**
     * Manually updates storage pool assignments for a given file virtual pool.
     * <p>
     * API Call: <tt>PUT /file/vpools/{id}/assign-matched-pools</tt>
     * 
     * @param id
     *        the ID of the file virtual pool.
     * @param input
     *        the configuration of the storage pool assignments.
     * @return the updated file virtual pool.
     */
    public FileVirtualPoolRestRep assignStoragePools(URI id, VirtualPoolPoolUpdateParam input) {
        return client.put(FileVirtualPoolRestRep.class, input, getIdUrl() + "/assign-matched-pools", id);
    }
}
