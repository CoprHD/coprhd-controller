package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.annotations.Component;

import java.util.UUID;

/**
 * Composite column name for all index entries
 */
public class IndexColumnName2 implements CompositeIndexColumnName {
    private @Component(ordinal = 0)
    UUID _timeUUID;
    private @Component(ordinal = 1)
    String _one;
    private @Component(ordinal = 2)
    String _two;
    private @Component(ordinal = 3)
    String _three;
    private @Component(ordinal = 4)
    String _four;

    public IndexColumnName2() {
    }

    public IndexColumnName2(String one, UUID timeUUID) {
        _one = one;
        _timeUUID = timeUUID;
    }

    public IndexColumnName2(String one, String two, UUID timeUUID) {
        _one = one;
        _two = two;
        _timeUUID = timeUUID;
    }

    public IndexColumnName2(String one, String two, String three, UUID timeUUID) {
        _one = one;
        _two = two;
        _three = three;
        _timeUUID = timeUUID;
    }

    public IndexColumnName2(String one, String two, String three, String four, UUID timeUUID) {
        _one = one;
        _two = two;
        _three = three;
        _four = four;
        _timeUUID = timeUUID;
    }

    @Override
    public String getOne() {
        return _one;
    }

    @Override
    public String getTwo() {
        return _two;
    }

    @Override
    public String getThree() {
        return _three;
    }

    @Override
    public String getFour() {
        return _four;
    }

    @Override
    public UUID getTimeUUID() {
        return _timeUUID;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(_timeUUID.toString());

        builder.append(":")
                .append(_one)
                .append(":")
                .append(_two)
                .append(":")
                .append(_three)
                .append(":")
                .append(_four);

        return builder.toString();
    }
}

