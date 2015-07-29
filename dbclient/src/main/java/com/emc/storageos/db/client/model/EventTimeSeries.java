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

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.TimeSeriesMetadata;

/**
 * CF definition for event time series data
 */
@Cf("Events")
@Shards(10)
@BucketGranularity(TimeSeriesMetadata.TimeBucket.HOUR)
@Ttl(60 * 60 * 24 * 30 /* 30 days */)
public class EventTimeSeries implements TimeSeries<Event> {
    private EventSerializer _serializer = new EventSerializer();

    @Override
    public EventSerializer getSerializer() {
        return _serializer;
    }

    /**
     * Event serializer implementation
     */
    public static class EventSerializer implements TimeSeriesSerializer<Event> {
        private GenericSerializer _genericSerializer = new GenericSerializer();

        @Override
        public byte[] serialize(Event data) {
            return _genericSerializer.toByteArray(Event.class, data);
        }

        @Override
        public Event deserialize(byte[] data) {
            return _genericSerializer.fromByteArray(Event.class, data);
        }
    }
}
