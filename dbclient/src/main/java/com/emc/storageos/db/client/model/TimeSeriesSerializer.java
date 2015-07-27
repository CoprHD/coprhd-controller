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

import java.io.Serializable;

import javax.xml.bind.annotation.XmlTransient;

/**
 * De/deserializer API for time series data
 */
public interface TimeSeriesSerializer<T extends TimeSeriesSerializer.DataPoint> {
    //Temporarily, both Stat and Event Models support Java serialization,
    //hence to serialize timeinMillis, there is necessity to make DataPoint serializable.
    public abstract class DataPoint implements Serializable{
        /**
         * Data point time stamp
         */
        protected long _timeInMillis;

        /**
         * set timestamp
         * @param time  timestamp in msec as long
         */
        public void setTimeInMillis(long time) {
            _timeInMillis = time;
        }

        /**
         * get timestamp
         * @return long
         */
        @XmlTransient
        @SerializationIndex(1)
        public long getTimeInMillis() {
            return _timeInMillis;
        }
    }

    /**
     * Serializes data point to byte array
     * 
     * @param data data point
     * @return serialized byte array
     */
    public byte[] serialize(T data);

    /**
     * Deserialize byte array into data point
     * 
     * @param data
     * @return
     */
    public T deserialize(byte[] data);
}
