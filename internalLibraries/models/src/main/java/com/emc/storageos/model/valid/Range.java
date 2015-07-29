/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.valid;

import java.lang.annotation.*;

/**
 * Annotation that defines minimum and maximum for a numeric field
 */
@Documented
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Range {
    long min() default 0;

    long max() default Long.MAX_VALUE;
}
