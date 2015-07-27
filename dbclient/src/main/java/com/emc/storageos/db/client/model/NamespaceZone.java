/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Object services namespace information for a tenant's zone instance
 */
public class NamespaceZone extends AbstractSerializableNestedObject {
    private static final String _TENANT = "tenant";

    /**
     * the tenant for the zone
     */
    @Name("tenant")
    public String getTenant() {
        return getStringField(_TENANT);
    }
    public void setTenant(String tenant) {
        setField(_TENANT, tenant);
    }
}
