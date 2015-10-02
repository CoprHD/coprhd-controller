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

@XmlRootElement(name = "catalog_services")
public class CatalogServiceList {

    private List<NamedRelatedResourceRep> catalogServices;

    public CatalogServiceList() {
    }

    public CatalogServiceList(List<NamedRelatedResourceRep> catalogServices) {
        this.catalogServices = catalogServices;
    }

    /**
     * List of catalog services
     * 
     */
    @XmlElement(name = "catalog_service")
    public List<NamedRelatedResourceRep> getCatalogServices() {
        if (catalogServices == null) {
            catalogServices = new ArrayList<>();
        }
        return catalogServices;
    }

    public void setCatalogServices(List<NamedRelatedResourceRep> catalogServices) {
        this.catalogServices = catalogServices;
    }
}
