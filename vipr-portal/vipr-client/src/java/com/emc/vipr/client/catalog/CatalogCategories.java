/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;
import static com.emc.vipr.client.core.util.ResourceUtils.asString;

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
import com.emc.vipr.model.catalog.CatalogCategoryBulkRep;
import com.emc.vipr.model.catalog.CatalogCategoryCreateParam;
import com.emc.vipr.model.catalog.CatalogCategoryList;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogCategoryUpdateParam;
import com.emc.vipr.model.catalog.CatalogUpgrade;

public class CatalogCategories extends AbstractCatalogBulkResources<CatalogCategoryRestRep> implements ACLResources,
        TenantResources<CatalogCategoryRestRep> {

    private static final String TENANT_ID_PARAM = "tenantId";

    public CatalogCategories(ViPRCatalogClient2 parent, RestClient client) {
        super(parent, client, CatalogCategoryRestRep.class, PathConstants.CATALOG_CATEGORY_URL);
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<CatalogCategoryRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<CatalogCategoryRestRep> getByUserTenant(ResourceFilter<CatalogCategoryRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {

        CatalogCategoryRestRep rootCatalogCategory =
                getRootCatalogCategory(asString(tenantId));

        UriBuilder uriBuilder = client.uriBuilder(PathConstants.CATALOG_SUB_CATEGORIES_URL);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }

        CatalogCategoryList response = client.getURI(
                CatalogCategoryList.class, uriBuilder.build(rootCatalogCategory.getId()));

        return ResourceUtils.defaultList(response.getCatalogCategories());
    }

    @Override
    public List<CatalogCategoryRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<CatalogCategoryRestRep> getByTenant(URI tenantId, ResourceFilter<CatalogCategoryRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    @Override
    protected List<CatalogCategoryRestRep> getBulkResources(BulkIdParam input) {
        CatalogCategoryBulkRep response = client.post(CatalogCategoryBulkRep.class, input, getBulkUrl());
        return defaultList(response.getCatalogCategories());
    }

    /**
     * Creates a catalog category
     * <p>
     * API Call: <tt>POST /catalog/categories</tt>
     * 
     * @param input
     *            the catalog category configuration.
     * @return the newly created catalog category.
     */
    public CatalogCategoryRestRep create(CatalogCategoryCreateParam input) {
        CatalogCategoryRestRep catalogCategory = client
                .post(CatalogCategoryRestRep.class, input, PathConstants.CATALOG_CATEGORY_URL);
        return catalogCategory;
    }

    /**
     * Get root catalog category
     * <p>
     * API Call: <tt>GET /catalog/categories</tt>
     * 
     * @param tenantId
     * @return the root catalog category.
     */
    public CatalogCategoryRestRep getRootCatalogCategory(String tenantId) {
        return getRootCatalogCategory(ResourceUtils.uri(tenantId));
    }

    public CatalogCategoryRestRep getRootCatalogCategory(URI tenantId) {
        UriBuilder builder = client.uriBuilder(PathConstants.CATALOG_CATEGORY_URL);
        if (tenantId != null) {
            builder.queryParam(TENANT_ID_PARAM, tenantId);
        }
        CatalogCategoryRestRep catalogCategory = client.getURI(CatalogCategoryRestRep.class, builder.build());
        return catalogCategory;
    }

    /**
     * Updates the given catalog category by ID.
     * <p>
     * API Call: <tt>PUT /catalog/categories/{id}</tt>
     * 
     * @param id
     *            the ID of the catalog category to update.
     * @param input
     *            the update configuration.
     * @return the updated catalog category.
     */
    public CatalogCategoryRestRep update(URI id, CatalogCategoryUpdateParam input) {
        return client.put(CatalogCategoryRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given catalog category by ID.
     * <p>
     * API Call: <tt>POST /catalog/categories/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of catalog category to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    /**
     * Reset the catalog to the default
     * <p>
     * API Call: <tt>POST /catalog/categories/reset</tt>
     * 
     * @param tenantId 
     *            the ID of the tenant
     */
    public void resetCatalog(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.CATALOG_RESET_URL);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }
        client.postURI(String.class, uriBuilder.build());
    }

    /**
     * Return the list of catalog categories contained within supplied category id
     * <p>
     * API Call: <tt>POST /catalog/categories/{id}/categories</tt>
     * 
     * @param catalogCategoryId
     *            the ID of the catalog category
     */
    public List<CatalogCategoryRestRep> getSubCategories(URI catalogCategoryId) {
        CatalogCategoryList response = client.get(CatalogCategoryList.class, PathConstants.CATALOG_SUB_CATEGORIES_URL, catalogCategoryId);
        return getByRefs(response.getCatalogCategories());
    }

    /**
     * Determines if an upgrade is available to the service catalog
     * <p>
     * API Call: <tt>GET /catalog/upgrade</tt>
     * 
     * @param tenantId
     *            the ID of the tenant
     */
    public boolean upgradeAvailable(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.CATALOG_UPGRADE_URI);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }
        CatalogUpgrade response = client.getURI(CatalogUpgrade.class, uriBuilder.build());
        return response != null && response.getUpgradeAvailable();
    }

    /**
     * Upgrades the service catalog, if available
     * <p>
     * API Call: <tt>POST /catalog/upgrade</tt>
     * 
     * @param tenantId
     *            the ID of the tenant
     */
    public void upgradeCatalog(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.CATALOG_UPGRADE_URI);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }
        client.postURI(String.class, uriBuilder.build());
    }

    @Override
    public List<ACLEntry> getACLs(URI id) {
        return doGetACLs(id);
    }

    @Override
    public List<ACLEntry> updateACLs(URI id, ACLAssignmentChanges aclChanges) {
        return doUpdateACLs(id, aclChanges);
    }

    public void moveUp(URI catalogCategoryId) {
        client.put(String.class, PathConstants.CATALOG_CATEGORY_MOVE_UP_URL, catalogCategoryId);
    }

    public void moveDown(URI catalogCategoryId) {
        client.put(String.class, PathConstants.CATALOG_CATEGORY_MOVE_DOWN_URL, catalogCategoryId);
    }

}
