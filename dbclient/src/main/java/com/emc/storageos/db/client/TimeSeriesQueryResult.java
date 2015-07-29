/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client;

import com.emc.storageos.db.client.model.TimeSeriesSerializer;

/**
 * Callback interface for receiving time series data chunk
 * 
 * @param <T>
 */
public interface TimeSeriesQueryResult<T extends TimeSeriesSerializer.DataPoint> {
    /**
     * queryTimeSeries calls this method for each time series record. For example,
     * queryTimeSeries(EventTimeSeries.class, ...) will call this method with deserialized
     * Event for every record in the specified time bucket.
     * 
     * @param data
     */
    void data(T data, long insertionTimeMs);

    /**
     * Called when all data points have been retrieved
     */
    void done();

    /**
     * Called when an error is encountered during query. Maybe called multiple
     * times for each thread servicing this query
     * 
     * @param e
     */
    void error(Throwable e);
}
