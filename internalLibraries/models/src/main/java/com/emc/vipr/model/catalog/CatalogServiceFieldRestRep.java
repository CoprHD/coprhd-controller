/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name = "catalog_service_field")
public class CatalogServiceFieldRestRep extends SortedIndexRestRep {

    private String value;
    private Boolean override;
    
    @XmlElement(name = "value")
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    
    @XmlElement(name = "override")
    public Boolean getOverride() {
        return override;
    }
    public void setOverride(Boolean override) {
        this.override = override;
    }

}
