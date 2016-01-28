/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade;

import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class AddIndexMigrationCallback extends BaseDefaultMigrationCallback {

    @Override
    public void process() throws MigrationCallbackException {

        MigrateIndexHelper.migrateAddedIndex(getInternalDbClient(), cfClass, fieldName, annotation.annotationType().getCanonicalName());

    }

}
