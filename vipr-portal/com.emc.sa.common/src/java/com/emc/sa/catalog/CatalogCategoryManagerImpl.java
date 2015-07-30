/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.sa.util.Messages;
import com.emc.sa.util.ServiceIdPredicate;
import com.emc.storageos.db.client.model.NamedURI;
import com.google.common.collect.Lists;

@Component
public class CatalogCategoryManagerImpl implements CatalogCategoryManager {

    private static final Logger log = Logger.getLogger(CatalogCategoryManagerImpl.class);

    @Autowired
    private ModelClient client;

    @Autowired
    private CatalogServiceManager catalogServiceManager;

    @Autowired
    private ServiceDescriptors serviceDescriptors;

    private Messages MESSAGES = new Messages(CatalogBuilder.class, "default-catalog");

    public void upgradeCatalog(URI tenantId) throws IOException {
        CatalogCategory rootCategory = getOrCreateRootCategory(tenantId);

        com.emc.sa.catalog.CategoryDef newCatalog = CatalogBuilder.readCatalogDef(getDefaultCatalog());

        log.info(String.format("Updating Service catalog for tenant %s", tenantId));
        upgradeCategory(rootCategory, newCatalog);

        rootCategory.setVersion(newCatalog.version);
        client.save(rootCategory);
    }

    public CatalogCategory getOrCreateRootCategory(URI tenantId) {
        CatalogCategory root = null;
        root = client.catalogCategories().getRootCategory(tenantId.toString());
        if (root == null) {
            root = createDefaultCatalog(tenantId);
        }
        return root;
    }

    public void restoreDefaultCatalog(URI tenant) throws IOException {
        // Delete old catalog
        CatalogCategory catalog = getOrCreateRootCategory(tenant);
        deleteCatalogCategory(catalog);

        // Rebuild catalog
        catalog = getOrCreateRootCategory(tenant);
        CatalogBuilder builder = new CatalogBuilder(client, serviceDescriptors);
        builder.clearCategory(catalog);
        builder.buildCatalog(tenant.toString(), getDefaultCatalog());
    }

    private CatalogCategory createDefaultCatalog(URI tenant) {
        loadCatalog(tenant);
        return client.catalogCategories().getRootCategory(tenant.toString());
    }

    private void loadCatalog(URI tenant) {
        try {
            log.info("Loading default catalog");
            new CatalogBuilder(client, serviceDescriptors).buildCatalog(tenant.toString(), getDefaultCatalog());
        } catch (IOException e) {
            log.error("Failed to populate default catalog", e);
        } catch (RuntimeException e) {
            log.error("Failed to populate default catalog", e);
        }
    }

    public boolean isCatalogUpdateAvailable(URI tenantId) {
        try {
            CatalogCategory rootCategory = getOrCreateRootCategory(tenantId);

            if (rootCategory.getVersion() == null) {
                return true;
            }
            else {
                String catalogHash = CatalogBuilder.getCatalogHash(getDefaultCatalog());
                return !rootCategory.getVersion().equals(catalogHash);
            }
        } catch (IOException e) {
            log.error("Reading default catalog file", e);
            return false;
        }
    }

    private boolean isServiceUsedForOrders(String tenantId, CatalogService service) {
        String serviceId = service.getId().toString();
        List<Order> orders = client.orders().findAll(tenantId);
        return CollectionUtils.exists(orders, new ServiceIdPredicate(serviceId));
    }

    private InputStream getDefaultCatalog() {
        return getClass().getResourceAsStream("default-catalog.json");
    }

    /**
     * Updates the category, adding and removing out off date sub categories and services
     * (Warning: This is recursive)
     */
    private void upgradeCategory(CatalogCategory currentCategory, com.emc.sa.catalog.CategoryDef newCategory) {
        if (newCategory.categories != null) {
            List<CatalogCategory> subCategories = client.catalogCategories().findSubCatalogCategories(currentCategory.getId());

            for (com.emc.sa.catalog.CategoryDef newSubCategory : newCategory.categories) {
                String label = StringUtils.deleteWhitespace(getMessage(getLabel(newSubCategory)));
                CatalogCategory currentSubCategory = findSubCategory(subCategories, label);

                if (currentSubCategory == null) {
                    log.info(String.format("CREATING Missing Category : %s for tenant:%s", label, currentCategory.getTenant()));
                    createCategory(currentCategory.getTenant(), newSubCategory, currentCategory);
                }
                else {
                    upgradeCategory(currentSubCategory, newSubCategory);
                }
            }
        }

        upgradeServices(currentCategory, newCategory);
    }

