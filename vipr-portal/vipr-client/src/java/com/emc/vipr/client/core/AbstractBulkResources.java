/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.refIds;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Base class for all resources types that support bulk operations. This overrides the
 * {@link #getByIds(Collection, ResourceFilter)} method to query through the bulk API for improved performance.
 * 
 * @param <T>
 *        the type of resource.
 */
public abstract class AbstractBulkResources<T extends DataObjectRestRep> extends AbstractResources<T> implements
        BulkResources<T> {

    public AbstractBulkResources(RestClient client, Class<T> resourceClass, String baseUrl) {
        super(client, resourceClass, baseUrl);
    }

    /**
     * Gets the URL for bulk queries: <tt><i>baseUrl</i>/bulk</tt>
     * 
     * @return the bulk URL.
     */
    protected String getBulkUrl() {
        return String.format(PathConstants.BULK_URL_FORMAT, baseUrl);
    }

    protected String getSearchUrl() {
        return String.format(PathConstants.SEARCH_URL_FORMAT, baseUrl);
    }

    @Override
    public List<URI> listBulkIds() {
        BulkIdParam response = client.get(BulkIdParam.class, getBulkUrl());
        return response.getIds();
    }

    @Override
    public List<T> getByIds(Collection<URI> ids, ResourceFilter<T> filter) {
        List<T> results = new ArrayList<T>();

        if (ids != null) {
            BulkIdParam input = new BulkIdParam();
            for (URI id : ids) {
                addId(id, input, filter, results);
            }
            if (!input.getIds().isEmpty()) {
                fetchChunk(input, filter, results);
            }
        }

        return results;
    }

    @Override
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> refs, ResourceFilter<T> filter) {
        return getByIds(refIds(refs), filter);
    }

    /**
     * Adds an ID to the bulk input. If the fetch limit is reached, a chunk will be fetched and the input will be
     * cleared.
     * 
     * @param id
     *        the ID to add.
     * @param input
     *        the bulk input.
     * @param filter
     *        the filter to apply (may be null, for no filtering).
     * @param results
     *        the result list to add to if a chunk is fetched.
     */
    private void addId(URI id, BulkIdParam input, ResourceFilter<T> filter, List<T> results) {
        if ((filter == null) || filter.acceptId(id)) {
            input.getIds().add(id);
        }
        if (input.getIds().size() >= client.getConfig().getBulkSize()) {
            fetchChunk(input, filter, results);
            input.getIds().clear();
        }
    }

    /**
     * Fetches a chunk and filters (if required).
     * 
     * @param input
     *        the bulk input.
     * @param filter
     *        the filter to apply (may be null, for no filtering).
     * @param results
     *        the result list to add to.
     */
    private void fetchChunk(BulkIdParam input, ResourceFilter<T> filter, List<T> results) {
        List<T> items = getBulkResources(input);
        for (T item : items) {
            if (accept(item, filter)) {
                results.add(item);
            }
        }
    }

    /**
     * Performs a bulk fetch of the IDs in the input.
     * 
     * @param input
     *        the bulk input.
     * @return the results.
     */
    protected abstract List<T> getBulkResources(BulkIdParam input);
}
