/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.AddIndexMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.TimeSeriesIndexMigration;

import java.lang.annotation.*;

@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback = TimeSeriesIndexMigration.class)
public @interface TimeSeriesAlternateId {
    String value() default "";
}