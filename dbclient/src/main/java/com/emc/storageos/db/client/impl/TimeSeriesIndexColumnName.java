/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.annotations.Component;
import com.netflix.astyanax.util.TimeUUIDUtils;

import java.util.UUID;

public class TimeSeriesIndexColumnName implements CompositeIndexColumnName {
    private @Component(ordinal = 0)
    String className;

    private @Component(ordinal = 1)
    long timeInMicros;

    private @Component(ordinal = 2)
    String id;

    private @Component(ordinal = 3)
    String four;

    private @Component(ordinal = 4)
    UUID timeUUID;

    // used by serialize/deserialize
    public TimeSeriesIndexColumnName() {
    }

    public TimeSeriesIndexColumnName(String className, String id, UUID timeUUID) {
        this(className, -1, id, "", timeUUID);
    }

    public TimeSeriesIndexColumnName(String className, long timestamp, String id, String four, UUID timeUUID) {
        this.className = className;
        this.timeInMicros = timestamp < 0 ? TimeUUIDUtils.getMicrosTimeFromUUID(timeUUID): timestamp;
        this.id = id;
        this.four=four;
        this.timeUUID = timeUUID;
    }

    @Override
    public String getOne() {
        return className;
    }

    @Override
    public String getTwo() {
        return Long.toString(timeInMicros);
    }

    @Override
    public String getThree() {
        return id;
    }

    @Override
    public String getFour() {
        return four;
    }

    @Override
    public UUID getTimeUUID() {
        return timeUUID;
    }

    public long getTimeInMicros() {
        return timeInMicros;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(className);

        builder.append(":")
                .append(Long.toString(timeInMicros))
                .append(":")
                .append(id)
                .append(":")
                .append(four)
                .append(":")
                .append(timeUUID);

        return builder.toString();
    }
}
