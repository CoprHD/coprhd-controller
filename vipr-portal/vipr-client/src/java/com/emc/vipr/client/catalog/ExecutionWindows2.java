/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.client.core.TenantResources;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.ExecutionWindowBulkRep;
import com.emc.vipr.model.catalog.ExecutionWindowCreateParam;
import com.emc.vipr.model.catalog.ExecutionWindowList;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.ExecutionWindowUpdateParam;

public class ExecutionWindows2 extends AbstractCatalogBulkResources<ExecutionWindowRestRep> implements
        TenantResources<ExecutionWindowRestRep> {

    public ExecutionWindows2(ViPRCatalogClient2 parent, RestClient client) {
        super(parent, client, ExecutionWindowRestRep.class, PathConstants.EXECUTION_WINDOWS2_URL);
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<ExecutionWindowRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<ExecutionWindowRestRep> getByUserTenant(ResourceFilter<ExecutionWindowRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.EXECUTION_WINDOWS2_URL);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }
        ExecutionWindowList response = client.getURI(ExecutionWindowList.class, uriBuilder.build());
        return ResourceUtils.defaultList(response.getExecutionWindows());        
    }

    @Override
    public List<ExecutionWindowRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<ExecutionWindowRestRep> getByTenant(URI tenantId, ResourceFilter<ExecutionWindowRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    @Override
    protected List<ExecutionWindowRestRep> getBulkResources(BulkIdParam input) {
        ExecutionWindowBulkRep response = client.post(ExecutionWindowBulkRep.class, input, getBulkUrl());
        return defaultList(response.getExecutionWindows());
    }

    /**
     * Creates a execution window 
     * <p>
     * API Call: <tt>POST /catalog/execution-windows</tt>
     * 
     * @param input
     *        the execution window configuration.
     * @return the newly created execution window.
     */
    public ExecutionWindowRestRep create(ExecutionWindowCreateParam input) {
        ExecutionWindowRestRep executionWindow = client
                .post(ExecutionWindowRestRep.class, input, PathConstants.EXECUTION_WINDOWS2_URL);
        return executionWindow;
    }

    /**
     * Updates the given execution window by ID.
     * <p>
     * API Call: <tt>PUT /catalog/execution-windows/{id}</tt>
     * 
     * @param id
     *        the ID of the execution window to update.
     * @param input
     *        the update configuration.
     * @return the updated execution window.
     */
    public ExecutionWindowRestRep update(URI id, ExecutionWindowUpdateParam input) {
        return client.put(ExecutionWindowRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given execution window by ID.
     * <p>
     * API Call: <tt>POST /catalog/execution-windows/{id}/deactivate</tt>
     * 
     * @param id
     *        the ID of execution window to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }    
    
}
