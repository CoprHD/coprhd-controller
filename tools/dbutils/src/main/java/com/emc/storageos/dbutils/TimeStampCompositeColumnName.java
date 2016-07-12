package com.emc.storageos.dbutils;

import java.nio.ByteBuffer;
import java.util.UUID;

import com.emc.storageos.db.client.impl.CompositeColumnName;

public class TimeStampCompositeColumnName extends CompositeColumnName {
    private long writeTimeStampMS; // unit is MS

    public TimeStampCompositeColumnName(String rowKey, String one, String two, String three, UUID timeUUID, ByteBuffer value) {
        super(rowKey, one, two, three, timeUUID, value);
    }

    public long getWriteTimeStampMS() {
        return writeTimeStampMS;
    }

    public void setWriteTimeStampMS(long writeTimeStampMS) {
        this.writeTimeStampMS = writeTimeStampMS;
    }
}
