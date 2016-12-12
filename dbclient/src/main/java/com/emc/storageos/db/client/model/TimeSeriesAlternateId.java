/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.AddIndexMigrationCallback;

import java.lang.annotation.*;

/**
 * Created by brian on 16-11-16.
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback = AddIndexMigrationCallback.class)
public @interface TimeSeriesAlternateId {
    String value() default "";
}