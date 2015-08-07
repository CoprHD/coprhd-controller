/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyInfoUtil {
    public static final Pattern PROP_PATTERN = Pattern.compile("(.+?)=(.*)");

    public static Map<String, String> splitKeyValue(String[] strings) {
        Map<String, String> map = new TreeMap<String, String>();

        if (strings != null) {
            for (int i = 0; i < strings.length; i++) {
                String key, value;
                Matcher m = PROP_PATTERN.matcher(strings[i]);
                if (m.matches()) {
                    key = m.group(1);
                    value = m.group(2);

                    map.put(key, value);
                }
            }
        }

        return map;
    }

}
