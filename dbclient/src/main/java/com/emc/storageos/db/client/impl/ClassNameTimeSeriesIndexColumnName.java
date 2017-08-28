/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.annotations.Component;
import com.netflix.astyanax.util.TimeUUIDUtils;

import java.util.UUID;

/**
 * Composite column name for all index entries
 */
public class ClassNameTimeSeriesIndexColumnName implements CompositeIndexColumnName {
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
    public ClassNameTimeSeriesIndexColumnName() {
    }

    public ClassNameTimeSeriesIndexColumnName(String className, String id, UUID timeUUID) {
        this(-1, className, id, "", timeUUID);
    }

    public ClassNameTimeSeriesIndexColumnName(long timestamp, String className, String id, String four, UUID timeUUID) {
        this.timeInMicros = timestamp < 0 ? TimeUUIDUtils.getMicrosTimeFromUUID(timeUUID): timestamp;
        this.id = id;
        this.className = className;
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
        StringBuilder builder = new StringBuilder(Long.toString(timeInMicros));

        builder.append(":")
                .append(className)
                .append(":")
                .append(id)
                .append(":")
                .append(four)
                .append(":")
                .append(timeUUID);

        return builder.toString();
    }
}
