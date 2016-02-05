/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import org.apache.commons.lang.StringUtils;

import java.util.List;

public class CategoryDef {
    public String label;
    public String title;
    public String image;
    public String version;
    public String description;
    public List<CategoryDef> categories;
    public List<ServiceDef> services;

    public boolean containsSubCategory(String name) {
        for (CategoryDef subCategory : categories) {
            if (subCategory.getName().equals(name)) {
                return true;
            }
        }

        return false;
    }

    public String getName() {
        return StringUtils.defaultIfBlank(this.label, StringUtils.replace(title, " ", ""));
    }
}