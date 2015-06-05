/**
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

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.serializers.AnnotatedCompositeSerializer;

/**
 * Index column serializer
 */
public class IndexColumnNameSerializer extends AnnotatedCompositeSerializer<IndexColumnName> {
    private static final IndexColumnNameSerializer _instance = new IndexColumnNameSerializer();
    private static final String COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";

    public static IndexColumnNameSerializer get() {
        return _instance;
    }

    public static String getComparatorName() {
        return COMPARATOR_NAME;
    }

    public IndexColumnNameSerializer() {
        super(IndexColumnName.class);
    }
}
