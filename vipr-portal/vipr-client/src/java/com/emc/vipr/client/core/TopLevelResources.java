/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.util.List;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.vipr.client.core.filters.ResourceFilter;

/**
 * Interface for resources that are considered top-level (independent of other types).
 *
 * @param <T> the resource type.
 */
public interface TopLevelResources<T extends DataObjectRestRep> {
    /**
     * <p>Lists named resources (name + ID) for this resource type.
     *
     * <p>API Call: GET /<i>RESOURCE_PATH</i>
     *
     * @return the named resources.
     */
    public List<? extends NamedRelatedResourceRep> list();

    /**
     * <p>Gets all resources for this resource type. This is combination of the list() call combined with a bulk query
     * to retrieve the resources.
     *
     * <p>API Call: GET /<i>RESOURCE_PATH</i>
     *
     * @return All resources of this type available to the user.
     * 
     * @see #list()
     * @see Resources#getByRefs(java.util.Collection)
     */
    public List<T> getAll();

    /**
     * <p>Gets all resources for this resource type. This is combination of the list() call combined with a bulk query
     * to retrieve the resources. This optionally filters the results by the filter specified.
     *
     * <p>API Call: GET /<i>RESOURCE_PATH</i>
     *
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return Filtered list of resources of this type available to the user.
     * 
     * @see #list()
     * @see Resources#getByRefs(java.util.Collection, ResourceFilter)
     */
    public List<T> getAll(ResourceFilter<T> filter);
}
