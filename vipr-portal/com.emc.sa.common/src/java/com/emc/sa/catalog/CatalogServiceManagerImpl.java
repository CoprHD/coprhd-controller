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
package com.emc.sa.catalog;import static com.emc.storageos.db.client.URIUtil.uri;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import com.emc.storageos.db.client.URIUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.util.CreationTimeComparator;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.sa.util.ServiceIdPredicate;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceAndFields;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.RecentService;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
;

@Component
public class CatalogServiceManagerImpl implements CatalogServiceManager {

    private static final Logger log = Logger.getLogger(CatalogServiceManagerImpl.class);

    public static final int MAX_RECENT_SERVICES = 5;

    @Autowired
    private ModelClient client;

    @Autowired
    private OrderManager orderManager;

    @Autowired
    private ServiceDescriptors serviceDescriptors;

    @Autowired
    private CatalogCategoryManager catalogCategoryManager;
    
    @Autowired 
    private CustomServicesWorkflowManager customServicesWorkflowManager;

    public CatalogService getCatalogServiceById(URI id) {
        if (id == null) {
            return null;
        }

        CatalogService catalogService = client.catalogServices().findById(id);

        // For "Test Workflow" CustomServiceWorkflow ID is set as CatalogService.
        if (null == catalogService && id.toString().startsWith(CustomServicesWorkflow.ID_PREFIX)) {
            final CustomServicesWorkflow customServicesWorkflow = customServicesWorkflowManager.getById(id);
            if (customServicesWorkflow == null) {
                log.debug(String.format("Unable to get Catalog Service by Id [%s]. Workflow may have been deleted.", id));
                throw new IllegalStateException("Unable to get Catalog Service by Id" + id + "Workflow may have been deleted.");
            }
            catalogService = new CatalogService();
            catalogService.setId(id);
            catalogService.setTitle(customServicesWorkflow.getLabel());
            catalogService.setDescription(customServicesWorkflow.getLabel());
            catalogService.setImage("icon_orchestration.png");
            catalogService.setBaseService(URIUtil.asString(id));
        }

        return catalogService;
    }

    public ServiceDescriptor getServiceDescriptor(String serviceId) {

        if (serviceId == null) {
            return null;
        }

        return serviceDescriptors.getDescriptor(Locale.getDefault(), serviceId);
    }

    public List<CatalogServiceAndFields> getCatalogServicesWithFields(List<URI> ids) {
        List<CatalogServiceAndFields> catalogServicesWithFields =
                new ArrayList<CatalogServiceAndFields>();
        if (ids == null) {
            return null;
        }

        for (URI id : ids) {
            CatalogService catalogService = client.catalogServices().findById(id);
            if (catalogService != null) {
                List<CatalogServiceField> fields =
                        getCatalogServiceFields(catalogService.getId());
                SortedIndexUtils.sort(fields);
                CatalogServiceAndFields catalogServiceWithFields =
                        new CatalogServiceAndFields();
                catalogServiceWithFields.setCatalogService(catalogService);
                catalogServiceWithFields.setCatalogServiceFields(fields);
                catalogServicesWithFields.add(catalogServiceWithFields);
            }
        }

        return catalogServicesWithFields;
    }

    public void createCatalogService(CatalogService catalogService, List<CatalogServiceField> catalogServiceFields) {

        if (catalogService.getSortedIndex() == null) {
            catalogService.setSortedIndex(SortedIndexUtils.getNextSortedIndex(catalogService, client));
        }

        client.save(catalogService);

        if (catalogServiceFields != null) {
            for (CatalogServiceField catalogServiceField : catalogServiceFields) {

                if (catalogServiceField.getSortedIndex() == null) {
                    catalogServiceField.setSortedIndex(SortedIndexUtils.getNextSortedIndex(catalogServiceField, client));
                }

                client.save(catalogServiceField);
            }
        }
    }

