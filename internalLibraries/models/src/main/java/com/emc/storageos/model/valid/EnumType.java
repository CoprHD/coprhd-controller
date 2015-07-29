/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.valid;

import java.lang.annotation.*;

/**
 * Used for marking string fields with possible values defined by
 * given enum type. Note that string is used for extensibility reasons.
 */
@Documented
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumType {
    Class<? extends Enum> value();
}
