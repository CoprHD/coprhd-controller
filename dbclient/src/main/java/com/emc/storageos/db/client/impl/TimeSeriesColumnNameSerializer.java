/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.serializers.AnnotatedCompositeSerializer;

public class TimeSeriesColumnNameSerializer extends AnnotatedCompositeSerializer<TimeSeriesIndexColumnName> {
    private static final TimeSeriesColumnNameSerializer instance = new TimeSeriesColumnNameSerializer();
    private static final String COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(" +
                    "org.apache.cassandra.db.marshal.UTF8Type," + // className
                    "org.apache.cassandra.db.marshal.LongType,"+ // timestamp
                    "org.apache.cassandra.db.marshal.UTF8Type," + // object ID
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";

    public static TimeSeriesColumnNameSerializer get() {
        return instance;
    }

    public static String getComparatorName() {
        return COMPARATOR_NAME;
    }

    public TimeSeriesColumnNameSerializer() {
        super(TimeSeriesIndexColumnName.class);
    }
}
