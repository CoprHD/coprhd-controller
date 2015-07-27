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
import com.emc.vipr.model.catalog.CatalogImageBulkRep;
import com.emc.vipr.model.catalog.CatalogImageCreateParam;
import com.emc.vipr.model.catalog.CatalogImageList;
import com.emc.vipr.model.catalog.CatalogImageRestRep;
import com.emc.vipr.model.catalog.CatalogImageUpdateParam;

public class CatalogImages extends AbstractCatalogBulkResources<CatalogImageRestRep> implements
        TenantResources<CatalogImageRestRep> {

    public CatalogImages(ViPRCatalogClient2 parent, RestClient client) {
        super(parent, client, CatalogImageRestRep.class, PathConstants.CATALOG_IMAGE_URL);
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<CatalogImageRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<CatalogImageRestRep> getByUserTenant(ResourceFilter<CatalogImageRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.CATALOG_IMAGE_URL);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }
        CatalogImageList response = client.getURI(CatalogImageList.class, uriBuilder.build());
        return ResourceUtils.defaultList(response.getCatalogImages());    
    }

    @Override
    public List<CatalogImageRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<CatalogImageRestRep> getByTenant(URI tenantId, ResourceFilter<CatalogImageRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    @Override
    protected List<CatalogImageRestRep> getBulkResources(BulkIdParam input) {
        CatalogImageBulkRep response = client.post(CatalogImageBulkRep.class, input, getBulkUrl());
        return defaultList(response.getCatalogImages());
    }
    
    /**
     * Creates a catalog image
     * <p>
     * API Call: <tt>POST /catalog/images</tt>
     * 
     * @param input
     *        the catalog image configuration.
     * @return the newly created catalog image.
     */
    public CatalogImageRestRep create(CatalogImageCreateParam input) {
        CatalogImageRestRep catalogImage = client
                .post(CatalogImageRestRep.class, input, PathConstants.CATALOG_IMAGE_URL);
        return catalogImage;
    }

    /**
     * Updates the given catalog image by ID.
     * <p>
     * API Call: <tt>PUT /catalog/images/{id}</tt>
     * 
     * @param id
     *        the ID of the catalog image to update.
     * @param input
     *        the update configuration.
     * @return the updated catalog image.
     */
    public CatalogImageRestRep update(URI id, CatalogImageUpdateParam input) {
        return client.put(CatalogImageRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given catalog image by ID.
     * <p>
     * API Call: <tt>POST /catalog/images/{id}/deactivate</tt>
     * 
     * @param id
     *        the ID of catalog image to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }        

}
