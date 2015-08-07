/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.util;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.refIds;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.client.core.Resources;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.exceptions.ViPRHttpException;

/**
 * Wrapper around resources that can cache the results. This can be quite useful when looking up related resources from
 * another type.
 * 
 * @param <T>
 *            the resource type.
 */
public class CachedResources<T extends DataObjectRestRep> {
    private static final int NOT_FOUND = 404;
    private final Resources<T> resources;
    private Map<URI, T> cache = new HashMap<URI, T>();

    public CachedResources(Resources<T> resources) {
        this.resources = resources;
    }

    /**
     * Gets a resource by ID, first checking if it is already in the cache. This will handle an HTTP 404 error by
     * caching and returning a null value.
     * 
     * @param id
     *            the resource ID.
     * @return the resource.
     */
    public T get(URI id) {
        if (id == null) {
            return null;
        }
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        try {
            T value = cache(resources.get(id));
            return value;
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == NOT_FOUND) {
                cache.put(id, null);
                return null;
            }
            throw e;
        }
    }

    /**
     * Gets a list resource by reference, first checking if it is already in the cache.
     * 
     * @param ref
     *            the resource reference.
     * @return the resource.
     */
    public T get(RelatedResourceRep ref) {
        return get(id(ref));
    }

    /**
     * Gets a list of resources by reference, checking the cache for each. This is a convenience method for
     * <code>getByRefs(refs, null)</code>.
     * 
     * @param refs
     *            the resource references.
     * @return the list of resources.
     * 
     * @see #getByRefs(Collection, ResourceFilter)
     */
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> refs) {
        return getByRefs(refs, null);
    }

    /**
     * Gets a list of resources by reference, checking the cache for each. If a filter is provided the values are
     * filtered as they are retrieved.
     * 
     * @param refs
     *            the resource references.
     * @param filter
     *            the filter to apply (may be null).
     * @return the list of resources.
     */
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> refs, ResourceFilter<T> filter) {
        return getByIds(refIds(refs), filter);
    }

    /**
     * Gets a list of resources by IDs, checking the cache for each. This is a convenience method for <code>getByIds(ids, null)</code>.
     * 
     * @param ids
     *            the resource IDs.
     * @return the resources.
     * 
     * @see #getByIds(Collection, ResourceFilter)
     */
    public List<T> getByIds(Collection<URI> ids) {
        return getByIds(ids, null);
    }

    /**
     * Gets a list of resources by IDs, checking the cache for each. If a filter is provided, the resources are filtered
     * as they are retrieved.
     * 
     * @param ids
     *            the resource IDs.
     * @param filter
     *            the filter to apply (may be null).
     * @return the resources.
     */
    public List<T> getByIds(Collection<URI> ids, ResourceFilter<T> filter) {
        List<T> values = new ArrayList<T>();
        Set<URI> fetchIds = new HashSet<URI>();
        for (URI id : ids) {
            // Skip IDs that are rejected by the filter
            if ((filter != null) && !filter.acceptId(id)) {
                continue;
            }

            // Bypass values that are already cached
            if (isCached(id)) {
                T value = getCached(id);
                if (value != null) {
                    values.add(value);
                }
            }
            else {
                fetchIds.add(id);
            }
        }
        // Fetch any missing values and store them into the cache
        if (!fetchIds.isEmpty()) {
            List<T> results = resources.getByIds(fetchIds, filter);
            for (T result : results) {
                values.add(cache(result));
            }
        }
        return values;
    }

    /**
     * Determines if the cache contains the given resource by ID
     * 
     * @param id
     *            resource ID.
     * @return true if the cache contains the resource.
     */
    public boolean isCached(URI id) {
        return cache.containsKey(id);
    }

    /**
     * Gets the resource from the cache only.
     * 
     * @param id
     *            the resource ID.
     * @return the cached resource.
     */
    public T getCached(URI id) {
        return cache.get(id);
    }

    /**
     * Adds the resource to the cache.
     * 
     * @param value
     *            the resource to cache.
     * @return the resource.
     */
    protected T cache(T value) {
        if (value != null) {
            cache.put(value.getId(), value);
        }
        return value;
    }

    /**
     * Clears the cached values.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Removes a resource from the cache.
     * 
     * @param id
     *            the resource ID.
     * @return the previously cached value.
     */
    public T remove(URI id) {
        return cache.remove(id);
    }

    /**
     * Removes a resource from the cache.
     * 
     * @param ref
     *            the resource reference.
     * @return the previously cached value.
     */
    public T remove(RelatedResourceRep ref) {
        return remove(id(ref));
    }
}
