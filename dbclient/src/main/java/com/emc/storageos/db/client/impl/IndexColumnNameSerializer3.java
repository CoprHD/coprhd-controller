package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.serializers.AnnotatedCompositeSerializer;

/**
 * Created by brian on 16-11-16.
 */
public class IndexColumnNameSerializer3 extends AnnotatedCompositeSerializer<IndexColumnName3> {
    private static final IndexColumnNameSerializer3 instance = new IndexColumnNameSerializer3();
    private static final String COMPARATOR_NAME =
            //"org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.TimeUUIDType,"+
            //"org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.TimestampType,"+
            //"org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.LongType,"+
            "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.LongType,"+ // timestamp
                    "org.apache.cassandra.db.marshal.UTF8Type," +    // username
                    "org.apache.cassandra.db.marshal.UTF8Type," +    // object ID
                    "org.apache.cassandra.db.marshal.UTF8Type," +    //
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";

    public static IndexColumnNameSerializer3 get() {
        return instance;
    }

    public static String getComparatorName() {
        return COMPARATOR_NAME;
    }

    public IndexColumnNameSerializer3() {
        super(IndexColumnName3.class);
    }
}