    public void updateCatalogService(CatalogService catalogService, List<CatalogServiceField> catalogServiceFields) {

        if (catalogService.getSortedIndex() == null) {
            catalogService.setSortedIndex(SortedIndexUtils.getNextSortedIndex(catalogService, client));
        }

        client.save(catalogService);

        if (catalogServiceFields != null) {

            Map<String, CatalogServiceField> fields = toMap(catalogServiceFields);
            Map<String, CatalogServiceField> existingFields = toMap(client.catalogServiceFields().findByCatalogService(
                    catalogService.getId()));

            // Save Fields
            for (Entry<String, CatalogServiceField> fieldEntry : fields.entrySet()) {
                log.debug(String.format("Saving Catalog Service Field [%s]", fieldEntry.getKey()));

                if (fieldEntry.getValue().getSortedIndex() == null) {
                    fieldEntry.getValue().setSortedIndex(SortedIndexUtils.getNextSortedIndex(fieldEntry.getValue(), client));
                }
                client.save(fieldEntry.getValue());
            }

            // Remove Fields
            for (Entry<String, CatalogServiceField> existingFieldEntry : existingFields.entrySet()) {
                if (!fields.keySet().contains(existingFieldEntry.getKey())) {
                    log.debug(String.format("Removing Catalog Service Field [%s]", existingFieldEntry.getKey()));
                    client.delete(existingFieldEntry.getValue());
                }
            }
        }

    }

    public void deleteCatalogService(CatalogService catalogService) {
        CatalogCategory parentCatalogCategory = catalogCategoryManager.getCatalogCategoryById(catalogService.getCatalogCategoryId()
                .getURI());
        URI tenantId = uri(parentCatalogCategory.getTenant());

        if (isServiceUsedForOrders(tenantId, catalogService)) {
            URI deletedCategoryURI = URI.create(CatalogCategory.DELETED_CATEGORY);
            String deletedCategoryLabel = CatalogCategory.DELETED_CATEGORY;
            catalogService.setCatalogCategoryId(new NamedURI(deletedCategoryURI, deletedCategoryLabel));
            client.save(catalogService);
        }
        else {
            List<CatalogServiceField> serviceFields = getCatalogServiceFields(catalogService.getId());
            log.debug(String.format("Deleting Service Fields: %s", catalogService.getTitle()));
            client.delete(serviceFields);

            log.info(String.format("Deleting Service: %s", catalogService.getTitle()));
            client.delete(catalogService);
        }
    }

    public CatalogService createCatalogService(ServiceDef serviceDef, CatalogCategory parentCategory) {
        CatalogBuilder builder = new CatalogBuilder(client, serviceDescriptors);
        NamedURI namedUri = new NamedURI(parentCategory.getId(), parentCategory.getLabel());
        CatalogService newService = builder.createService(serviceDef, namedUri);
        newService.setSortedIndex(null);
        client.save(newService);
        return newService;
    }

    @Deprecated
    private void deleteRecentServices(CatalogService catalogService) {
        List<RecentService> recentServices = getRecentServices(catalogService);
        log.debug(String.format("Deleting Recent Services: %s", catalogService.getTitle()));
        client.delete(recentServices);
    }

    private boolean isServiceUsedForOrders(URI tenantId, CatalogService service) {
        String serviceId = service.getId().toString();
        List<Order> orders = orderManager.getOrders(tenantId);
        return CollectionUtils.exists(orders, new ServiceIdPredicate(serviceId));
    }

    public List<CatalogService> getCatalogServices(URI catalogCategoryId) {
        List<CatalogService> services = client.catalogServices().findByCatalogCategory(catalogCategoryId);
        SortedIndexUtils.sort(services);
        return services;
    }

    @Deprecated
    public List<CatalogService> getRecentCatalogServices(StorageOSUser user) {
        List<CatalogService> catalogServices = Lists.newArrayList();
        List<RecentService> recentServices = getRecentServices(user.getUserName());
        for (RecentService recentService : recentServices) {
            CatalogService catalogService = client.catalogServices().findById(recentService.getCatalogServiceId());
            if (catalogService != null) {
                catalogServices.add(catalogService);
            }
        }
        SortedIndexUtils.sort(catalogServices);
        return catalogServices;
    }

    @Deprecated
    public List<RecentService> getRecentServices(String username) {
        return client.recentServices().findByUserId(username);
    }

    @Deprecated
    public List<RecentService> getRecentServices(CatalogService catalogService) {
        return client.recentServices().findByCatalogService(catalogService.getId());
    }

    public List<CatalogServiceField> getCatalogServiceFields(URI catalogServiceId) {
        List<CatalogServiceField> fields = client.catalogServiceFields().findByCatalogService(catalogServiceId);
        SortedIndexUtils.sort(fields);
        return fields;
    }

    private Map<String, CatalogServiceField> toMap(List<CatalogServiceField> catalogServiceFields) {
        Map<String, CatalogServiceField> fields = Maps.newTreeMap();
        if (catalogServiceFields != null) {
            for (CatalogServiceField catalogServiceField : catalogServiceFields) {
                fields.put(catalogServiceField.getLabel(), catalogServiceField);
            }
        }
        return fields;
    }

