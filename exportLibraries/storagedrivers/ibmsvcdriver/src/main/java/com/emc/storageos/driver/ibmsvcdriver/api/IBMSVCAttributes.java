package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.Hashtable;

import com.google.common.base.Joiner;

/**
 * Basic class for holding attributes parsed from IBM-SVC CLI command output
 */
public class IBMSVCAttributes {
    private static final String EMPTY_STRING = "";

    private Hashtable<String, String> tableOfAttributes = new Hashtable<String, String>();

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
