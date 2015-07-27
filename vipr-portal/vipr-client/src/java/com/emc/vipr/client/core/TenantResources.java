/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.vipr.client.core.filters.ResourceFilter;
import java.net.URI;
import java.util.List;

public interface TenantResources<T extends DataObjectRestRep> {
    /**
     * Convenience method for listing by the user's current tenant.
     * <p>
     * Equivalent to: listByTenant(client.getUserTenant())
     * 
     * @see #listByTenant(URI)
     */
    public List<NamedRelatedResourceRep> listByUserTenant();

    /**
     * Convenience method for retrieving by user's current tenant.
     * <p>
     * Equivalent to: getByTenant(client.getUserTenant())
     * 
     * @see #getByTenant(URI)
     */
    public List<T> getByUserTenant();

    /**
     * Convenience method for retrieving by user's current tenant.
     * <p>
     * Equivalent to: getByTenant(client.getUserTenant(), filter)
     * 
     * @see #getByTenant(URI, ResourceFilter)
     */
    public List<T> getByUserTenant(ResourceFilter<T> filter);

    /**
     * Lists resources associated with a given tenant.
     * 
     * @param tenantId
     *        the tenant ID.
     * @return the list of resource references.
     */
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId);

    /**
     * Gets the resources associated with a given tenant.
     * 
     * @param tenantId
     *        the tenant ID.
     * @return the list of resources for this tenant.
     * 
     * @see Resources#getByRefs(java.util.Collection)
     */
    public List<T> getByTenant(URI tenantId);

    /**
     * Gets the resources associated with a given tenant, optionally filtering the results as they are returned.
     * 
     * @param tenantId
     *        the tenant ID.
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the list of resources for the tenant.
     * 
     * @see Resources#getByRefs(java.util.Collection, ResourceFilter)
     */
    public List<T> getByTenant(URI tenantId, ResourceFilter<T> filter);
}
