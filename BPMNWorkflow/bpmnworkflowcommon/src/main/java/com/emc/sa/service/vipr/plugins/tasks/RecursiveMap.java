package com.emc.sa.service.vipr.plugins.tasks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RecursiveMap {

    public static Object traverseMap(Map m, Object key) {
        return traverseMap(m, key, new HashSet());
    }

    public static Object traverseMap(Map m, Object key, Set traversed) {
        if (key == null) { // first key has to be null
            throw new NullPointerException();
        }
        traversed.add(key);
        Object value = m.get(key);
        if (traversed.contains(value)) { // added after Stephen C's comment on other answer
            // cycle found, either throw exception, return null, or return key
            return key;
        }
        return value != null ?
                traverseMap(m, value, traversed) :
                key; // I guess you want to return the last value that isn't also a key
    }

    public static void main(String[] args) {
        final HashMap<Integer, Integer> m = new HashMap<Integer, Integer>();
        m.put(0, 1);
        m.put(1, 2);
        m.put(3, 4);
        m.put(2, 3);
        final Object o = traverseMap(m, 2);
        System.out.println(o);
    }
}