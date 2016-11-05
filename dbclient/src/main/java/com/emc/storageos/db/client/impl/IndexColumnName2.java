package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.annotations.Component;
import com.netflix.astyanax.util.TimeUUIDUtils;

import java.util.UUID;

/**
 * Composite column name for all index entries
 */
public class IndexColumnName2 implements CompositeIndexColumnName {
    private @Component(ordinal = 0)
    String one;

    private @Component(ordinal = 1)
    long timeInMicros;

    private @Component(ordinal = 2)
    String two;

    private @Component(ordinal = 3)
    String three;

    private @Component(ordinal = 4)
    String four;

    public IndexColumnName2() {
    }

    public IndexColumnName2(String one, String two, long timeInMicros) {
        this.one = one;
        this.two = two;
        this.timeInMicros = timeInMicros;
    }

    public IndexColumnName2(String one, String two, String three, long timestamp) {
        this.one = one;
        this.two = two;
        this.three = three;
        this.timeInMicros = timestamp;
    }

    public IndexColumnName2(String one, String two, String three, String four, long time) {
        this.one = one;
        this.two = two;
        this.three = three;
        this.four = four;
        this.timeInMicros = time;
    }

    @Override
    public String getOne() {
        return one;
    }

    @Override
    public String getTwo() {
        return two;
    }

    @Override
    public String getThree() {
        return three;
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
        StringBuilder builder = new StringBuilder(Long.toString(timeInMicros));

        builder.append(":")
                .append(one)
                .append(":")
                .append(two)
                .append(":")
                .append(three)
                .append(":")
                .append(four);

        return builder.toString();
    }
}

