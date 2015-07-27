/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.Serializable;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * Base class for items (field/group/table) in a service definition.
 */
public abstract class ItemDefinition implements Serializable {
    private static final long serialVersionUID = 2328153220157510155L;

    /** The name of the item (unique within the service). */
    public String name;

    /** The type of the item. */
    public String type;

    /** The key of the item label. */
    public String labelKey;

    /** The key of the item description. */
    public String descriptionKey;

    protected void toString(ToStringBuilder builder) {
        builder.append("name", name);
        builder.append("type", type);
        builder.append("labelKey", labelKey);
        builder.append("descriptionKey", descriptionKey);
    }
}
