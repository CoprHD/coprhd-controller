/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import com.emc.storageos.model.BulkIdParam;
import com.emc.vipr.client.ViPRCatalogClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.ModelInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Base class for all resources types that support bulk operations. This overrides the
 * {@link #getByIds(java.util.Collection)} method to query through the bulk API for improved performance.
 *
 * @param <T>
 *        the type of resource.
 */
public abstract class AbstractBulkResources<T extends ModelInfo> extends AbstractResources<T> {

    public AbstractBulkResources(ViPRCatalogClient parent, RestClient client, Class<T> resourceClass, String baseUrl) {
        super(parent, client, resourceClass, baseUrl);
    }

    /**
     * Gets the URL for bulk queries: <tt><i>baseUrl</i>/bulk</tt>
     * 
     * @return the bulk URL.
     */
    protected String getBulkUrl() {
        return String.format(PathConstants.BULK_URL_FORMAT, baseUrl);
    }

    @Override
    public List<T> getByIds(Collection<String> ids) {
        List<T> results = new ArrayList<T>();

        if (ids != null) {
            BulkIdParam input = new BulkIdParam();
            for (String id : ids) {
                addId(id, input, results);
            }
            if (!input.getIds().isEmpty()) {
                fetchChunk(input, results);
            }
        }

        return results;
    }

    /**
     * Adds an ID to the bulk input. If the fetch limit is reached, a chunk will be fetched and the input will be
     * cleared.
     * 
     * @param id
     *        the ID to add.
     * @param input
     *        the bulk input.
     * @param results
     *        the result list to add to if a chunk is fetched.
     */
    private void addId(String id, BulkIdParam input, List<T> results) {
        input.getIds().add(URI.create(id));
        if (input.getIds().size() >= client.getConfig().getBulkSize()) {
            fetchChunk(input, results);
            input.getIds().clear();
        }
    }

    /**
     * Fetches a chunk and filters (if required).
     * 
     * @param input
     *        the bulk input.
     * @param results
     *        the result list to add to.
     */
    private void fetchChunk(BulkIdParam input, List<T> results) {
        List<T> items = getBulkResources(input);
        for (T item : items) {
            results.add(item);
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
