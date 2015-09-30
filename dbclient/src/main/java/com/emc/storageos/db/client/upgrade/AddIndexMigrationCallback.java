/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade;

public class AddIndexMigrationCallback extends BaseDefaultMigrationCallback {

    @Override
    public void process() {

        MigrateIndexHelper.migrateAddedIndex(getInternalDbClient(), cfClass, fieldName, annotation.annotationType().getCanonicalName());

    }

}
