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

@XmlRootElement(name = "bulk_catalog_services")
public class CatalogServiceBulkRep extends BulkRestRep {

    private List<CatalogServiceRestRep> catalogServices;

    public CatalogServiceBulkRep() {

    }

    /**
     * List of catalog services
     * 
     * @return catalog services list
     */
    @XmlElement(name = "catalog_service")
    public List<CatalogServiceRestRep> getCatalogServices() {
        if (catalogServices == null) {
            catalogServices = new ArrayList<CatalogServiceRestRep>();
        }
        return catalogServices;
    }

    public void setCatalogServices(List<CatalogServiceRestRep> catalogServices) {
        this.catalogServices = catalogServices;
    }

    public CatalogServiceBulkRep(List<CatalogServiceRestRep> catalogServices) {
        this.catalogServices = catalogServices;
    }
}
