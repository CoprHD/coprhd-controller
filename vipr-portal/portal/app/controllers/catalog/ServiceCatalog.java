/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import controllers.security.Security;
import models.BreadCrumb;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.cache.Cache;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.CatalogCategoryUtils;
import util.CatalogServiceUtils;
import util.DocUtils;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.tenant.TenantSelector;
import controllers.util.Models;

@With(Common.class)
public class ServiceCatalog extends Controller {
    private static final String SERVICE_CATALOG = "SERVICE_CATALOG";
    private static final String CATALOG_EXPIRE = "10min";

    public static void view() {
        Map<String, CategoryDef> catalog = getCatalog(Models.currentAdminTenant());
        Map<String, List<BreadCrumb>> breadcrumbs = createBreadCrumbs(catalog);
        TenantSelector.addRenderArgs();
        render(catalog, breadcrumbs);
    }

    public static void docCategory(String categoryId) {
        if (categoryId == null) {
            redirect(DocUtils.getDocumentationLink());
        }
        CatalogCategoryRestRep category = CatalogCategoryUtils.getCatalogCategory(uri(categoryId));
        if (category == null) {
            redirect(DocUtils.getDocumentationLink());
        }
        Logger.debug("Redirecting to doc page for category: " + category.getName());
        redirect(DocUtils.getCatalogDocumentationLink(category.getName()));
    }

    @Util
    public static CategoryDef getCatalogRoot(String tenantId) {
        Map<String, CategoryDef> catalog = getCatalog(tenantId);

        for (CategoryDef categoryDef : catalog.values()) {
            if (categoryDef.parentId == null) {
                return categoryDef;
            }
        }

        return null;
    }

    @Util
    public static void catalogModified(String tenantId) {
        String cacheKey = SERVICE_CATALOG + "." + tenantId + "." + Security.getUserInfo().getIdentifier();
        Cache.delete(cacheKey);
    }

    @Util
    public static Map<String, CategoryDef> getCatalog(String tenantId) {
        // change service catalog cache from by tenant to by tenant_user, as based on user's
        // roles and ACLs of every catalog, different users will have different views.
        String cacheKey = SERVICE_CATALOG + "." + tenantId + "." + Security.getUserInfo().getIdentifier();
        Map<String, CategoryDef> catalog = (Map<String, CategoryDef>) Cache.get(cacheKey);
        if (catalog == null) {
            Logger.debug("Creating catalog cache for " + cacheKey);

            catalog = createCatalog(tenantId);
            Cache.set(cacheKey, catalog, CATALOG_EXPIRE);
        }
        else {
            Logger.debug("Using catalog from cache for " + cacheKey);
        }
        return catalog;
    }

    private static Map<String, CategoryDef> createCatalog(String tenantId) {
        Map<String, CategoryDef> catalog = Maps.newLinkedHashMap();
        CategoryDef root = createCategory(CatalogCategoryUtils.getRootCategory(uri(tenantId)), null);
        addCategories(root, catalog);
        return catalog;
    }

    private static void addCategories(CategoryDef category, Map<String, CategoryDef> catalog) {
        catalog.put(category.id, category);
        for (CategoryDef subCategory : category.categories) {
            addCategories(subCategory, catalog);
        }
    }

    private static CategoryDef createCategory(CatalogCategoryRestRep category, String path) {
        CategoryDef def = new CategoryDef();
        def.id = category.getId().toString();
        def.name = category.getName();
        def.title = category.getTitle();
        def.description = category.getDescription();
        def.image = category.getImage();
        def.parentId = getParentId(category.getCatalogCategory());
        def.path = getPath(path, def.name);

        List<CatalogServiceRestRep> catalogServices = CatalogServiceUtils.getCatalogServices(category);
        for (CatalogServiceRestRep catalogService : catalogServices) {
            def.services.add(createService(catalogService, def.path));
        }

        List<CatalogCategoryRestRep> subCategories = CatalogCategoryUtils.getCatalogCategories(category);
        for (CatalogCategoryRestRep subCategory : subCategories) {
            def.categories.add(createCategory(subCategory, def.path));
        }
        return def;
    }

    public static ServiceDef createService(CatalogServiceRestRep service, String basePath) {
        ServiceDescriptorRestRep descriptor = service.getServiceDescriptor();
        ServiceDef def = new ServiceDef();
        def.id = service.getId().toString();
        def.name = service.getName();
        def.title = service.getTitle();
        def.description = service.getDescription();
        def.roles = descriptor != null ? descriptor.getRoles() : Collections.<String> emptyList();
        def.image = service.getImage();
        def.parentId = getParentId(service.getCatalogCategory());
        def.path = getPath(basePath, def.name);
        return def;
    }

    protected static String getParentId(RelatedResourceRep parentId) {
        if (parentId != null && parentId.getId() != null) {
            return parentId.getId().toString();
        }
        return null;
    }

    protected static String getPath(String basePath, String name) {
        if (StringUtils.isNotBlank(basePath)) {
            return basePath + "/" + name;
        }
        else {
            return name;
        }
    }

