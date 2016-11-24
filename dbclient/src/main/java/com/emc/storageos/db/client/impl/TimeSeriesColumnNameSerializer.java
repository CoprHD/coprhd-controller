package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.serializers.AnnotatedCompositeSerializer;

/**
 * Created by brian on 16-11-16.
 */
public class TimeSeriesColumnNameSerializer extends AnnotatedCompositeSerializer<TimeSeriesIndexColumnName> {
    private static final TimeSeriesColumnNameSerializer instance = new TimeSeriesColumnNameSerializer();
    private static final String COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.LongType,"+ // timestamp
                    "org.apache.cassandra.db.marshal.UTF8Type," +    // username
                    "org.apache.cassandra.db.marshal.UTF8Type," +    // object ID
                    "org.apache.cassandra.db.marshal.UTF8Type," +    //
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
