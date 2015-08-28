/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog.search;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Search builder for searching the service catalog. The user can either specify
 * multiple paths by chaining the .path() command or .segments() if the user has
 * an already split path segments. After the path is build the user can call either
 * category() or service() to retrieve the data from this path.
 * 
 * Examples:
 * client.browse().path("BlockStorageServices").category();
 * client.browse().path("BlockStorageServices/CreateBlockVolume").service();
 */
public class CatalogSearchBuilder {
    private ViPRCatalogClient2 catalog;
    private URI tenantId;
    private List<String> segments = new ArrayList<String>();

    public CatalogSearchBuilder(ViPRCatalogClient2 catalog, URI tenantId) {
        this.catalog = catalog;
        this.tenantId = tenantId;
    }

    /**
     * Appends a path to this builder to search.
     * 
     * @param path '/' separated path.
     * @return This Builder
     */
    public CatalogSearchBuilder path(String path) {
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment != null && !segment.equals("")) {
                this.segments.add(segment);
            }
        }
        return this;
    }

    /**
     * Appends a series of path segments to the builder.
     * 
     * @param segments segements of the path to add.
     * @return This Builder
     */
    public CatalogSearchBuilder segments(String... segments) {
        for (String segment : segments) {
            if (segment != null && !segment.equals("")) {
                this.segments.add(segment);
            }
        }
        return this;
    }

    /**
     * Searches the build path and returns a category at this location.
     * 
     * @return Category matching the built path.
     */
    public CatalogCategoryRestRep category() {
        CatalogCategoryRestRep parent = catalog.categories().getRootCatalogCategory(tenantId);
        for (String segment : segments) {
            parent = getChildCategory(parent, segment);
        }
        return parent;
    }

    /**
     * Searches the build path and returns a service at this location.
     * 
     * @return Service matching the built path.
     */
    public CatalogServiceRestRep service() {
        if (segments.isEmpty()) {
            return null;
        }

        CatalogCategoryRestRep parent = catalog.categories().getRootCatalogCategory(tenantId);

        for (Iterator<String> iter = segments.iterator(); iter.hasNext();) {
            String segment = iter.next();
            // If we have more elements, this is a category
            if (iter.hasNext()) {
                parent = getChildCategory(parent, segment);
            }
            else {
                return getChildService(parent, segment);
            }
        }
        return null;
    }

    private CatalogCategoryRestRep getChildCategory(CatalogCategoryRestRep parent, String subPath) {
        if (parent == null || subPath == null) {
            return null;
        }

        List<CatalogCategoryRestRep> subCatalogCategories = catalog.categories().getSubCategories(parent.getId());
        for (CatalogCategoryRestRep subCatalogCategory : subCatalogCategories) {
            if (subPath.equalsIgnoreCase(subCatalogCategory.getName())) {
                return subCatalogCategory;
            }
        }
        return null;
    }

    private CatalogServiceRestRep getChildService(CatalogCategoryRestRep parent, String subPath) {
        if (parent == null || subPath == null) {
            return null;
        }

        List<CatalogServiceRestRep> catalogServices = catalog.services().findByCatalogCategory(parent.getId());
        for (CatalogServiceRestRep catalogService : catalogServices) {
            if (subPath.equalsIgnoreCase(catalogService.getName())) {
                return catalogService;
            }
        }
        return null;
    }
}