    private void upgradeServices(CatalogCategory currentCategory, com.emc.sa.catalog.CategoryDef newCategory) {
        List<CatalogService> services = client.catalogServices().findByCatalogCategory(currentCategory.getId());

        // Add or Update Missing Services
        if (newCategory.services != null) {
            for (com.emc.sa.catalog.ServiceDef newService : newCategory.services) {
                List<CatalogService> matchingServices = findServices(services, newService.baseService);
                if (matchingServices != null && !matchingServices.isEmpty()) {
                    updateMatchingServices(currentCategory, matchingServices, newService);
                }
                else {
                    ServiceDescriptor descriptor = serviceDescriptors.getDescriptor(Locale.getDefault(), newService.baseService);
                    String label = "";
                    if (descriptor != null) {
                        label = StringUtils.deleteWhitespace(StringUtils.defaultString(getMessage(getLabel(newService)),
                                descriptor.getTitle()));
                    }
                    log.info(String.format("CREATING Missing Service %s: for tenant: %s", label, currentCategory.getTenant()));
                    catalogServiceManager.createCatalogService(newService, currentCategory);
                }
            }
        }

        // Remove Old Services
        for (CatalogService service : services) {
            ServiceDescriptor serviceDescriptor = null;
            try {
                serviceDescriptor = serviceDescriptors.getDescriptor(Locale.getDefault(), service.getBaseService());
            } catch (IllegalStateException ese) {
                // getDescriptor throws exception when no descriptor found
            }
            if (serviceDescriptor == null) {
                log.info(String.format("REMOVING Service '%s' as base service '%s' no longer exists for tenant:%s", service.getTitle(),
                        service.getBaseService(), currentCategory.getTenant()));
                catalogServiceManager.deleteCatalogService(service);
            }
        }

    }

    private <K, V> boolean mapEquals(Map<K, V> m1, Map<K, V> m2) {
        if ((m1 == null || m1.isEmpty()) && (m2 == null || m2.isEmpty())) {
            return true;
        } else if (m1 != null && m2 != null && m1.equals(m2)) {
            return true;
        }
        return false;
    }

    private void updateMatchingServices(CatalogCategory currentCategory, List<CatalogService> services, ServiceDef newService) {
        int pristineService = 0;
        for (CatalogService service : services) {
            if (isMatch(service, newService)) {
                if (pristineService == 0) {
                    log.info(String.format("Updating Existing Matching Service %s: for tenant: %s", service.getLabel(),
                            currentCategory.getTenant()));
                    ServiceDescriptor descriptor = serviceDescriptors.getDescriptor(Locale.getDefault(), newService.baseService);
                    if (descriptor != null) {
                        service.setLabel(StringUtils.deleteWhitespace(StringUtils.defaultString(getMessage(getLabel(newService)),
                                descriptor.getTitle())));
                        service.setTitle(StringUtils.defaultString(getMessage(newService.title), descriptor.getTitle()));
                        service.setDescription(StringUtils.defaultString(getMessage(newService.description), descriptor.getDescription()));
                    }
                    service.setImage(newService.image);
                    catalogServiceManager.updateCatalogService(service, catalogServiceManager.getCatalogServiceFields(service.getId()));
                    pristineService++;
                }
                else {
                    log.info(String.format("Removing Duplicate Service %s: for tenant: %s", service.getLabel(), currentCategory.getTenant()));
                    catalogServiceManager.deleteCatalogService(service);
                }
            }
        }
    }

