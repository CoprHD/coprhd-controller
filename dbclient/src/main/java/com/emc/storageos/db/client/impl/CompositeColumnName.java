/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.annotations.Component;

import java.util.UUID;

/**
 * Composite column name for all data object fields
 */
public class CompositeColumnName {
    private @Component(ordinal = 0)
    String _one;
    private @Component(ordinal = 1)
    String _two;
    private @Component(ordinal = 2)
    String _three;
    private @Component(ordinal = 3)
    UUID _timeUUID;

    public CompositeColumnName() {
    }

    public CompositeColumnName(String one) {
        _one = one;
    }

    public CompositeColumnName(String one, String two) {
        _one = one;
        _two = two;
    }

    public CompositeColumnName(String one, String two, UUID timeUUID) {
        _one = one;
        _two = two;
        _timeUUID = timeUUID;
    }

    public CompositeColumnName(String one, String two, String three) {
        _one = one;
        _two = two;
        _three = three;
    }

    public CompositeColumnName(String one, String two, String three, UUID timeUUID) {
        _one = one;
        _two = two;
        _three = three;
        _timeUUID = timeUUID;
    }

    public String getOne() {
        return _one;
    }

    public String getTwo() {
        return _two;
    }

    public String getThree() {
        return _three;
    }

    public UUID getTimeUUID() {
        return _timeUUID;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return String.format("%1$s:%2$s:%3$s", _one, _two, _three);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CompositeColumnName)) {
            return false;
        }
        return toString().equals(obj.toString());
    }
}
