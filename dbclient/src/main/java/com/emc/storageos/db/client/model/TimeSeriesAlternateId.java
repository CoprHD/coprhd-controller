/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.BaseDefaultMigrationCallback;

import java.lang.annotation.*;

@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback = BaseDefaultMigrationCallback.class)
public @interface TimeSeriesAlternateId {
    String value() default "";
}