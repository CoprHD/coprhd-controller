/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

/**
 * This class represents cassandra column family
 */
public class ColumnFamilyDefinition {
    
    public static final String DATA_CF_COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";
    
    public static final String INDEX_CF_COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";
    
    public static final String ORDER_INDEX_CF_COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(" +
                    "org.apache.cassandra.db.marshal.UTF8Type," + // className
                    "org.apache.cassandra.db.marshal.LongType,"+ // timestamp
                    "org.apache.cassandra.db.marshal.UTF8Type," + // object ID
                    "org.apache.cassandra.db.marshal.UTF8Type," +
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";
    public static final String ORDER_CLASSNAME_INDEX_COMPARATOR_NAME =
            "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.UTF8Type,"+ // class name
                    "org.apache.cassandra.db.marshal.LongType," +    // timestamp
                    "org.apache.cassandra.db.marshal.UTF8Type," +    // object ID
                    "org.apache.cassandra.db.marshal.UTF8Type," +    //
                    "org.apache.cassandra.db.marshal.TimeUUIDType)";
    
    public static enum ComparatorType {
        CompositeType,
        TimeUUIDType,
        ByteBuffer
    }
    
    private String name;
    private ComparatorType comparatorType;
    private String comparator;
    
    public ColumnFamilyDefinition(String name, ComparatorType comparatorType) {
        this(name, comparatorType, null);
    }

    public ColumnFamilyDefinition(String name, ComparatorType comparatorType, String comparator) {
        super();
        this.name = name;
        this.comparatorType = comparatorType;
        this.comparator = comparator;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ComparatorType getComparatorType() {
        return comparatorType;
    }

    public void setComparatorType(ComparatorType comparatorType) {
        this.comparatorType = comparatorType;
    }

    public String getComparator() {
        return comparator;
    }

    public void setComparator(String comparator) {
        this.comparator = comparator;
    }

}
