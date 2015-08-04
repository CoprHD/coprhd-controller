/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceAndFields;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;
import com.emc.storageos.db.client.model.uimodels.RecentService;
import com.emc.storageos.security.authentication.StorageOSUser;

public interface CatalogServiceManager {

    public CatalogService getCatalogServiceById(URI id);

    public ServiceDescriptor getServiceDescriptor(String serviceId);

    public List<CatalogServiceAndFields> getCatalogServicesWithFields(List<URI> ids);

    public void createCatalogService(CatalogService catalogService, List<CatalogServiceField> catalogServiceFields);

    public void updateCatalogService(CatalogService catalogService, List<CatalogServiceField> catalogServiceFields);

    public void deleteCatalogService(CatalogService catalogService);

    public CatalogService createCatalogService(ServiceDef serviceDef, CatalogCategory parentCategory);

    public List<CatalogService> getCatalogServices(URI catalogCategoryId);

    @Deprecated
    public List<CatalogService> getRecentCatalogServices(StorageOSUser user);

    @Deprecated
    public List<RecentService> getRecentServices(String username);

    @Deprecated
    public List<RecentService> getRecentServices(CatalogService catalogService);

    public List<CatalogServiceField> getCatalogServiceFields(URI catalogServiceId);

    @Deprecated
    public void createRecentCatalogService(CatalogService catalogService, StorageOSUser user);

    public void moveUpCatalogService(URI catalogServiceId);

    public void moveDownCatalogService(URI catalogServiceId);

    public void moveUpCatalogServiceField(URI catalogServiceId, String fieldName);

    public void moveDownCatalogServiceField(URI catalogServiceId, String fieldName);

    public Map<String, String> getLockedFields(URI catalogServiceId);

    public String getLockedValue(CatalogServiceField field);

}
