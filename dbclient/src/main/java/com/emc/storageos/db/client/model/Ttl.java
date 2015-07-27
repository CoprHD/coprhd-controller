/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * Marks a field with limited time to live.  When a field
 * with this annotation is written, it's scheduled for deletion
 * after configured time period (in seconds)
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Ttl {
    // by default, 1 hr
    int value() default 60 * 60;
}
