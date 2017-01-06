/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.annotations.Component;

import java.util.UUID;

/**
 * Composite column name for all index entries
 */
public class IndexColumnName implements CompositeIndexColumnName {
    private @Component(ordinal = 0)
    String _one;
    private @Component(ordinal = 1)
    String _two;
    private @Component(ordinal = 2)
    String _three;
    private @Component(ordinal = 3)
    String _four;
    private @Component(ordinal = 4)
    UUID _timeUUID;

    public IndexColumnName() {
    }

    public IndexColumnName(String one, UUID timeUUID) {
        _one = one;
        _timeUUID = timeUUID;
    }

    public IndexColumnName(String one, String two, UUID timeUUID) {
        _one = one;
        _two = two;
        _timeUUID = timeUUID;
    }

    public IndexColumnName(String one, String two, String three, UUID timeUUID) {
        _one = one;
        _two = two;
        _three = three;
        _timeUUID = timeUUID;
    }

    public IndexColumnName(String one, String two, String three, String four, UUID timeUUID) {
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
        return _one + ":" +
                _two + ":" +
                _three + ":" +
                _four + ":" +
                _timeUUID;
    }
}
