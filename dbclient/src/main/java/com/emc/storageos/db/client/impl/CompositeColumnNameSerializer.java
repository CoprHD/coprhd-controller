/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.serializers.AnnotatedCompositeSerializer;

/**
 * Composite column serializer
 */
public class CompositeColumnNameSerializer extends AnnotatedCompositeSerializer<CompositeColumnName> {
    private static final CompositeColumnNameSerializer INSTANCE = new CompositeColumnNameSerializer();
    private static final String COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";

    public static CompositeColumnNameSerializer get() {
        return INSTANCE;
    }

    public static String getComparatorName() {
        return COMPARATOR_NAME;
    }

    public CompositeColumnNameSerializer() {
        super(CompositeColumnName.class);
    }
}
