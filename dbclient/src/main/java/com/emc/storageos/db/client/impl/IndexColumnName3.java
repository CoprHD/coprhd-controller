package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.annotations.Component;
import com.netflix.astyanax.util.TimeUUIDUtils;

import java.util.UUID;

/**
 * Created by brian on 16-11-16.
 */
public class IndexColumnName3 implements CompositeIndexColumnName {
    private @Component(ordinal = 0)
    long timeInMicros;

    private @Component(ordinal = 1)
    String two;

    private @Component(ordinal = 2)
    String three;

    private @Component(ordinal = 3)
    String four;

    private @Component(ordinal = 4)
    UUID timeUUID;

    // used by serialize/deserialize
    public IndexColumnName3() {
    }

    public IndexColumnName3(String two, UUID timeUUID) {
        this(-1, two, "", "", timeUUID);
    }


    public IndexColumnName3(long timestamp, String two, String three, String four, UUID timeUUID) {
        this.timeInMicros = timestamp < 0 ? TimeUUIDUtils.getMicrosTimeFromUUID(timeUUID): timestamp;

        this.two = two;
        this.three = three;
        this.four=four;
        this.timeUUID = timeUUID;
    }

    @Override
    public String getOne() {
        return Long.toString(timeInMicros);
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
        return timeUUID;
    }

    public long getTimeInMicros() {
        return timeInMicros;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(Long.toString(timeInMicros));

        builder.append(":")
                .append(two)
                .append(":")
                .append(three)
                .append(":")
                .append(four)
                .append(":")
                .append(timeUUID);

        return builder.toString();
    }
}
