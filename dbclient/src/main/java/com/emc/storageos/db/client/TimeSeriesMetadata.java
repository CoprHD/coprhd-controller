/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client;

import java.util.List;

/**
 * Times series metadata
 */
public interface TimeSeriesMetadata {
    /**
     * Time buckets
     */
    public enum TimeBucket {
        SECOND,
        MINUTE,
        HOUR,
        DAY,
        MONTH,
        YEAR
    }

    /**
     * Name of this time series data type
     * 
     * @return
     */
    String getName();

    /**
     * Supported query granularity
     * 
     * @return bucket granularities at which you can query this time series data type
     */
    List<TimeBucket> getSupportedQueryGranularity();
}
