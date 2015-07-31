/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;

import com.emc.storageos.db.client.model.uimodels.TenantPreferences;

public interface CatalogPreferenceManager {

    public TenantPreferences getPreferencesByTenant(String tenantId);

    public TenantPreferences getPreferences(URI id);

    public void updatePreferences(TenantPreferences tenantPreferences);

}
