/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

@Documented
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
/**
 * Column family a data object class maps to
 */
public @interface Cf {
    /**
     * Column family name
     * 
     * @return
     */
    String value() default "";
}
