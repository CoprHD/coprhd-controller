/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.nio.ByteBuffer;
import java.util.Map;

public class LongMap extends AbstractChangeTrackingMap<Long> {

    public LongMap() {
    }

    public LongMap(Map<String, Long> source) {
        super(source);
    }

    @Override
    public Long valFromByte(byte[] value) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(value);
        buffer.flip(); 
        return buffer.getLong();
    }

    @Override
    public byte[] valToByte(Long value) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(value);
        return buffer.array();
    }

}
