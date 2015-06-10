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

import com.emc.storageos.db.client.model.TimeSeriesSerializer;

/**
 * Callback interface for receiving time series data chunk
 *
 * @param <T>
 */
public interface TimeSeriesQueryResult<T extends TimeSeriesSerializer.DataPoint> {
    /**
     * queryTimeSeries calls this method for each time series record.   For example,
     * queryTimeSeries(EventTimeSeries.class, ...) will call this method with deserialized
     * Event for every record in the specified time bucket.
     *
     * @param data
     */
    public void data(T data, long insertionTimeMs);

    /**
     * Called when all data points have been retrieved
     */
    public void done();

    /**
     * Called when an error is encountered during query.  Maybe called multiple
     * times for each thread servicing this query
     *
     * @param e
     */
    public void error(Throwable e);
}
