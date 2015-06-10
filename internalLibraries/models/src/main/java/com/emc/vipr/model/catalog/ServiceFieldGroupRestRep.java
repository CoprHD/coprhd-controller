/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "service_field_group")
public class ServiceFieldGroupRestRep extends ServiceItemRestRep implements ServiceItemContainerRestRep {

    private Boolean collapsible;

    private Boolean collapsed;
    
    private List<ServiceItemRestRep> items;

    @XmlElement(name = "collapsible")
    public Boolean getCollapsible() {
        return collapsible;
    }

    public void setCollapsible(Boolean collapsible) {
        this.collapsible = collapsible;
    }

    @XmlElement(name = "collapsed")
    public Boolean getCollapsed() {
        return collapsed;
    }

    public void setCollapsed(Boolean collapsed) {
        this.collapsed = collapsed;
    }
    
    @XmlElementWrapper(name="items")
    @XmlElements({
        @XmlElement(name="field", type=ServiceFieldRestRep.class),
        @XmlElement(name="group", type=ServiceFieldGroupRestRep.class),
        @XmlElement(name="table", type=ServiceFieldTableRestRep.class)
    })
    public List<ServiceItemRestRep> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }
    public void setItems(List<ServiceItemRestRep> items) {
        this.items = items;
    }    

}
