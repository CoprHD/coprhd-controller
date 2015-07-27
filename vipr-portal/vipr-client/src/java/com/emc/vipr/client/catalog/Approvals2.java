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
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.client.catalog.search.ApprovalSearchBuilder;
import com.emc.vipr.client.core.TenantResources;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.ApprovalBulkRep;
import com.emc.vipr.model.catalog.ApprovalList;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.ApprovalUpdateParam;

public class Approvals2 extends AbstractCatalogBulkResources<ApprovalRestRep> implements TenantResources<ApprovalRestRep> {

    public static final String ORDER_ID_PARAM = "orderId";
    
    public Approvals2(ViPRCatalogClient2 parent, RestClient client) {
        super(parent, client, ApprovalRestRep.class, PathConstants.APPROVALS2_URL);
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<ApprovalRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<ApprovalRestRep> getByUserTenant(ResourceFilter<ApprovalRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.APPROVALS2_URL);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }
        ApprovalList response = client.getURI(ApprovalList.class, uriBuilder.build());
        return ResourceUtils.defaultList(response.getApprovals());             
    }

    @Override
    public List<ApprovalRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<ApprovalRestRep> getByTenant(URI tenantId, ResourceFilter<ApprovalRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    @Override
    protected List<ApprovalRestRep> getBulkResources(BulkIdParam input) {
        ApprovalBulkRep response = client.post(ApprovalBulkRep.class, input, getBulkUrl());
        return defaultList(response.getApprovals());
    }
    
    /**
     * Creates a search builder specifically for creating approval search queries.
     * 
     * @return a approval search builder.
     */
    @Override
    public ApprovalSearchBuilder search() {
        return new ApprovalSearchBuilder(this);
    }    

    /**
     * Updates the given approval by ID.
     * <p>
     * API Call: <tt>PUT /catalog/approvals/{id}</tt>
     * 
     * @param id
     *        the ID of the approval to update.
     * @param input
     *        the update configuration.
     * @return the updated approval.
     */
    public ApprovalRestRep update(URI id, ApprovalUpdateParam input) {
        return client.put(ApprovalRestRep.class, input, getIdUrl(), id);
    }

}
