/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import com.emc.vipr.model.catalog.ModelInfo;
import com.emc.vipr.model.catalog.Reference;
import com.emc.vipr.client.ViPRCatalogClient;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.GenericType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static com.emc.vipr.client.catalog.impl.ApiListUtils.getApiList;
import static com.emc.vipr.client.catalog.impl.PathConstants.ID_URL_FORMAT;

public class AbstractResources<T extends ModelInfo> {
    protected final ViPRCatalogClient parent;
    protected final RestClient client;
    protected final Class<T> resourceClass;
    protected final String baseUrl;

    public AbstractResources(ViPRCatalogClient parent, RestClient client, Class<T> resourceClass, String baseUrl) {
        this.parent = parent;
        this.client = client;
        this.baseUrl = baseUrl;
        this.resourceClass = resourceClass;
    }

    protected String getIdUrl() {
        return String.format(ID_URL_FORMAT, baseUrl);
    }

    /**
     * Gets a single resource by ID.
     * <p>
     * API Call: GET /api/RESOURCE/{id}
     * 
     * @param id identifier.
     * @return The resource.
     */
    public T get(String id) {
        return client.get(resourceClass, getIdUrl(), id);
    }

    /**
     * Gets a number of resources by their identifier.
     * 
     * @param ids identifiers.
     * @return List of resources.
     */
    public List<T> getByIds(Collection<String> ids) {
        List<T> results = new ArrayList<T>();
        if (ids != null) {
            for (String id : ids) {
                T item = get(id);
                if (item != null) {
                    results.add(item);
                }
            }
        }
        return results;
    }

    /**
     * Gets a list of resources by references. Convenience method that calls getByIds.
     * 
     * @see #getByIds(java.util.Collection)
     * @param refs references.
     * @return List of resources.
     */
    public List<T> getByRefs(Collection<Reference> refs) {
        return getByIds(refIds(refs));
    }

    protected List<Reference> doList() {
        List<Reference> apiList = getApiList(client, new GenericType<List<Reference>>() {
        }, baseUrl);
        return apiList;
    }

    protected List<T> doGetAll() {
        return getByRefs(doList());
    }

    private static List<String> refIds(Collection<? extends Reference> refs) {
        List<String> ids = new ArrayList<String>();
        if (refs != null) {
            for (Reference ref : refs) {
                ids.add(ref.getId());
            }
        }
        return ids;
    }
}
