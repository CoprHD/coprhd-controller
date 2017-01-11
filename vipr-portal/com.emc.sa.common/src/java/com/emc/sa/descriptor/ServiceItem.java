/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;

public abstract class ServiceItem implements Serializable {
    private static final long serialVersionUID = -651793641143021258L;

    public static String TYPE_GROUP = "group";
    public static String TYPE_TABLE = "table";
    public static String TYPE_MODAL = "modal";

    private String name;
    private String label;
    private String type;
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isGroup() {
        return StringUtils.equals(type, TYPE_GROUP);
    }

    public boolean isTable() {
        return StringUtils.equals(type, TYPE_TABLE);
    }
    
    public boolean isModal() {
        return StringUtils.equals(type, TYPE_MODAL);
    }

    public boolean isField() {
        return !(isGroup() || isTable() || isModal());
    }

    public boolean isPassword() {
        return isField() && StringUtils.equals(type, ServiceField.TYPE_PASSWORD);
    }

    protected void toString(ToStringBuilder builder) {
        builder.append("name", name);
        builder.append("type", type);
        builder.append("label", label);
        builder.append("description", description);
    }
}
