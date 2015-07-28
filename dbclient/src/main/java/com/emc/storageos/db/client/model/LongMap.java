/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
