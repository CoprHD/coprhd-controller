/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Time series interface
 */
public interface TimeSeries<T extends TimeSeriesSerializer.DataPoint> {
    /**
     * Get serializer for time series data points
     * 
     * @return
     */
    public TimeSeriesSerializer<T> getSerializer();
}
