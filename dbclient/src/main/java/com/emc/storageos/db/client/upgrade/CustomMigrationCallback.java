/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
