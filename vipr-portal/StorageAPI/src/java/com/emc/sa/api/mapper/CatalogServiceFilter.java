/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.db.client.URIUtil.uri;

import com.emc.sa.catalog.CatalogCategoryManager;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList.TenantResourceFilter;
import com.emc.storageos.security.authentication.StorageOSUser;

public class CatalogServiceFilter
        extends TenantResourceFilter<CatalogService> {

    private CatalogCategoryManager catalogCategoryManager;

    public CatalogServiceFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper, CatalogCategoryManager catalogCategoryManager) {
        super(user, permissionsHelper);
        this.catalogCategoryManager = catalogCategoryManager;
    }

    @Override
    public boolean isAccessible(CatalogService resource) {
        if (resource.getCatalogCategoryId() != null) {
            CatalogCategory catalogCategory = catalogCategoryManager.getCatalogCategoryById(resource.getCatalogCategoryId().getURI());
            return isTenantResourceAccessible(uri(catalogCategory.getTenant()));
        }
        return false;
    }
}
