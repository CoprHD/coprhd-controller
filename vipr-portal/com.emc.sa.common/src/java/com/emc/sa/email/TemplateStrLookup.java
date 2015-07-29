/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.email;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrLookup;

public class TemplateStrLookup extends StrLookup {

    /** Map keys are variable names and value. */
    private final Map map;

    /**
     * Creates a new instance backed by a Map.
     * 
     * @param map the map of keys to values, may be null
     */
    TemplateStrLookup(Map map) {
        this.map = map;
    }

    /**
     * Looks up a String key to a String value using the map.
     * <p>
     * If the map is null, then null is returned. The map result object is converted to a string using toString().
     * 
     * @param key the key to be looked up, may be null
     * @return the matching value, null if no match
     */
    public String lookup(String key) {
        if (map == null) {
            return null;
        }
        Object obj = map.get(key);
        if (obj == null) {
            return StringUtils.EMPTY;
        }
        return obj.toString();
    }

}
