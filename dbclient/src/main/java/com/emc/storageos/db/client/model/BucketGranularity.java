/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.TimeSeriesMetadata;

import java.lang.annotation.*;

/**
 * Time unit for each (row) bucket
 */
@Documented
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface BucketGranularity {
    /**
     * Bucket time granularity
     * 
     * @return
     */
    TimeSeriesMetadata.TimeBucket value() default TimeSeriesMetadata.TimeBucket.HOUR;
}
