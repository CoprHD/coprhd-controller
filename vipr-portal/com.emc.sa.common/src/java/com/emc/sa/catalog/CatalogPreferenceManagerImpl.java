/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.model.uimodels.TenantPreferences;
import com.emc.sa.model.dao.ModelClient;

@Component
public class CatalogPreferenceManagerImpl implements CatalogPreferenceManager {

    private static final Logger log = Logger.getLogger(CatalogPreferenceManagerImpl.class);

    @Autowired
    private ModelClient client;

    public TenantPreferences getPreferencesByTenant(String tenantId) {
        List<TenantPreferences> list = client.tenantPreferences().findAll(tenantId);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }

        return createNewPreferences(tenantId.toString());
    }

    private TenantPreferences createNewPreferences(String tenantId) {
        TenantPreferences newTenantPreferences = new TenantPreferences();
        newTenantPreferences.setTenant(tenantId.toString());
        client.save(newTenantPreferences);
        return newTenantPreferences;
    }

    public TenantPreferences getPreferences(URI id) {
        return client.tenantPreferences().findById(id);
    }

    public void updatePreferences(TenantPreferences tenantPreferences) {
        client.save(tenantPreferences);
    }

}
