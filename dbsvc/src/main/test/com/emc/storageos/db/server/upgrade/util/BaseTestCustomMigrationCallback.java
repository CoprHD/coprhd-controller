/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util;

import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * @author cgarber
 * 
 */
public abstract class BaseTestCustomMigrationCallback extends BaseCustomMigrationCallback {

    public abstract void verify();

}
