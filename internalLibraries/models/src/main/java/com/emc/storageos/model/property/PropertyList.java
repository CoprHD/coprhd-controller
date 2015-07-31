/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.property;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

//Argument for reset-properties call
@XmlRootElement(name = "property_list")
public class PropertyList {

    private List<String> propertyList;

    public PropertyList() {
    }

    public PropertyList(List<String> propertyList) {
        this.propertyList = propertyList;
    }

    @XmlElement(name = "property")
    public List<String> getPropertyList() {
        if (propertyList == null) {
            propertyList = new ArrayList<String>();
        }
        return propertyList;
    }

    public void setPropertyList(ArrayList<String> propertyList) {
        this.propertyList = propertyList;
    }

}
