/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import com.emc.storageos.db.client.model.uimodels.TenantPreferences;

public class TenantPreferencesFinder extends TenantModelFinder<TenantPreferences> {

    public TenantPreferencesFinder(DBClientWrapper client) {
        super(TenantPreferences.class, client);
    }
}
