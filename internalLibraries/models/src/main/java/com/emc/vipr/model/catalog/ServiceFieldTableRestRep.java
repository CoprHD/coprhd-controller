/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "service_field_table")
public class ServiceFieldTableRestRep extends ServiceItemRestRep implements ServiceItemContainerRestRep {

    private List<ServiceFieldRestRep> items;

    @XmlElementWrapper(name = "items")
    @XmlElements({
            @XmlElement(name = "field", type = ServiceFieldRestRep.class)
    })
    public List<ServiceFieldRestRep> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }

    public void setItems(List<ServiceFieldRestRep> items) {
        this.items = items;
    }

}
