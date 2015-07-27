/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.TagAssignment;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.search.SearchBuilder;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface Resources<T extends DataObjectRestRep> {
    /**
     * <p>Gets a resource by ID.
     *
     * <p>API Call: GET /<i>RESOURCE_PATH</i>/{id}
     *
     * @param id
     *        the resource ID.
     * @return the resource.
     */
    public T get(URI id);

    /**
     * <p>Gets a resource by a reference.
     *
     * <p>Convenience method for calling get(ref.getId()).
     *
     * @param ref
     *        the resource reference.
     * @return the resource.
     */
    public T get(RelatedResourceRep ref);

    /**
     * <p>Fetches the resource values for the given references.
     *
     * <p>Convenience method for calling getByIds(*resources.id).
     *
     * @param refs
     *        the resource references.
     * @return the resource values.
     */
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> refs);

    /**
     * <p>Fetches the resource values for the given references, optionally filtering the results as they are returned.
     *
     * <p>Convenience method for calling getByIds(*resources.id, filter).
     *
     * @param refs
     *        the resource references.
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the resource values.
     */
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> refs, ResourceFilter<T> filter);

    /**
     * <p>Fetches the resource values for the given IDs.
     *
     * <p>API Call: GET /<i>RESOURCE_PATH</i>/{id}
     *
     * @param ids
     *        the resource IDs.
     * @return the resource values.
     */
    public List<T> getByIds(Collection<URI> ids);

    /**
     * <p>Fetches the resource values for the given IDs, optionally filtering the results as they are returned.
     *
     * <p>API Call: GET /<i>RESOURCE_PATH</i>/{id}
     *
     * @param ids
     *        the resource IDs.
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the resource values.
     */
    public List<T> getByIds(Collection<URI> ids, ResourceFilter<T> filter);

    /**
     * Gets the set of all tags assigned to the given resource.
     *
     * @param id
     *        the ID of the resource.
     * @return the set of tags assigned to the given resource.
     */
    public Set<String> getTags(URI id);

    /**
     * Updates the tags for a given resource.
     *
     * @param id
     *        the ID of the resource.
     * @param tags
     *        the tags to add/remove.
     */
    public void updateTags(URI id, TagAssignment tags);

    /**
     * Adds a set of tags to the tags for a given resource.
     *
     * @param id
     *        the ID of the resource.
     * @param add
     *        the tags to add.
     */
    public void addTags(URI id, Set<String> add);

    /**
     * Removes a set of tags to the tags for a given resource.
     *
     * @param id
     *        the ID of the resource.
     * @param remove
     *        the tags to remove.
     */
    public void removeTags(URI id, Set<String> remove);

    /**
     * <p>Creates a builder for building up search queries.</p>
     *
     * @return Returns a search builder.
     */
    public SearchBuilder<T> search();
}
