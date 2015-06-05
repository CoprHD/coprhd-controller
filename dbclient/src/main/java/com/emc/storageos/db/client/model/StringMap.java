/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
