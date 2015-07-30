/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.emc.storageos.db.client.upgrade.BaseDefaultMigrationCallback;

@Documented
@Target({ ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface UpgradeAllowed {
    Class<? extends BaseDefaultMigrationCallback> migrationCallback() default BaseDefaultMigrationCallback.class;
}
