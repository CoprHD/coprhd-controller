/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class ServiceFieldGroup extends ServiceItem implements ServiceItemContainer {
    private static final long serialVersionUID = 3826977920256643398L;

    /** Whether the group is collapsible. */
    private boolean collapsible;

    /** Whether the group is collapsed initially. */
    private boolean collapsed;

    /** The items in the group. */
    private Map<String, ServiceItem> items = new LinkedHashMap<>();

    public ServiceFieldGroup() {
        setType(TYPE_GROUP);
    }

    @Override
    public void setType(String type) {
        if (!StringUtils.equals(type, TYPE_GROUP)) {
            throw new IllegalArgumentException("Invalid type for group: " + type);
        }
        super.setType(type);
    }

    public boolean isCollapsible() {
        return collapsible;
    }

    public void setCollapsible(boolean collapsible) {
        this.collapsible = collapsible;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    @Override
    public Map<String, ServiceItem> getItems() {
        return items;
    }

    public void setItems(Map<String, ServiceItem> items) {
        this.items = items;
    }

    public void addItem(ServiceItem item) {
        items.put(item.getName(), item);
    }

    public List<ServiceField> getAssetFields() {
        return ServiceField.getAssetFields(this);
    }

    public Set<String> getAssetTypes() {
        return ServiceField.getAssetTypes(this);
    }

    public ServiceField getField(String name) {
        return ServiceField.findField(this, name);
    }

    public List<ServiceField> getFieldList() {
        return ServiceField.getFieldList(this);
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        toString(builder);
        builder.append("collapsible", collapsible);
        builder.append("collapsed", collapsed);
        builder.append("items", items);
        return builder.toString();
    }
}
