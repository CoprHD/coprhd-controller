/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;

public class ExecutionWindowFinder extends TenantModelFinder<ExecutionWindow> {

    public ExecutionWindowFinder(DBClientWrapper client) {
        super(ExecutionWindow.class, client);
    }

}
