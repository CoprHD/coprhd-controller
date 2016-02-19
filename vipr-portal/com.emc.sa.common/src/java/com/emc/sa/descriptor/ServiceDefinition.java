/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Java representation of the service definition JSON format.
 */
public class ServiceDefinition implements Serializable {
    private static final long serialVersionUID = -7173618289200246823L;

    /** Whether the service is disabled. */
    public boolean disabled;

    /** The base key for i18n messages. */
    public String baseKey;

    /** The ID of the service. */
    public String serviceId;

    /** The key of the service category. */
    public String categoryKey;

    /** The key of the service title. */
    public String titleKey;

    /** The key of the service description. */
    public String descriptionKey;
    
    public boolean isExtended=false;
    
    public String  extendedServiceId;

    /** The key of a warning message to display in the service catalog. */
    public String warningMessageKey;

    /** The items in the service (fields/groups/tables). */
    public Map<String, ItemDefinition> items = new LinkedHashMap<>();

    /**
     * Optional roles to limit the service visibility to (User must have one). No restriction is required as this is
     * more intended to limit things already restricted by the services that run or asset options required for them.
     */
    public List<String> roles = new ArrayList<>();

    public void addItem(ItemDefinition item) {
        items.put(item.name, item);
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("serviceId", serviceId);
        builder.append("baseKey", baseKey);
        builder.append("categoryKey", categoryKey);
        builder.append("titleKey", titleKey);
        builder.append("descriptionKey", descriptionKey);
        builder.append("warningMessageKey", warningMessageKey);
        builder.append("roles", roles);
        builder.append("items", items);
        return builder.toString();
    }
}
