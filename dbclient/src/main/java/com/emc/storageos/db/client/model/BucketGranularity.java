/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 * <p/>
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
