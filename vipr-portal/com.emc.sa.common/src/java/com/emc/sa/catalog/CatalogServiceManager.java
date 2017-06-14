/*
 * Copyright 2015-2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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

    public String getWorkflowDocument(String workflowName);

}
