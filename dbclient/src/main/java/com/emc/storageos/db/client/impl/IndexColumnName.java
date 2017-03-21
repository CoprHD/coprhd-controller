/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Composite column name for all index entries
 */
public class IndexColumnName implements CompositeIndexColumnName {
	private String _one;
    private String _two;
    private String _three;
    private String _four;
    private UUID _timeUUID;
    private ByteBuffer value;
    
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
    
    public IndexColumnName(String one, String two, String three, String four, UUID timeUUID, ByteBuffer value) {
        _one = one;
        _two = two;
        _three = three;
        _four = four;
        _timeUUID = timeUUID;
        this.value = value;
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
    
    public ByteBuffer getValue() {
        return value;
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
