/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

public class ExceptionMigration extends BaseCustomMigrationCallback {
    @Override
    public void process() {
        throw new RuntimeException("Exception from Migration Callback");
    }

}
