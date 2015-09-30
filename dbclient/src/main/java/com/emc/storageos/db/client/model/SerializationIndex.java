/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * Field index for serializing - never reuse the same value, will break compatibility
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface SerializationIndex {
    byte value() default 0;
}