    /**
     * Creates the breadcrumbs for the entire catalog.
     * 
     * @param catalog
     *        the service catalog.
     * @return the mapping of category ID to breadcrumb list.
     */
    @Util
    public static Map<String, List<BreadCrumb>> createBreadCrumbs(Map<String, CategoryDef> catalog) {
        Map<String, List<BreadCrumb>> catalogBreadcrumbs = Maps.newLinkedHashMap();
        for (CategoryDef category : catalog.values()) {
            List<BreadCrumb> breadcrumbs = createBreadCrumbs(category.id, catalog);
            catalogBreadcrumbs.put(category.id, breadcrumbs);
        }
        return catalogBreadcrumbs;
    }

    /**
     * Creates the breadcrumbs for the catalog service.
     * 
     * @param service
     *        the catalog service.
     * @return the breadcrumb list.
     */
    @Util
    public static List<BreadCrumb> createBreadCrumbs(String tenantId, CatalogServiceRestRep service) {
        RelatedResourceRep categoryId = service.getCatalogCategory();
        String parentId = getParentId(categoryId);
        List<BreadCrumb> breadcrumbs = createBreadCrumbs(parentId, getCatalog(tenantId));
        String id = service.getId() != null ? service.getId().toString() : "";
        addBreadCrumb(breadcrumbs, id, service.getName(), service.getTitle());
        return breadcrumbs;
    }

    /**
     * Creates the breadcrumbs for the catalog category.
     * 
     * @param category
     *        the catalog category.
     * @return the breacrumb list.
     */
    @Util
    public static List<BreadCrumb> createBreadCrumbs(String tenantId, CatalogCategoryRestRep category) {
        return createBreadCrumbs(category.getId().toString(), getCatalog(tenantId));
    }

    /**
     * Adds a breadcrumb to the end of the list, constructing its path based on the breadcrumb at the end of the list.
     * 
     * @param breadcrumbs
     *        the list of breadcrumbs.
     * @param id
     *        the breadcrumb ID.
     * @param name
     *        the breadcrumb name, used to construct the path.
     * @param title
     *        the breadcrumb title.
     */
    @Util
    public static void addBreadCrumb(List<BreadCrumb> breadcrumbs, String id, String name, String title) {
        String path = getPath(getLastPath(breadcrumbs), name);
        breadcrumbs.add(new BreadCrumb(id, name, title, path));
    }

    /**
     * Adds a simple breadcrumb with a title only. This should only ever be used to add a final breadcrumb for display
     * purposes.
     * 
     * @param breadcrumbs
     *        the breadcrumbs.
     * @param title
     *        the breadcrumb title.
     */
    @Util
    public static void addBreadCrumb(List<BreadCrumb> breadcrumbs, String title) {
        breadcrumbs.add(new BreadCrumb("", "", title, ""));
    }

    /**
     * Gets the last path entry from the breadcrumb list.
     * 
     * @param breadcrumbs
     *        the breadcrumbs.
     * @return the last path or empty string if there are no breadcrumbs.
     */
    private static String getLastPath(List<BreadCrumb> breadcrumbs) {
        if (!breadcrumbs.isEmpty()) {
            return breadcrumbs.get(breadcrumbs.size() - 1).path;
        }
        else {
            return "";
        }
    }

    /**
     * Creates a breadcrumb list for the given category.
     * 
     * @param categoryId
     *        the ID of the category to create the breadcrumb for.
     * @param catalog
     *        the catalog.
     * @return the breadcrumb list.
     */
    @Util
    public static List<BreadCrumb> createBreadCrumbs(String categoryId, Map<String, CategoryDef> catalog) {
        List<BreadCrumb> breadcrumbs = Lists.newArrayList();

        CategoryDef current = catalog.get(categoryId);
        while (current != null) {
            breadcrumbs.add(createBreadCrumb(current));
            current = catalog.get(current.parentId);
        }
        Collections.reverse(breadcrumbs);

        return breadcrumbs;
    }

    @Util
    public static BreadCrumb createBreadCrumb(CategoryDef category) {
        return new BreadCrumb(category.id, category.name, category.title, category.path);
    }

    @Util
    public static BreadCrumb createBreadCrumb(ServiceDef service) {
        return new BreadCrumb(service.id, service.name, service.title, service.path);
    }

    public static class CategoryDef implements Serializable {
        public String id;
        public String name;
        public String title;
        public String description;
        public String image;
        public String parentId;
        public String path;

        public List<CategoryDef> categories = Lists.newArrayList();
        public List<ServiceDef> services = Lists.newArrayList();


        public CategoryDef getSubCategory(String name) {
            for (CategoryDef subcategory : categories) {
                if (subcategory.name.equals(name)) {
                    return subcategory;
                }
            }

            return null;
        }

        public boolean containsSubCategory(String name) {
            return getSubCategory(name) != null;
        }

        public boolean containsService(String name) {
            for (ServiceDef service : services) {
                if (service.name.equals(name)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static class ServiceDef implements Serializable {
        public String id;
        public String name;
        public String title;
        public String description;
        public List<String> roles;
        public String image;
        public String parentId;
        public String path;
    }
}
