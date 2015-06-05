/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Java representation of the service group definition JSON.
 */
public class GroupDefinition extends ItemDefinition {
    private static final long serialVersionUID = 3785410624548057455L;

    /** Items within the group. */
    public Map<String, ItemDefinition> items = new LinkedHashMap<>();

    /** Whether the group is collapsible (defaults to true). */
    public boolean collapsible = true;

    /** Whether the group is initially collapsed. */
    public boolean collapsed;

    public void addItem(ItemDefinition item) {
        items.put(item.name, item);
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
