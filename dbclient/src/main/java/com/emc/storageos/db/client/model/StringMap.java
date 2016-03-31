/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.nio.charset.Charset;
import java.util.*;

/**
 * Map type supported by DB mapper
 */
public class StringMap extends AbstractChangeTrackingMap<String> {
    /**
     * Default constructor
     */
    public StringMap() {
    }

    /**
     * Constructs a map with the same mapping as source
     * 
     * @param source
     */
    public StringMap(Map<String, String> source) {
        super(source);
    }

    @Override
    public String valFromByte(byte[] value) {
        return new String(value, Charset.forName("UTF-8"));
    }

    @Override
    public byte[] valToByte(String value) {
        return value.getBytes(Charset.forName("UTF-8"));
    }
    
}
