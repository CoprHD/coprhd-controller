/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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
