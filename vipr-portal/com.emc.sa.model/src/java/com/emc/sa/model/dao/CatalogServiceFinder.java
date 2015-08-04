/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.google.common.collect.Lists;

public class CatalogServiceFinder extends ModelFinder<CatalogService> {

    public CatalogServiceFinder(DBClientWrapper client) {
        super(CatalogService.class, client);
    }

    public List<CatalogService> findByCatalogCategory(URI catalogCategoryId) {

        List<CatalogService> results = Lists.newArrayList();

        List<NamedElement> catalogServiceIds = client.findBy(CatalogService.class, CatalogService.CATALOG_CATEGORY_ID, catalogCategoryId);
        if (catalogServiceIds != null) {
            results.addAll(findByIds(toURIs(catalogServiceIds)));
        }

        SortedIndexUtils.sort(results);

        return results;
    }

    public Map<URI, Set<String>> findPermissions(StorageOSUser user, URI tenantId) {
        return findPermissions(this.clazz, user, tenantId);
    }

    public Map<URI, Set<String>> findPermissions(StorageOSUser user, URI tenantId, Set<String> filterBy) {
        return findPermissions(this.clazz, user, tenantId, filterBy);
    }

}
