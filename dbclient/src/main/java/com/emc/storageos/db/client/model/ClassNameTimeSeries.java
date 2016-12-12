/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

import com.emc.storageos.db.client.upgrade.AddIndexMigrationCallback;

/**
 * Alternate ID field marker
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback = AddIndexMigrationCallback.class)
public @interface ClassNameTimeSeries {
    String value() default "";
}

