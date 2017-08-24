/*
 * Copyright (c) 2017  DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class ModalDefinition extends ItemDefinition {
    private static final long serialVersionUID = 1141589857348119959L;
    
    /** Items within the modal. */
    public Map<String, ItemDefinition> items = new LinkedHashMap<>();

    public void addItem(ItemDefinition item) {
        items.put(item.name, item);
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        toString(builder);
        builder.append("items", items);
        return builder.toString();
    }
}
