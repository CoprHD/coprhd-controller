/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade;

import java.lang.annotation.*;

/**
 * annotation to provide a custom migration callbacks on new fields or new CFs
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomMigrationCallback {
    // the callback implementation class
    Class<? extends BaseCustomMigrationCallback> callback();
    // dependency if any, used for ordering callbacks
    Class<? extends BaseCustomMigrationCallback> runAfter() default BaseCustomMigrationCallback.class;
}
