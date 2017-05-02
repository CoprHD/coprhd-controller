/*
 * Copyright (c) 2017  DELL EMC Corporation
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

public class ServiceFieldModal extends ServiceItem implements ServiceItemContainer {

    private static final long serialVersionUID = 2L;
    
    /** The items in the modal. */
    private Map<String, ServiceItem> items = new LinkedHashMap<>();

    public ServiceFieldModal() {
        setType(TYPE_MODAL);
    }

    @Override
    public void setType(String type) {
        if (!StringUtils.equals(type, TYPE_MODAL)) {
            throw new IllegalArgumentException("Invalid type for modal: " + type);
        }
        super.setType(type);
    }

    public void addItem(ServiceItem item) {
        items.put(item.getName(), item);
    }

    @Override
    public Map<String, ServiceItem> getItems() {
        return items;
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
        builder.append("items", items);
        return builder.toString();
    }
}
