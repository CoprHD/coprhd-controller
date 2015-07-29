/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * Number of shards for time series data
 */
@Documented
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Shards {
    /**
     * Number of shards
     * 
     * @return
     */
    int value() default 10;
}
