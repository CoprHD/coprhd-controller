/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.client.core.filters.ResourceFilter;

import java.net.URI;
import java.util.Collection;
import java.util.List;

public interface BulkResources<T extends DataObjectRestRep> extends Resources<T> {
    /**
     * <p>Lists all IDs for this resource type through the Bulk API. May require specific roles.
     *
     * <p>API Call: GET /<i>RESOURCE_PATH</i>/bulk
     *
     * @return the list of all IDs for this type of resource.
     */
    public List<URI> listBulkIds();

    /**
     * <p>Fetches the resource values for the given references.
     *
     * <p>Convenience method for calling getByIds(*resources.id).
     *
     * @param resources
     *        the resource references.
     * @return the resource values.
     */
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> resources);

    /**
     * <p>Fetches the resource values for the given references, optionally filtering the results as they are returned.
     *
     * <p>Convenience method for calling getByIds(*resources.id, filter).
     *
     * @param resources
     *        the resource references.
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the resource values.
     */
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> resources, ResourceFilter<T> filter);

    /**
     * <p>Fetches the resource values for the given IDs (using the bulk API).
     *
     * <p>API Call: POST /<i>RESOURCE_PATH</i>/bulk
     *
     * @param ids
     *        the resource IDs.
     * @return the resource values.
     */
    public List<T> getByIds(Collection<URI> ids);

    /**
     * <p>Fetches the resource values for the given IDs (using the bulk API), optionally filtering the results as they
     * are returned.
     *
     * <p>API Call: POST /<i>RESOURCE_PATH</i>/bulk
     *
     * @param ids
     *        the resource IDs.
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the resource values.
     */
    public List<T> getByIds(Collection<URI> ids, ResourceFilter<T> filter);
}
