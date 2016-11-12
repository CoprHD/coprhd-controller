package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.annotations.Component;
import com.netflix.astyanax.util.TimeUUIDUtils;

import java.util.UUID;

/**
 * Composite column name for all index entries
 */
public class IndexColumnName2 implements CompositeIndexColumnName {
    private @Component(ordinal = 0)
    String className;

    private @Component(ordinal = 1)
    String  inactive;

    private @Component(ordinal = 2)
    long timeInMicros;

    private @Component(ordinal = 3)
    String id;

    private @Component(ordinal = 4)
    String four;

    public IndexColumnName2() {
    }

    public IndexColumnName2(String className, String id, long timeInMicros) {
        this(className, id, false, "", timeInMicros);
    }

    public IndexColumnName2(String className, String id, boolean inactive, long timestamp) {
        this(className, id, inactive, "", timestamp);
    }

    public IndexColumnName2(String className, String id, boolean inactive, String four, long timestamp) {
        this.className = className;
        this.id = id;
        this.inactive = Boolean.toString(inactive);
        this.timeInMicros = timestamp;
        this.four = four;
        timeInMicros = timestamp;
    }

    @Override
    public String getOne() {
        return className;
    }

    @Override
    public String getTwo() {
        return id;
    }

    @Override
    public String getThree() {
        return inactive;
    }

    @Override
    public String getFour() {
        return four;
    }

    @Override
    public UUID getTimeUUID() {
        return TimeUUIDUtils.getTimeUUID(timeInMicros);
    }

    public long getTimeInMicros() {
        return timeInMicros;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(className);

        builder.append(":")
                .append(inactive)
                .append(":")
                .append(Long.toString(timeInMicros))
                .append(":")
                .append(id)
                .append(":")
                .append(four);

        return builder.toString();
    }
}

