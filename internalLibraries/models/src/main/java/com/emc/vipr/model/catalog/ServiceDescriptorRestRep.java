/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "service_descriptor")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ServiceDescriptorRestRep implements ServiceItemContainerRestRep {

    private String serviceId;
    private String category;
    private String title;
    private String description;
    private List<String> roles;
    private boolean destructive = false;
    private List<ServiceItemRestRep> items;

    @XmlElement(name = "service_id")
    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @XmlElement(name = "category")
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @XmlElement(name = "title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @XmlElement(name = "descriptor")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "role")
    public List<String> getRoles() {
        if (roles == null) {
            roles = new ArrayList<String>();
        }
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    @XmlElement(name = "destructive")
    public boolean isDestructive() {
        return destructive;
    }

    public void setDestructive(boolean destructive) {
        this.destructive = destructive;
    }

    @XmlElementWrapper(name = "items")
    @XmlElements({
            @XmlElement(name = "field", type = ServiceFieldRestRep.class),
            @XmlElement(name = "group", type = ServiceFieldGroupRestRep.class),
            @XmlElement(name = "table", type = ServiceFieldTableRestRep.class)
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
