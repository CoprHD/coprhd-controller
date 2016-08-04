/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;


import com.emc.storageos.db.client.model.uimodels.EphemeralObject;

public class EphemeralObjectFinder extends TenantModelFinder<EphemeralObject> {

    public EphemeralObjectFinder(DBClientWrapper client) {
        super(EphemeralObject.class, client);
    }
}
