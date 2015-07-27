/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.AddIndexMigrationCallback;

import java.lang.annotation.*;

/**
 * Index for case insensitive prefix search
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback=AddIndexMigrationCallback.class)
public @interface PrefixIndex {
    String cf() default "";
    int minChars() default 2;
    boolean scoped() default false;
}
