/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.serializers.AnnotatedCompositeSerializer;

public class ClassNameTimeSeriesSerializer extends AnnotatedCompositeSerializer<ClassNameTimeSeriesIndexColumnName> {
    private static final ClassNameTimeSeriesSerializer instance = new ClassNameTimeSeriesSerializer();
    private static final String COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.UTF8Type,"+ // class name
                    "org.apache.cassandra.db.marshal.LongType," +    // timestamp
                    "org.apache.cassandra.db.marshal.UTF8Type," +    // object ID
                    "org.apache.cassandra.db.marshal.UTF8Type," +    //
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";

    public static ClassNameTimeSeriesSerializer get() {
        return instance;
    }

    public static String getComparatorName() {
        return COMPARATOR_NAME;
    }

    public ClassNameTimeSeriesSerializer() {
        super(ClassNameTimeSeriesIndexColumnName.class);
    }
}