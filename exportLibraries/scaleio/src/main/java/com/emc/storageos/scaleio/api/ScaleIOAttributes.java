/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import com.google.common.base.Joiner;

import java.util.Hashtable;

/**
 * Basic class for holding attributes parsed from ScaleIO CLI command output
 */
public class ScaleIOAttributes {
    private static final String EMPTY_STRING = "";

    private Hashtable<String, String> tableOfAttributes = new Hashtable<>();

    public void put(String name, String value) {
        tableOfAttributes.put(name, value);
    }

    public String get(String name) {
        String value = tableOfAttributes.get(name);
        if (value == null) {
            value = EMPTY_STRING;
        }
        return value;
    }

    @Override
    public String toString() {
        return Joiner.on(',').join(tableOfAttributes.entrySet());
    }
}
