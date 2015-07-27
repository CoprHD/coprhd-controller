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

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.Serializer;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.UUID;

/**
 * Wraps basic column name / value information for use in serializer
 */
public class ColumnWrapper implements Column<CompositeColumnName> {
    private CompositeColumnName _columnName;
    private Object _val;

    public ColumnWrapper(CompositeColumnName name, Object val) {
        _columnName = name;
        _val = val;
    }

    @Override
    public CompositeColumnName getName() {
        return _columnName;
    }

    @Override
    public ByteBuffer getRawName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getTimestamp() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> V getValue(Serializer<V> valSer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getStringValue() {
        return (String)_val;
    }

    @Override
    public int getIntegerValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDoubleValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLongValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getByteArrayValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBooleanValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuffer getByteBufferValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getDateValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getUUIDValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <C2> ColumnList<C2> getSubColumns(Serializer<C2> ser) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isParentColumn() {
        return false;
    }

    @Override
    public int getTtl() {
        return 0;
    }

    @Override
    public boolean hasValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte getByteValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCompressedStringValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getFloatValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public short getShortValue() {
        throw new UnsupportedOperationException();
    }
}