    @Deprecated
    public void createRecentCatalogService(CatalogService catalogService, StorageOSUser user) {
        List<RecentService> recentServices = getRecentServices(user.getUserName());
        if (catalogService != null) {
            RecentService found = findRecentService(recentServices, catalogService, user);
            if (found != null) {
                // Delete and Re-Save to update Created Time
                client.delete(found);
                createRecentService(catalogService.getId(), user.getUserName());
            }
            else {
                cleanUpRecentServices(recentServices);
                createRecentService(catalogService.getId(), user.getUserName());
            }
        }
    }

    @Deprecated
    private void createRecentService(URI catalogServiceId, String username) {
        RecentService recentService = new RecentService();
        recentService.setUserId(username);
        recentService.setCatalogServiceId(catalogServiceId);
        client.save(recentService);
    }

    @Deprecated
    private void cleanUpRecentServices(List<RecentService> recentServices) {
        if (recentServices.size() >= MAX_RECENT_SERVICES) {
            Collections.sort(recentServices, CreationTimeComparator.NEWEST_FIRST);
            for (int i = MAX_RECENT_SERVICES - 1; i < recentServices.size(); i++) {
                client.delete(recentServices.get(i));
            }
        }
    }

    @Deprecated
    private RecentService findRecentService(List<RecentService> recentServices, CatalogService service, StorageOSUser user) {
        RecentService found = null;
        for (RecentService recentService : recentServices) {
            if (service.getId() != null && service.getId().equals(recentService.getCatalogServiceId())) {
                found = recentService;
            }
        }
        return found;
    }

    public void moveUpCatalogService(URI catalogServiceId) {
        CatalogService catalogService = getCatalogServiceById(catalogServiceId);
        if (catalogService != null) {
            SortedIndexUtils.moveUp(catalogService, client);
        }
    }

    public void moveDownCatalogService(URI catalogServiceId) {
        CatalogService catalogService = getCatalogServiceById(catalogServiceId);
        if (catalogService != null) {
            SortedIndexUtils.moveDown(catalogService, client);
        }
    }

    public void moveUpCatalogServiceField(URI catalogServiceId, String fieldName) {
        CatalogServiceField field = findCatalogServiceFieldByName(catalogServiceId, fieldName);
        if (field != null) {
            SortedIndexUtils.moveUp(field, client);
        }
    }

    public void moveDownCatalogServiceField(URI catalogServiceId, String fieldName) {
        CatalogServiceField field = findCatalogServiceFieldByName(catalogServiceId, fieldName);
        if (field != null) {
            SortedIndexUtils.moveDown(field, client);
        }
    }

    private CatalogServiceField findCatalogServiceFieldByName(URI catalogServiceId, String fieldName) {
        CatalogServiceField found = null;
        if (catalogServiceId != null && StringUtils.isNotBlank(fieldName)) {
            List<CatalogServiceField> fields = getCatalogServiceFields(catalogServiceId);
            if (fields != null) {
                for (CatalogServiceField field : fields) {
                    if (fieldName.equalsIgnoreCase(field.getLabel())) {
                        found = field;
                        break;
                    }
                }
            }
        }
        return found;
    }

    public Map<String, String> getLockedFields(URI catalogServiceId) {
        Map<String, String> fields = Maps.newLinkedHashMap();
        for (CatalogServiceField field : getCatalogServiceFields(catalogServiceId)) {
            String value = getLockedValue(field);
            if (value != null) {
                fields.put(field.getLabel(), value);
            }
        }
        return fields;
    }

    public String getLockedValue(CatalogServiceField field) {
        if (Boolean.TRUE.equals(field.getOverride()) && StringUtils.isNotBlank(field.getValue())) {
            return field.getValue();
        }
        else {
            return null;
        }
    }

    @Override 
    public String getWorkflowDocument(String workflowName) {
        if( null == workflowName || workflowName.isEmpty()) return null;
        
        final List<CustomServicesWorkflow> results = customServicesWorkflowManager.getByName(workflowName);
        if(null == results || results.isEmpty()) {
            return null;
        }
        if(results.size() > 1) {
            throw new IllegalStateException("Multiple workflows with the name " + workflowName);
        }
        
        try {
            return WorkflowHelper.toWorkflowDocumentJson(results.get(0));
        } catch (final IOException e) {
            throw new RuntimeException("Failed to deserialize workflow document " + workflowName, e);
        }
    }

}
