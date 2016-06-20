/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.model.*;
import com.netflix.astyanax.model.ByteBufferRange;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.serializers.TimeUUIDSerializer;
import com.netflix.astyanax.util.RangeBuilder;
import com.netflix.astyanax.util.TimeUUIDUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Encapsulate time series information
 */
public class TimeSeriesType<T extends TimeSeriesSerializer.DataPoint> implements TimeSeriesMetadata {
    private static final Logger _logger = LoggerFactory.getLogger(TimeSeriesType.class);

    private Class<? extends TimeSeries> _type;
    private int _shardCount = 1;
    private boolean _compactionOptimized = false;
    private String _cfName;
    private DateTimeFormatter _prefixFormatter;
    private TimeBucket _bucketGranularity;
    private ColumnFamily<String, UUID> _cf;
    private Integer _ttl;
    private AtomicLong _bucketIndex = new AtomicLong();
    private TimeSeries<T> _timeSeries;
    private List<TimeBucket> _supportedGranularity;

    /**
     * Constructor
     * 
     * @param clazz
     */
    public TimeSeriesType(Class<? extends TimeSeries> clazz) {
        _type = clazz;
        init();
    }

    /**
     * Gets shard index to use for next data point. Note that shard
     * index should be calculated by
     * 
     * getAndIncrementBucketIndex % shard count
     * 
     * @return
     */
    private long getNextShardIndex() {
        return _bucketIndex.getAndIncrement() % _shardCount;
    }

    /**
     * Get bucket configuration
     * 
     * @return
     */
    public TimeBucket getBucketConfig() {
        return _bucketGranularity;
    }

    /**
     * Override TTL setting
     * 
     * @param ttl
     */
    public void setTtl(Integer ttl) {
        _ttl = ttl;
    }

    /**
     * Returns row ID to use for current time (UTC). Takes into account
     * bucket granularity and shard count. Row ID does not use data
     * point's time because it's used as a way to
     * 
     * 1. load balance across rows. Data point's time stamps
     * do not guarantee such things since time series data source
     * could be out of whack
     * 2. serve as collection time stamp
     * 
     * This means that this time series implementation is not
     * a good fit for use cases that need to
     * 
     * 1. retrieve by source time stamp order
     * or
     * 2. source vs. insertion timestamp differ by a wide margin
     * 
     * @return row Id to use for next insertion
     */
    public String getRowId() {
        return getRowId(null);
    }

    public boolean getCompactOptimized() {
        return _compactionOptimized;
    }

    /**
     * Return row Id to use for given time.
     * 
     * @param time
     * @return row id to use for next insertion
     */
    public String getRowId(DateTime time) {
        if (time == null) {
            time = new DateTime(DateTimeZone.UTC);
        }
        StringBuilder rowId = new StringBuilder(_prefixFormatter.print(time));
        rowId.append(getNextShardIndex());
        return rowId.toString();
    }

    /**
     * Returns rows to query for given time bucket
     * 
     * @return
     */
    public List<String> getRows(DateTime bucket) {
        List<String> rows = new ArrayList<String>(_shardCount);
        String prefix = _prefixFormatter.print(bucket);
        for (int index = 0; index < _shardCount; index++) {
            rows.add(String.format("%1$s%2$d", prefix, index));
        }
        return rows;
    }

    /**
     * Return column range for given time and bucket granularity
     * 
     * @param time target query time
     * @param granularity granularity
     * @param pageSize page size
     * @return
     */
    public DateTime[] getColumnRange(DateTime time, TimeBucket granularity) {
        if (time.getZone() != DateTimeZone.UTC) {
            throw new IllegalArgumentException("Invalid timezone");
        }
        if (granularity.ordinal() > _bucketGranularity.ordinal()) {
            throw new IllegalArgumentException("Invalid granularity");
        }
        
        if (granularity.ordinal() < _bucketGranularity.ordinal()) {
            // finer than specified granularity
            DateTime start = DateTime.now();
            DateTime end = DateTime.now();
            switch (granularity) {
                case MONTH:
                    start = new DateTime(time.getYear(), time.getMonthOfYear(), 1, 0, 0, DateTimeZone.UTC);
                    end = start.plusMonths(1);
                    break;
                case DAY:
                    start = new DateTime(time.getYear(), time.getMonthOfYear(), time.getDayOfMonth(), 0, 0, DateTimeZone.UTC);
                    end = start.plusDays(1);
                    break;
                case HOUR:
                    start = new DateTime(time.getYear(), time.getMonthOfYear(), time.getDayOfMonth(),
                            time.getHourOfDay(), 0, DateTimeZone.UTC);
                    end = start.plusHours(1);
                    break;
                case MINUTE:
                    start = new DateTime(time.getYear(), time.getMonthOfYear(), time.getDayOfMonth(),
                            time.getHourOfDay(), time.getMinuteOfHour(), DateTimeZone.UTC);
                    end = start.plusMinutes(1);
                    break;
                case SECOND:
                    start = new DateTime(time.getYear(), time.getMonthOfYear(), time.getDayOfMonth(),
                            time.getHourOfDay(), time.getMinuteOfHour(), time.getSecondOfMinute(), DateTimeZone.UTC);
                    end = start.plusSeconds(1);
                    break;
            }
            
            return new DateTime[]{start, end};
        }
        return new DateTime[0];
    }

