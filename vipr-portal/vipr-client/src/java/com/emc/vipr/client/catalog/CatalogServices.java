/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.client.core.ACLResources;
import com.emc.vipr.client.core.TenantResources;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.CatalogServiceBulkRep;
import com.emc.vipr.model.catalog.CatalogServiceCreateParam;
import com.emc.vipr.model.catalog.CatalogServiceList;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.CatalogServiceUpdateParam;

public class CatalogServices extends AbstractCatalogBulkResources<CatalogServiceRestRep> implements ACLResources,
        TenantResources<CatalogServiceRestRep> {

    public CatalogServices(ViPRCatalogClient2 parent, RestClient client) {
        super(parent, client, CatalogServiceRestRep.class, PathConstants.CATALOG_SERVICE_URL);
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<CatalogServiceRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<CatalogServiceRestRep> getByUserTenant(ResourceFilter<CatalogServiceRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.CATALOG_SERVICE_URL);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }
        CatalogServiceList response = client.getURI(CatalogServiceList.class, uriBuilder.build());
        return ResourceUtils.defaultList(response.getCatalogServices());
    }

    @Override
    public List<CatalogServiceRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<CatalogServiceRestRep> getByTenant(URI tenantId, ResourceFilter<CatalogServiceRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    @Override
    protected List<CatalogServiceRestRep> getBulkResources(BulkIdParam input) {
        CatalogServiceBulkRep response = client.post(CatalogServiceBulkRep.class, input, getBulkUrl());
        return defaultList(response.getCatalogServices());
    }

    /**
     * Creates a catalog service
     * <p>
     * API Call: <tt>POST /catalog/services</tt>
     * 
     * @param input
     *            the catalog service configuration.
     * @return the newly created catalog service.
     */
    public CatalogServiceRestRep create(CatalogServiceCreateParam input) {
        CatalogServiceRestRep catalogService = client
                .post(CatalogServiceRestRep.class, input, PathConstants.CATALOG_SERVICE_URL);
        return catalogService;
    }

    /**
     * Updates the given catalog service by ID.
     * <p>
     * API Call: <tt>PUT /catalog/services/{id}</tt>
     * 
     * @param id
     *            the ID of the catalog service to update.
     * @param input
     *            the update configuration.
     * @return the updated catalog service.
     */
    public CatalogServiceRestRep update(URI id, CatalogServiceUpdateParam input) {
        return client.put(CatalogServiceRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given catalog service by ID.
     * <p>
     * API Call: <tt>POST /catalog/services/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of catalog service to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    /**
     * Return the list of catalog services contained within supplied category id
     * <p>
     * API Call: <tt>POST /catalog/categories/{id}/services</tt>
     * 
     * @param id
     *            the ID of the catalog service
     */
    public List<CatalogServiceRestRep> findByCatalogCategory(URI catalogCategoryId) {
        CatalogServiceList response = client.get(CatalogServiceList.class, PathConstants.CATALOG_SUB_SERVICES_URL, catalogCategoryId);
        return getByRefs(response.getCatalogServices());
    }

    /**
     * Return the list of recent services used by the current user
     * <p>
     * API Call: <tt>GET /catalog/services/recent</tt>
     * 
     */
    public List<CatalogServiceRestRep> getRecentServices() {
        CatalogServiceList response = client.get(CatalogServiceList.class, PathConstants.CATALOG_SERVICE_RECENT_URL);
        return getByRefs(response.getCatalogServices());
    }

    @Override
    public List<ACLEntry> getACLs(URI id) {
        return doGetACLs(id);
    }

    @Override
    public List<ACLEntry> updateACLs(URI id, ACLAssignmentChanges aclChanges) {
        return doUpdateACLs(id, aclChanges);
    }

    public void moveUp(URI catalogServiceId) {
        client.put(String.class, PathConstants.CATALOG_SERVICE_MOVE_UP_URL, catalogServiceId);
    }

    public void moveDown(URI catalogServiceId) {
        client.put(String.class, PathConstants.CATALOG_SERVICE_MOVE_DOWN_URL, catalogServiceId);
    }

    public void moveUpField(URI catalogServiceId, String fieldName) {
        client.put(String.class, PathConstants.CATALOG_SERVICE_FIELD_MOVE_UP_URL, catalogServiceId, fieldName);
    }

    public void moveDownField(URI catalogServiceId, String fieldName) {
        client.put(String.class, PathConstants.CATALOG_SERVICE_FIELD_MOVE_DOWN_URL, catalogServiceId, fieldName);
    }

}
