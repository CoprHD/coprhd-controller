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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import com.emc.storageos.db.client.TimeSeriesMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CF definition for statistics time series data
 */
@Cf("Stats")
@CompactionOptimized
@Shards(10)
@BucketGranularity(TimeSeriesMetadata.TimeBucket.HOUR)
@Ttl(60 * 60 * 24 * 7 /* 7 days */)
public class StatTimeSeries implements TimeSeries<Stat> {
    private static final Logger _logger = LoggerFactory
            .getLogger(StatTimeSeries.class);
    private StatSerializer _serializer = new StatSerializer();

    @Override
    public StatSerializer getSerializer() {
        return _serializer;
    }

    /**
     * Stat serializer implementation ;Default Serialization is not efficient (version problems),
     * but till we finalize Serializer Implementation, this code remains.
     * For demo purpose, will use Default Serialization
     * To-Do: use Protocol Buffers
     */
    public static class StatSerializer implements TimeSeriesSerializer<Stat> {
        @Override
        public byte[] serialize(Stat data) {
            byte[] Objbytes = null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = null;
            try {
                out = new ObjectOutputStream(bos);
                out.writeObject(data);
                Objbytes = bos.toByteArray();
            } catch (Exception e) {
                _logger.error("Serializing Object to byte Array Exception  :" + e);
            } finally {
                try {
                    out.close();
                    bos.close();
                } catch (IOException e) {
                    _logger.error("Error while closing Streams " + e);
                }
            }
            return Objbytes;
        }

        @Override
        public Stat deserialize(byte[] data) {
            Object statsObj = null;
            ByteArrayInputStream bis = null;
            ObjectInput in = null;
            try {
                bis = new ByteArrayInputStream(data);
                in = new ObjectInputStream(bis);
                statsObj = in.readObject();
            } catch (Exception e1) {
                _logger.error("DeSerializing Object to byte Array Exception  :" + e1);
            } finally {
                try {
                    in.close();
                    bis.close();
                } catch (IOException e1) {
                    _logger.error("Error while closing Streams " + e1);
                }
            }
            return (Stat) statsObj;
        }
    }
}
