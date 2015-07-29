/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import com.emc.vipr.client.ViPRCatalogClient;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.ExecutionWindowInfo;
import com.emc.vipr.model.catalog.Reference;

import java.util.List;

import static com.emc.vipr.client.catalog.impl.PathConstants.*;

/**
 * 
 * @deprecated Replaced by
 * @see ExecutionWindows2
 * 
 */
@Deprecated
public class ExecutionWindows extends AbstractResources<ExecutionWindowInfo> {
    public ExecutionWindows(ViPRCatalogClient parent, RestClient client) {
        super(parent, client, ExecutionWindowInfo.class, EXECUTION_WINDOWS_URL);
    }

    /**
     * Lists Execution Windows.
     * <p>
     * API Call: GET /admin/api/executionwindows
     * 
     * @return References to approval requests.
     */
    @Deprecated
    public List<Reference> list() {
        return doList();
    }

    /**
     * Retrieves all execution windows. Convenience method to list windows and retrieve all.
     * <p>
     * API Call: GET /admin/api/executionwindows
     * 
     * @see #list()
     * @see #getByRefs(java.util.Collection)
     * @return All approval requests
     */
    @Deprecated
    public List<ExecutionWindowInfo> getAll() {
        return doGetAll();
    }

    /**
     * Create a new execution window.
     * <p>
     * API Call: POST /admin/api/executionwindows
     * </p>
     * 
     * @param window Execution window create information
     */
    @Deprecated
    public ExecutionWindowInfo create(ExecutionWindowInfo window) {
        return client.post(ExecutionWindowInfo.class, window, baseUrl);
    }

    /**
     * Updates and existing execution window.
     * <p>
     * API Call: PUT /admin/api/executionwindows/{id}
     * </p>
     * 
     * @param id Identifier of the execution window to update
     * @param window Execution window update information
     */
    @Deprecated
    public ExecutionWindowInfo update(String id, ExecutionWindowInfo window) {
        return client.put(ExecutionWindowInfo.class, window, getIdUrl(), id);
    }

    /**
     * Deletes an execution window.
     * <p>
     * API Call: DELETE /admin/api/executionwindows/{id}
     * </p>
     * 
     * @param id Identifier of the execution window to delete.
     */
    @Deprecated
    public void delete(String id) {
        client.delete(String.class, getIdUrl(), id);
    }
}
