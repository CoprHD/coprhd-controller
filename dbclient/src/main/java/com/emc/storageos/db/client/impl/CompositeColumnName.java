/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.nio.ByteBuffer;
import java.util.UUID;

import com.netflix.astyanax.annotations.Component;

/**
 * Composite column name for all data object fields
 */
public class CompositeColumnName {
    private String rowKey;
    
    private @Component(ordinal = 0)
    String _one;
    private @Component(ordinal = 1)
    String _two;
    private @Component(ordinal = 2)
    String _three;
    private @Component(ordinal = 3)
    UUID _timeUUID;
    
    private ByteBuffer value;
    private long writeTimeStampMS; // unit is MS

    public CompositeColumnName() {
    }

    public CompositeColumnName(String one) {
        this(null, one, null, null, null, null, 0);
    }

    public CompositeColumnName(String one, String two) {
        this(null, one, two, null, null, null, 0);
    }

    public CompositeColumnName(String one, String two, UUID timeUUID) {
        this(null, one, two, null, timeUUID, null, 0);
    }

    public CompositeColumnName(String one, String two, String three) {
        this(null, one, two, three, null, null, 0);
    }

    public CompositeColumnName(String one, String two, String three, UUID timeUUID) {
        this(null, one, two, three, timeUUID, null, 0);
    }
    
    public CompositeColumnName(String rowKey, String one, String two, String three, UUID timeUUID, ByteBuffer value) {
        this(rowKey, one, two, three, timeUUID, value, 0);
    }
    
    public CompositeColumnName(String rowKey, String one, String two, String three, UUID timeUUID, ByteBuffer value, long writeTimeStampMS) {
        this.rowKey = rowKey;
        _one = one;
        _two = two;
        _three = three;
        _timeUUID = timeUUID;
        this.value = value;
        this.writeTimeStampMS = writeTimeStampMS;
    }

    public String getOne() {
        return _one;
    }

    public String getTwo() {
        return _two;
    }

    public String getThree() {
        return _three;
    }

    public UUID getTimeUUID() {
        return _timeUUID;
    }

    public String getRowKey() {
        return rowKey;
    }

    public ByteBuffer getValue() {
        return value;
    }

    public long getWriteTimeStampMS() {
        return writeTimeStampMS;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return String.format("%1$s:%2$s:%3$s", _one, _two, _three);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CompositeColumnName)) {
            return false;
        }
        return toString().equals(obj.toString());
    }
}
