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

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;

import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;

import com.emc.storageos.db.client.TimeSeriesMetadata;

/**
 * Interface to retrieve events from underlying persistence layer
 * 
 */
public interface EventRetriever {

    /**
     * Retrieve all events and stream them to the output stream
     * 
     * @param time
     *            - time of the bucket to be retrieved
     * @param bucket
     *            - granularity of the time, eg. HOUR, MINUTE, etc.
     * @param type
     *            - media type to be streamed
     * @param writer
     *            - the output writer to stream retrived events
     * @throws MarshallingExcetion
     *             - event object marshalling failed
     */
    public void getBulkEvents(DateTime time, TimeSeriesMetadata.TimeBucket bucket,
            MediaType type, Writer writer) throws MarshallingExcetion;
}
