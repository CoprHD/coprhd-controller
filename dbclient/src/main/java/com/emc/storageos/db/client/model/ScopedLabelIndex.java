/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * Index for case insensitive scoped prefix search
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ScopedLabelIndex {
    String cf() default "";

    int minChars() default 2;
}
