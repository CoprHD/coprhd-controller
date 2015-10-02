/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "catalog_category")
public class CatalogCategoryList {

    private List<NamedRelatedResourceRep> categories;

    public CatalogCategoryList() {
    }

    public CatalogCategoryList(List<NamedRelatedResourceRep> categories) {
        this.categories = categories;
    }

    /**
     * List of catalog categories
     * 
     */
    @XmlElement(name = "catalog_category")
    public List<NamedRelatedResourceRep> getCatalogCategories() {
        if (categories == null) {
            categories = new ArrayList<>();
        }
        return categories;
    }

    public void setCatalogCategories(List<NamedRelatedResourceRep> categories) {
        this.categories = categories;
    }

}
