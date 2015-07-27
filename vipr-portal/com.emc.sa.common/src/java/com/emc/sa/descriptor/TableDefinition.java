/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Java representation of the service table definition JSON.
 */
public class TableDefinition extends ItemDefinition {
    private static final long serialVersionUID = -2808177114384293730L;

    /** The fields in each table row. */
    public Map<String, FieldDefinition> fields = new LinkedHashMap<>();

    public void addField(FieldDefinition field) {
        fields.put(field.name, field);
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        toString(builder);
        builder.append("fields", fields);
        return builder.toString();
    }
}
