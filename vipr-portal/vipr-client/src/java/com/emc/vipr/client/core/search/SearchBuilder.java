/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.search;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.vipr.client.core.filters.FilterChain;
import com.emc.vipr.client.core.filters.NameFilter;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.AbstractResources;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.emc.vipr.client.core.impl.SearchConstants.*;

/**
 * A builder for search queries.
 * 
 * @param <T> Resource type returned by this search builder.
 */
public class SearchBuilder<T extends DataObjectRestRep> {
    private AbstractResources<T> resources;
    private ResourceFilter<T> filter = null;
    private Map<String, Object> parameters = new HashMap<String, Object>();

    public SearchBuilder(AbstractResources<T> resources) {
        this.resources = resources;
    }

    /**
     * Adds a single parameter to search by.
     * 
     * @param name
     *            the parameter name.
     * @param value
     *            the parameter value.
     * @return This SearchBuilder.
     */
    public SearchBuilder<T> by(String name, Object value) {
        parameters.put(name, value);
        return this;
    }

    /**
     * Adds multiple search parameters to search by.
     * 
     * @param parameters Map containing name value pairs
     * @return This SearchBuilder.
     */
    public SearchBuilder<T> byAll(Map<String, Object> parameters) {
        this.parameters.putAll(parameters);
        return this;
    }

    /**
     * Filters the results returned by the search query. Multiple filters
     * can be added.
     * 
     * @param addFilter Filter to use for filtering the search results.
     * @return This SearchBuilder.
     */
    public SearchBuilder<T> filter(ResourceFilter<T> addFilter) {
        if (filter == null) {
            filter = addFilter;
        }
        else if (addFilter != null) {
            filter = new FilterChain<T>(filter).and(addFilter);
        }
        return this;
    }

    /**
     * Shortcut to search by name.
     * 
     * @param name Name of the resource.
     * @return This SearchBuilder.
     */
    public SearchBuilder<T> byName(String name) {
        return by(NAME_PARAM, name);
    }

    /**
     * Shortcut to search by exact name. This both searches by name and
     * adds a filter to ensure the exact name string is matched.
     * 
     * @param name Name of the resource.
     * @return This SearchBuilder.
     */
    public SearchBuilder<T> byExactName(String name) {
        return byName(name).filter(new NameFilter<T>(name));
    }

    /**
     * Shortcut to search by tag.
     * 
     * @param tag Tag on the resource.
     * @return This SearchBuilder.
     */
    public SearchBuilder<T> byTag(String tag) {
        return by(TAG_PARAM, tag);
    }

    /**
     * Runs a search based on the criteria built with this SearchBuilder. Only
     * the first search result is returned.
     * 
     * @return The first search result.
     */
    public T first() {
        // If there is no filter, we can run the search and just query the first item
        if (filter == null) {
            List<SearchResultResourceRep> resultRefs = resources.performSearch(parameters);
            if (resultRefs.isEmpty()) {
                return null;
            }
            return resources.get(resultRefs.get(0));
        }
        // If there is a filter, we need to run a full search to ensure filtering is done
        else {
            List<T> items = run();
            if (items.isEmpty()) {
                return null;
            }
            return items.get(0);
        }
    }

    /**
     * Runs a search based on the criteria built with this SearchBuilder. All
     * search results are queried and returned.
     * 
     * @return All search results.
     */
    public List<T> run() {
        return resources.getByRefs(resources.performSearch(parameters), filter);
    }
}
