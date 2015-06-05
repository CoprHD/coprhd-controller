/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

public interface TenantDataObject {
    
    public static final String TENANT_COLUMN_NAME = "tenant";
    
    public String getTenant();
    public void setTenant(String tenant);
    
}
