/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_catalog_categories")
public class CatalogCategoryBulkRep extends BulkRestRep {

    private List<CatalogCategoryRestRep> catalogCategories;

    public CatalogCategoryBulkRep() {

    }

    /**
     * List of catalog categories
     * 
     * @return The list of catalog categories
     */
    @XmlElement(name = "catalog_category")
    public List<CatalogCategoryRestRep> getCatalogCategories() {
        if (catalogCategories == null) {
            catalogCategories = new ArrayList<>();
        }
        return catalogCategories;
    }

    public void setCatalogCategories(List<CatalogCategoryRestRep> catalogCategories) {
        this.catalogCategories = catalogCategories;
    }

    public CatalogCategoryBulkRep(List<CatalogCategoryRestRep> catalogCategories) {
        this.catalogCategories = catalogCategories;
    }
}
