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
    
    /**
     * Find all catalog service which are marked to run within an execution window. 
     * 
     * @param executionWindowId 
     * @return list of catalog service
     */
    public List<CatalogService> findByExecutionWindow(URI executionWindowId) {
        
        List<CatalogService> results = Lists.newArrayList();
        
        List<NamedElement> catalogServiceIds = client.findByAlternateId(CatalogService.class, CatalogService.DEFAULT_EXECUTION_WINDOW_ID, executionWindowId.toString() + ":ExecutionWindow");
        if (catalogServiceIds != null) {
            results.addAll(findByIds(toURIs(catalogServiceIds)));
        }
        
        // remove any service that isn't required to run in execution windows
        List<CatalogService> toRemove = Lists.newArrayList();
        for (CatalogService service : results) {
        	if (!service.getExecutionWindowRequired()) {
        		toRemove.add(service);
        	}
        }
        results.removeAll(toRemove);
        
        SortedIndexUtils.sort(results);
        
        return results;
    }
}