    private boolean isMatch(CatalogService service, ServiceDef newService) {
        Map<String, String> serviceFields = catalogServiceManager
                .getLockedFields(service.getId());
        Map<String, String> newServiceFields = newService.lockFields;
        if (mapEquals(serviceFields, newServiceFields)
                && !service.getApprovalRequired()
                && !service.getExecutionWindowRequired()
                && (service.getAcls() == null || !service.getAcls().isEmpty())) {
            return true;
        }
        return false;
    }

    private List<CatalogService> findServices(List<CatalogService> services,
            String baseService) {
        List<CatalogService> matchingServices = Lists.newArrayList();
        for (CatalogService service : services) {
            if (StringUtils.equals(service.getBaseService(), baseService)) {
                matchingServices.add(service);
            }
        }
        return matchingServices;
    }

    private CatalogCategory findSubCategory(List<CatalogCategory> categories, String label) {
        for (CatalogCategory subCategory : categories) {
            if (subCategory.getLabel().equals(label)) {
                return subCategory;
            }
        }

        return null;
    }

    private CatalogCategory createCategory(String tenant, CategoryDef def, CatalogCategory parentCategory) {
        CatalogBuilder builder = new CatalogBuilder(client, serviceDescriptors);
        NamedURI namedUri = new NamedURI(parentCategory.getId(), parentCategory.getLabel());
        CatalogCategory newCategory = builder.createCategory(tenant, def, namedUri);

        newCategory.setSortedIndex(null);
        client.save(newCategory);

        return newCategory;
    }

    /**
     * Get catalog category object from id
     * 
     * @param id the URN of a catalog category
     * @return
     */
    public CatalogCategory getCatalogCategoryById(URI id) {
        if (id == null) {
            return null;
        }

        CatalogCategory catalogCategory = client.catalogCategories().findById(id);

        return catalogCategory;
    }

    public void createCatalogCategory(CatalogCategory catalogCategory) {

        if (catalogCategory.getSortedIndex() == null) {
            catalogCategory.setSortedIndex(SortedIndexUtils.getNextSortedIndex(catalogCategory, client));
        }

        client.save(catalogCategory);
    }

    public void updateCatalogCategory(CatalogCategory catalogCategory) {

        if (catalogCategory.getSortedIndex() == null) {
            catalogCategory.setSortedIndex(SortedIndexUtils.getNextSortedIndex(catalogCategory, client));
        }

        client.save(catalogCategory);
    }

    public void deleteCatalogCategory(CatalogCategory catalogCategory) {
        deleteCategoryContents(catalogCategory);
        client.delete(catalogCategory);
    }

    private void deleteCategoryContents(CatalogCategory catalogCategory) {
        List<CatalogCategory> categories = getSubCategories(catalogCategory.getId());
        for (CatalogCategory subCategory : categories) {
            deleteCatalogCategory(subCategory);
        }
        List<CatalogService> services = catalogServiceManager.getCatalogServices(catalogCategory.getId());
        for (CatalogService service : services) {
            catalogServiceManager.deleteCatalogService(service);
        }
    }

    public List<CatalogCategory> getSubCategories(URI parentCatalogCategoryId) {
        List<CatalogCategory> categories = client.catalogCategories().findSubCatalogCategories(parentCatalogCategoryId);
        SortedIndexUtils.sort(categories);
        return categories;
    }

    public void moveUpCatalogCategory(URI catalogCategoryId) {
        CatalogCategory catalogCategory = getCatalogCategoryById(catalogCategoryId);
        if (catalogCategory != null) {
            SortedIndexUtils.moveUp(catalogCategory, client);
        }
    }

    public void moveDownCatalogCategory(URI catalogCategoryId) {
        CatalogCategory catalogCategory = getCatalogCategoryById(catalogCategoryId);
        if (catalogCategory != null) {
            SortedIndexUtils.moveDown(catalogCategory, client);
        }
    }

    protected String getLabel(CategoryDef def) {
        return StringUtils.defaultString(def.label, def.title);
    }

    protected String getLabel(ServiceDef def) {
        return StringUtils.defaultString(def.label, def.title);
    }

    protected String getMessage(String key) {
        try {
            return (key != null) ? MESSAGES.get(key) : null;
        } catch (MissingResourceException e) {
            return key;
        }
    }

}
