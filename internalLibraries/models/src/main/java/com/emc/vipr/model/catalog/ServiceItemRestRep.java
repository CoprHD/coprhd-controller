/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;

public abstract class ServiceItemRestRep {

    public static String TYPE_GROUP = "group";
    public static String TYPE_TABLE = "table";

    private String name;
    private String label;
    private String type;
    private String description;

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "label")
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isGroup() {
        return TYPE_GROUP.equals(type);
    }

    public boolean isTable() {
        return TYPE_TABLE.equals(type);
    }

    public boolean isField() {
        return !(isGroup() || isTable());
    }

}
