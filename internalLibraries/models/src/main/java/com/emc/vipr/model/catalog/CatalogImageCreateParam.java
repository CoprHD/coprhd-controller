/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "catalog_image_create")
public class CatalogImageCreateParam extends CatalogImageCommonParam {

    private URI tenant;

    @XmlElement(name = "tenant")
    public URI getTenant() {
        return tenant;
    }

    public void setTenant(URI tenant) {
        this.tenant = tenant;
    }

}
