/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class ServiceFieldTable extends ServiceItem implements ServiceItemContainer {
    private static final long serialVersionUID = 4835390430819956676L;

    private Map<String, ServiceField> items = new LinkedHashMap<>();

    public ServiceFieldTable() {
        setType(TYPE_TABLE);
    }

    @Override
    public void setType(String type) {
        if (!StringUtils.equals(type, TYPE_TABLE)) {
            throw new IllegalArgumentException("Invalid type for table: " + type);
        }
        super.setType(type);
    }

    public void addItem(ServiceField item) {
        items.put(item.getName(), item);
    }

    @Override
    public Map<String, ServiceField> getItems() {
        return items;
    }

    public List<ServiceField> getFieldList() {
        return new ArrayList<ServiceField>(items.values());
    }

    public List<ServiceField> getAssetFields() {
        return ServiceField.getAssetFields(this);
    }

    public Set<String> getAssetTypes() {
        return ServiceField.getAssetTypes(this);
    }
    
    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        toString(builder);
        builder.append("items", items);
        return builder.toString();
    }
}
