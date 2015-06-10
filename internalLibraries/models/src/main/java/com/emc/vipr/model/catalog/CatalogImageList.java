/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "catalog_images")
public class CatalogImageList {
    
    private List<NamedRelatedResourceRep> catalogImages;
    
    public CatalogImageList() {}
    
    public CatalogImageList(List<NamedRelatedResourceRep> catalogImages) {
        this.catalogImages = catalogImages;
    }

    /**
     * List of catalog images
     * @valid none
     */
    @XmlElement(name = "catalog_image")
    public List<NamedRelatedResourceRep> getCatalogImages() {
        if (catalogImages == null) {
            catalogImages = new ArrayList<>();
        }
        return catalogImages;
    }

    public void setCatalogImages(List<NamedRelatedResourceRep> catalogImages) {
        this.catalogImages = catalogImages;
    }    
}
