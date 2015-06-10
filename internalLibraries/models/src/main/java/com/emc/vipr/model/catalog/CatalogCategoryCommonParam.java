/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.valid.Length;

public class CatalogCategoryCommonParam {

    private URI catalogCategoryId;
    
    private String name;

    private String title;
    
    private String description;    

    private String image;
    
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "catalog_category")
    public URI getCatalogCategoryId() {
        return catalogCategoryId;
    }

    public void setCatalogCategoryId(URI catalogCategoryId) {
        this.catalogCategoryId = catalogCategoryId;
    }

    @XmlElement(required = true, name = "title")
    @Length(min = 2, max = 128)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @XmlElement(required = true, name = "description")
    @Length(max = 255)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(required = true, name = "image")
    @Length(max = 255)
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

}
