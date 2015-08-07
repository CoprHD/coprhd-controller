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
import com.emc.storageos.model.block.tier.StorageTierBulkRep;
import com.emc.storageos.model.block.tier.StorageTierList;
import com.emc.storageos.model.block.tier.StorageTierRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.util.ResourceUtils;

/**
 * Storage Tiers resources.
 * <p>
 * Base URL: <tt>/vdc/storage-tiers</tt>
 */
public class StorageTiers extends AbstractCoreBulkResources<StorageTierRestRep> implements
        TopLevelResources<StorageTierRestRep> {
    public StorageTiers(ViPRCoreClient parent, RestClient client) {
        super(parent, client, StorageTierRestRep.class, PathConstants.STORAGE_TIER_URL);
    }

    @Override
    public StorageTiers withInactive(boolean inactive) {
        return (StorageTiers) super.withInactive(inactive);
    }

    @Override
    public StorageTiers withInternal(boolean internal) {
        return (StorageTiers) super.withInternal(internal);
    }

    @Override
    protected List<StorageTierRestRep> getBulkResources(BulkIdParam input) {
        StorageTierBulkRep response = client.post(StorageTierBulkRep.class, input, getBulkUrl());
        return defaultList(response.getStorageTiers());
    }

    /**
     * Lists all storage tiers.
     * <p>
     * API Call: <tt>GET /vdc/storage-tiers</tt>
     * 
     * @return the list of storage tier references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        StorageTierList response = client.get(StorageTierList.class, baseUrl);
        return ResourceUtils.defaultList(response.getStorageTiers());
    }

    /**
     * Gets the list of all storage tiers. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @return the list of all storage tiers.
     */
    @Override
    public List<StorageTierRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all storage tiers, optionally filtering the results. This is a convenience method for:
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of all storage tiers.
     */
    @Override
    public List<StorageTierRestRep> getAll(ResourceFilter<StorageTierRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Lists the storage tiers for the given storage pool by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-pools/{storagePoolId}/storage-tiers</tt>
     * 
     * @param storagePoolId
     *            the ID of the storage pool.
     * @return the list of storage tier references.
     */
    public List<NamedRelatedResourceRep> listByStoragePool(URI storagePoolId) {
        StorageTierList response = client.get(StorageTierList.class, PathConstants.STORAGE_TIER_BY_STORAGE_POOL,
                storagePoolId);
        return defaultList(response.getStorageTiers());
    }

    /**
     * Gets the list of storage pools for the given storage pool by ID. This is a convenience method for:
     * <tt>getByRefs(listByStoragePool(id))</tt>.
     * 
     * @param id
     *            the ID of the storage pool.
     * @return the list of storage tiers.
     */
    public List<StorageTierRestRep> getByStoragePool(URI id) {
        List<NamedRelatedResourceRep> refs = listByStoragePool(id);
        return getByRefs(refs);
    }

    /**
     * Lists the storage pools for the given auto tier policy by ID.
     * <p>
     * API Call: <tt>GET /vdc/auto-tier-policy/{autoTierPolicyId}/storage-tiers</tt>
     * 
     * @param autoTierPolicyId
     *            the ID of the auto tier policy.
     * @return the list of storage pool references.
     */
    public List<NamedRelatedResourceRep> listByAutoTieringPolicy(URI autoTierPolicyId) {
        StorageTierList response = client.get(StorageTierList.class,
                PathConstants.STORAGE_TIER_BY_AUTO_TIERING_POLICY_URL, autoTierPolicyId);
        return defaultList(response.getStorageTiers());
    }

    /**
     * Gets the list of storage pools for the given auto tier policy by ID. This is a convenience method for:
     * <tt>getByRefs(listByAutoTieringPolicy(id))</tt>
     * 
     * @param id
     *            the ID of the auto tier policy.
     * @return the list of storage tiers.
     */
    public List<StorageTierRestRep> getByAutoTieringPolicy(URI id) {
        List<NamedRelatedResourceRep> refs = listByAutoTieringPolicy(id);
        return getByRefs(refs);
    }
}
