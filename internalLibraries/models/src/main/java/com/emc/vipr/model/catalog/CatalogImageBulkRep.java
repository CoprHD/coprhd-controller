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

@XmlRootElement(name = "bulk_catalog_images")
public class CatalogImageBulkRep extends BulkRestRep {

    private List<CatalogImageRestRep> catalogImages;

    public CatalogImageBulkRep() {

    }

    /**
     * List of catalog images
     * 
     * @return
     */
    @XmlElement(name = "catalog_image")
    public List<CatalogImageRestRep> getCatalogImages() {
        if (catalogImages == null) {
            catalogImages = new ArrayList<>();
        }
        return catalogImages;
    }

    public void setProjects(List<CatalogImageRestRep> catalogImages) {
        this.catalogImages = catalogImages;
    }

    public CatalogImageBulkRep(List<CatalogImageRestRep> catalogImages) {
        this.catalogImages = catalogImages;
    }
}
