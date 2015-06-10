/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "catalog_category")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class CatalogCategoryRestRep extends SortedIndexRestRep {
    
    private RelatedResourceRep tenant;
    
    private RelatedResourceRep catalogCategory;

    private String title;
    
    private String description;    

    private String image;

    private String version;

    /**
     * Catalog Category's Tenant
     *
     * @valid none
     */    
    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    /**
     * Catalog Category's parent category
     *
     * @valid none
     */        
    @XmlElement(name = "catalog_category")
    public RelatedResourceRep getCatalogCategory() {
        return catalogCategory;
    }

    public void setCatalogCategory(RelatedResourceRep catalogCategory) {
        this.catalogCategory = catalogCategory;
    }

    /**
     * Catalog Category's title
     *
     * @valid none
     */        
    @XmlElement(name = "title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Catalog Category's description
     *
     * @valid none
     */        
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Catalog Category's image url
     *
     * @valid none
     */        
    @XmlElement(name = "image")
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    /**
     * Catalog Category's version.  Used when preforming catalog upgrades.
     *
     * @valid none
     */        
    @XmlElement(name = "version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

}