    /**
     * Serializer for data points
     * 
     * @return
     */
    public TimeSeriesSerializer<T> getSerializer() {
        return _timeSeries.getSerializer();
    }

    /**
     * Data point TTL
     * 
     * @return
     */
    public Integer getTtl() {
        return _ttl;
    }

    /**
     * Get CF for this time series data
     * 
     * @return
     */
    public ColumnFamily<String, UUID> getCf() {
        return _cf;
    }

    /**
     * Process annotations for time series type
     */
    @SuppressWarnings(value = "unchecked")
    private void init() {
        Annotation[] annotations = _type.getAnnotations();
        for (int i = 0; i < annotations.length; i++) {
            Annotation a = annotations[i];
            if (a instanceof Cf) {
                _cfName = ((Cf) a).value();
            } else if (a instanceof Shards) {
                _shardCount = ((Shards) a).value();
            } else if (a instanceof CompactionOptimized) {
                _compactionOptimized = true;
            } else if (a instanceof BucketGranularity) {
                _bucketGranularity = ((BucketGranularity) a).value();
                switch (_bucketGranularity) {
                    case SECOND:
                        _prefixFormatter = DateTimeFormat.forPattern("yyyyMMddHHmmss-");
                        break;
                    case MINUTE:
                        _prefixFormatter = DateTimeFormat.forPattern("yyyyMMddHHmm-");
                        break;
                    case HOUR:
                        _prefixFormatter = DateTimeFormat.forPattern("yyyyMMddHH-");
                        break;
                    case DAY:
                        _prefixFormatter = DateTimeFormat.forPattern("yyyyMMdd-");
                        break;
                    case MONTH:
                        _prefixFormatter = DateTimeFormat.forPattern("yyyyMM-");
                        break;
                    case YEAR:
                        _prefixFormatter = DateTimeFormat.forPattern("yyyy-");
                        break;
                }
                _supportedGranularity = new ArrayList<TimeBucket>();
                TimeBucket[] buckets = TimeBucket.values();
                for (int j = 0; j < buckets.length; j++) {
                    TimeBucket bucket = buckets[j];
                    if (bucket.ordinal() > _bucketGranularity.ordinal()) {
                        break;
                    }
                    _supportedGranularity.add(bucket);
                }
                _supportedGranularity = Collections.unmodifiableList(_supportedGranularity);
            } else if (a instanceof Ttl) {
                _ttl = ((Ttl) a).value();
            } else {
                throw new IllegalArgumentException("Unexpected annotation");
            }
        }
        _cf = new ColumnFamily<String, UUID>(_cfName,
                StringSerializer.get(),
                TimeUUIDSerializer.get());
        try {
            _timeSeries = _type.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String getName() {
        return _type.getSimpleName();
    }

    @Override
    public List<TimeBucket> getSupportedQueryGranularity() {
        return _supportedGranularity;
    }

    /**
     * Create max range time UUID for given millisecond - see UUIDGen for algorithm/source.
     * 
     * @param maxTime
     * @return
     */
    private UUID createMaxTimeUUID(long maxTime) {
        long time;
        // UTC time
        long timeToUse = (maxTime * 10000) + 0x01b21dd213814000L + 9999;
        // time low
        time = timeToUse << 32;
        // time mid
        time |= (timeToUse & 0xFFFF00000000L) >> 16;
        // time hi and version
        time |= 0x1000 | ((timeToUse >> 48) & 0x0FFF); // version 1

        return new UUID(time, 0xffffffffffffffffL);
    }
}
