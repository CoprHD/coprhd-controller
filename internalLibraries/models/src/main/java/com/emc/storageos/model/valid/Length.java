/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.valid;

import java.lang.annotation.*;

/**
 * Annotation that defines minimum and maximum lengths for a string field
 */
@Documented
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Length {
    int min() default 0;

    int max() default Integer.MAX_VALUE;
}
