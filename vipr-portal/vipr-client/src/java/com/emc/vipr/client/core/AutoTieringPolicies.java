/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.tier.AutoTierPolicyList;
import com.emc.storageos.model.block.tier.AutoTieringPolicyBulkRep;
import com.emc.storageos.model.block.tier.AutoTieringPolicyRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Auto tier policy resources.
 * <p>
 * Base URL: <tt>/vdc/auto-tier-policies</tt>
 * 
 * @see AutoTieringPolicyRestRep
 */
public class AutoTieringPolicies extends AbstractCoreBulkResources<AutoTieringPolicyRestRep> implements
        TopLevelResources<AutoTieringPolicyRestRep> {
    public AutoTieringPolicies(ViPRCoreClient parent, RestClient client) {
        super(parent, client, AutoTieringPolicyRestRep.class, PathConstants.AUTO_TIERING_POLICY_URL);
    }

    @Override
    public AutoTieringPolicies withInactive(boolean inactive) {
        return (AutoTieringPolicies) super.withInactive(inactive);
    }

    @Override
    public AutoTieringPolicies withInternal(boolean internal) {
        return (AutoTieringPolicies) super.withInternal(internal);
    }

    @Override
    protected List<AutoTieringPolicyRestRep> getBulkResources(BulkIdParam input) {
        AutoTieringPolicyBulkRep response = client.post(AutoTieringPolicyBulkRep.class, input, getBulkUrl());
        return defaultList(response.getAutoTierPolicies());
    }

    /**
     * Gets a list of auto tier policy references from the given URL.
     * 
     * @param url
     *        the URL.
     * @param args
     *        the URL arguments.
     * @return the list of auto tier policy references.
     */
    protected List<NamedRelatedResourceRep> getList(String url, Object... args) {
        AutoTierPolicyList response = client.get(AutoTierPolicyList.class, url, args);
        return defaultList(response.getAutoTierPolicies());
    }

    /**
     * Gets a list of auto tier policy references from the given URI.
     * 
     * @param uri
     *        the URI.
     * @return the list of auto tier policy references.
     */
    protected List<NamedRelatedResourceRep> getList(URI uri) {
        AutoTierPolicyList response = client.getURI(AutoTierPolicyList.class, uri);
        return defaultList(response.getAutoTierPolicies());
    }

    /**
     * Lists all auto tier policies.
     * <p>
     * API Call: <tt>GET /vdc/auto-tier-policies</tt>
     * 
     * @return the list of auto tier policy references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        return list(null, null);
    }

    /**
     * Lists the auto tier policies, with the ability to limit by provisioning type or unique policy name.
     * <p>
     * API Call:
     * <tt>GET /vdc/auto-tier-policies?provisioning_type={provisioningType}&unique_auto_tier_policy_names={uniqueNames}</tt>
     * 
     * @param provisioningType
     *        the provisioning type, if null matches any provisioning type.
     * @param uniqueNames
     *        when true duplicate named policies will be ignored.
     * @return the list of auto tier policy references.
     */
    public List<NamedRelatedResourceRep> list(String provisioningType, Boolean uniqueNames) {
        UriBuilder builder = client.uriBuilder(baseUrl);
        if ((provisioningType != null) && (provisioningType.length() > 0)) {
            builder.queryParam("provisioning_type", provisioningType);
        }
        if (uniqueNames != null) {
            builder.queryParam("unique_auto_tier_policy_names", uniqueNames);
        }
        return getList(builder.build());
    }

    @Override
    public List<AutoTieringPolicyRestRep> getAll() {
        return getAll(null);
    }

    @Override
    public List<AutoTieringPolicyRestRep> getAll(ResourceFilter<AutoTieringPolicyRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Lists all auto tier policies for a given storage system.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems/{storageSystemId}/auto-tier-policies</tt>
     * 
     * @param storageSystemId
     *        the ID of the storage system.
     * @return the list of auto tier policy references.
     * 
     * @see StorageSystems
     */
    public List<NamedRelatedResourceRep> listByStorageSystem(URI storageSystemId) {
        return listByStorageSystem(storageSystemId, null);
    }

    /**
     * Lists all auto tier policies for a given storage system.
     * <p>
     * API Call:
     * <tt>GET /vdc/storage-systems/{storageSystemId}/auto-tier-policies?unique_policy_names={uniqueNames}</tt>
     * 
     * @param storageSystemId
     *        the ID of the storage system.
     * @param uniqueNames
     *        when true duplicate named policies will be ignored.
     * @return the list of auto tier policy references.
     * 
     * @see StorageSystems
     */
    public List<NamedRelatedResourceRep> listByStorageSystem(URI storageSystemId, Boolean uniqueNames) {
        UriBuilder builder = client.uriBuilder(PathConstants.AUTO_TIER_BY_STORAGE_SYSTEM_URL);
        if (uniqueNames != null) {
            builder.queryParam("unique_policy_names", uniqueNames);
        }
        return getList(builder.build(storageSystemId));
    }

    /**
     * Gets all auto tier policies for a given storage system.
     * 
     * @param storageSystemId
     *        the ID of the storage system.
     * @return the list of auto tier policies.
     * 
     * @see #listByStorageSystem(URI)
     * @see #getByRefs(java.util.Collection)
     * @see StorageSystems
     */
    public List<AutoTieringPolicyRestRep> getByStorageSystem(URI storageSystemId) {
        List<NamedRelatedResourceRep> refs = listByStorageSystem(storageSystemId);
        return getByRefs(refs);
    }

    /**
     * Lists all auto tier policies for a given virtual array.
     * <p>
     * API Call: <tt>GET /vdc/varrays/{virtualArrayId}/auto-tier-policies</tt>
     * 
     * @param virtualArrayId
     *        the ID of the virtual array.
     * @return the list of auto tier policy references.
     * 
     * @see VirtualArrays
     */
    public List<NamedRelatedResourceRep> listByVirtualArray(URI virtualArrayId) {
        return getList(PathConstants.AUTO_TIER_BY_VARRAY_URL, virtualArrayId);
    }

    /**
     * Lists all auto tier policies for a given virtual array.
     * <p>
     * API Call:
     * <tt>GET /vdc/varrays/{virtualArrayId}/auto-tier-policies?provisioning_type={provisioningType}&unique_auto_tier_policy_names={uniqueNames}</tt>
     * 
     * @param virtualArrayId
     *        the ID of the virtual array.
     * @param provisioningType
     *        the provisioning type, if null matches any provisioning type.
     * @param uniqueNames
     *        when true duplicate named policies will be ignored.
     * @return the list of auto tier policy references.
     * 
     * @see VirtualArrays
     */
    public List<NamedRelatedResourceRep> listByVirtualArray(URI virtualArrayId, String provisioningType,
            Boolean uniqueNames) {
        UriBuilder builder = client.uriBuilder(PathConstants.AUTO_TIER_BY_VARRAY_URL);
        if ((provisioningType != null) && (provisioningType.length() > 0)) {
            builder.queryParam("provisioning_type", provisioningType);
        }
        if (uniqueNames != null) {
            builder.queryParam("unique_auto_tier_policy_names", uniqueNames);
        }
        return getList(builder.build(virtualArrayId));
    }
    
    /**
     * Gets all auto tier policies for a given virtual array.
     * 
     * @param virtualArrayId
     *        the ID of the virtual array.
     * @param provisioningType
     *        the provisioning type, if null matches any provisioning type.
     * @param uniqueNames
     *        when true duplicate named policies will be ignored.
     * @param filter
     *        filter used to filter results       
     * @return the list of auto tier policies.
     * 
     * @see #listByVirtualArray(URI, String, Boolean)
     * @see #getByRefs(java.util.Collection)
     * @see VirtualArrays
     */
    public List<AutoTieringPolicyRestRep> getByVirtualArray(URI virtualArrayId, String provisioningType,
            Boolean uniqueNames, ResourceFilter<AutoTieringPolicyRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByVirtualArray(virtualArrayId, provisioningType, uniqueNames);
        
        return getByRefs(refs, filter);
    }
    
    /**
     * Gets all auto tier policies for all virtual arrays.
     * 
     * @param virtualArrayId
     *        the ID of the virtual array.
     * @param provisioningType
     *        the provisioning type, if null matches any provisioning type.
     * @param uniqueNames
     *        when true duplicate named policies will be ignored.      
     * @param filter 
     * @return the list of auto tier policies.
     * 
     * @see #getByRefs(java.util.Collection)
     * @see VirtualArrays
     */
    public List<AutoTieringPolicyRestRep> getByVirtualArrays(Collection<URI> virtualArrayIds, String provisioningType, Boolean uniqueNames, ResourceFilter<AutoTieringPolicyRestRep> filter) {
        UriBuilder builder = client.uriBuilder(PathConstants.AUTO_TIER_FOR_ALL_VARRAY);
        if ((provisioningType != null) && (provisioningType.length() > 0)) {
            builder.queryParam("provisioning_type", provisioningType);
        }
        if (uniqueNames != null) {
            builder.queryParam("unique_auto_tier_policy_names", uniqueNames);
        }
        
        BulkIdParam input = new BulkIdParam((List<URI>) virtualArrayIds);
        
        AutoTierPolicyList response = client.postURI(AutoTierPolicyList.class, input, builder.build());
        
        return defaultList(getByRefs(response.getAutoTierPolicies(), filter));
    }
    
    /**
     * Gets all auto tier policies for a given virtual array. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @param virtualArrayId
     *        the ID of the virtual array.
     * @param provisioningType
     *        the provisioning type, if null matches any provisioning type.
     * @param uniqueNames
     *        when true duplicate named policies will be ignored.       
     * @return the list of auto tier policies.
     * 
     * @see #getByVirtualArray(URI, String, Boolean, ResourceFilter)
     * @see VirtualArrays
     */
    public List<AutoTieringPolicyRestRep> getByVirtualArray(URI virtualArrayId, String provisioningType,
            Boolean uniqueNames) {
        return getByVirtualArray(virtualArrayId, provisioningType, uniqueNames, null);
    }

    /**
     * Gets all auto tier policies for a given virtual array.
     * 
     * @param virtualArrayId
     *        the ID of the virtual array.
     * @return the list of auto tier policies.
     * 
     * @see #listByVirtualArray(URI)
     * @see #getByRefs(java.util.Collection)
     * @see VirtualArrays
     */
    public List<AutoTieringPolicyRestRep> getByVirtualArray(URI virtualArrayId) {
        List<NamedRelatedResourceRep> refs = listByVirtualArray(virtualArrayId);
        return getByRefs(refs);
    }
}
