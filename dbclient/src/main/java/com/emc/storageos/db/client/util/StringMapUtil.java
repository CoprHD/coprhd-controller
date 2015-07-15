/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.StringMap;

/**
 * A utility class for simplifying the use of {@link StringMap}.
 *
 */
public class StringMapUtil {
    
    /**
     * Creates and returns a {@link Map} of volume-to-lun from a {@link StringMap}
     * 
     * @param strMap the {@link StringMap} containing the values
     * @return a {@link Map} of volume-to-lun
     */
    public static Map<URI, Integer> stringMapToVolumeMap(StringMap strMap) {
        Map<URI, Integer> map = new HashMap<URI, Integer>();
        if (strMap != null && !strMap.isEmpty()) {
            for (String strUri : strMap.keySet()) {
                map.put(URI.create(strUri), Integer.valueOf(strMap.get(strUri)));
            }
        }
        return map;
    }
    
    /**
     * Creates a return a {@link StringMap} of volume/lun from a {@link Map}
     * @param volMap the {@link Map} containing the values
     * @return a {@link StringMap} of volume/lun 
     */
    public static Map<String, String> volumeMapToStringMap (Map<URI, Integer> volMap) {
        Map<String, String> map = new HashMap<String, String>();
        if (volMap != null && !volMap.isEmpty()) {
            for (URI uri : volMap.keySet()) {
                map.put(uri.toString(), String.valueOf(volMap.get(uri)));
            }
        }
        return map;
    }
    
    /**
     * Populated a (key,value) pair in a map of lists. It finds the list at key and 
     * adds value to the list. If there is no list at key, create a new list and add
     * it to the map.
     *  
     * @param map the map of lists
     * @param key the list key in the map
     * @param value the value to be added to the list at key
     * @return a {@link StringMap} of volume/lun 
     */
    public static <K extends Object, V extends Object> boolean addToListMap (Map<K, List<V>> map, K key, V value) {
        if (map == null) return false;
        List<V> list = map.get(key);
        if (list == null) {
            list = new ArrayList<V>();
            map.put(key, list);
        }
        if (!list.contains(value)) {
            list.add(value);
            return true;
        }
        return false;
    }
    
}
