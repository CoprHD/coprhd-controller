/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

public class StringSetTest {
	private static final String MAP_KEY = "dummyKey";
	private static final String SET_VALUE_ONE = "setValue1";
	private static final String SET_VALUE_TWO = "setValue2";
	
    @Test
    // Remove a StringSet entry from a StringSetMap
            public
            void removeStringSet() {

        StringSetMap map = new StringSetMap();

        StringSet set1 = new StringSet();
        set1.add("RAID0");
        set1.add("RAID1");

        map.put("raid_level", set1);

        map.remove("raid_level");

        Set<String> removed = map.getChangedKeySet();

        Assert.assertEquals(1, removed.size());
    }

    @Test
    // Test AbstractChangeTrackingSet.replace()
            public
            void replaceStringSet() {

        StringSetMap map = new StringSetMap();
        String newValue = "one one";

        map.put("1", "one");
        map.put("2", "two");
        map.put("3", "three");

        Set<String> set = map.get("1").getAddedSet();

        StringSet newSet = new StringSet();
        newSet.add(newValue);

        map.get("1").replace(newSet);

        set = map.get("1").getAddedSet();
        String[] values = set.toArray(new String[0]);
        Assert.assertEquals(newValue, values[0]);
    }

    @Test
    public void replaceStringSetMapWithNewEntry() {
        StringSet newValue = new StringSet();
        newValue.add("four");
        newValue.add("five");
        newValue.add("six");

        // The map should be:
        // ["1", ["one"]]
        // ["2", ["two"]]
        // ["3", ["three"]]

        StringSetMap map = new StringSetMap();
        map.put("1", "one");
        map.put("2", "two");
        map.put("3", "three");

        // The newMap is: ["4", ["four", "five", "six"]]
        StringSetMap newMap = new StringSetMap();

        newMap.put("4", newValue);

        map.replace(newMap);

        // Now the map should be:
        // ["1", []]
        // ["2", []]
        // ["3", []]
        // ["4", ["four", "five", "six"]]

        Assert.assertEquals(4, map.size());

        Assert.assertEquals(true, map.get("1").isEmpty());
        Assert.assertEquals(true, map.get("2").isEmpty());
        Assert.assertEquals(true, map.get("3").isEmpty());

        checkValue(newValue, map.get("4"));
    }

    @Test
    public void replaceExistingEntryInStringSetMap() {
        StringSet newValue = new StringSet();
        newValue.add("four");
        newValue.add("five");
        newValue.add("six");

        // The map should be:
        // ["1", ["one"]]
        // ["2", ["two"]]
        // ["3", ["three"]]

        StringSetMap map = new StringSetMap();
        map.put("1", "one");
        map.put("2", "two");
        map.put("3", "three");

        // The newMap is: ["2", ["four", "five", "six"]]
        StringSetMap newMap = new StringSetMap();

        newMap.put("2", newValue);

        map.replace(newMap);

        // Now the map should be:
        // ["1", []]
        // ["2", ["four", "five", "six"]]
        // ["3", []]
        Assert.assertEquals(3, map.size());

        Assert.assertEquals(true, map.get("1").isEmpty());
        Assert.assertEquals(true, map.get("3").isEmpty());

        checkValue(newValue, map.get("2"));
    }

    private void checkValue(StringSet validValues, Set<String> set) {
        String[] values = set.toArray(new String[0]);

        Assert.assertEquals(validValues.size(), values.length);
        Assert.assertTrue(set.containsAll(validValues));
    }

    @Test
    public void clearEmptyStringSetMap() {
        StringSetMap map = new StringSetMap();

        map.clear();

        Assert.assertEquals(0, map.size());
    }

    @Test
    public void clearStringSetMap() {
        // The map should be:
        // ["1", ["one"]]
        // ["2", ["two"]]
        // ["3", ["three"]]
        StringSetMap map = new StringSetMap();

        StringSet value1 = new StringSet();
        value1.add("a");
        value1.add("b");
        value1.add("c");

        map.put("1", value1);
        map.put("2", "two");
        map.put("3", "three");

        map.clear();

        Assert.assertEquals(3, map.size());

        Set<String> changedSet = map.getChangedKeySet();
        Assert.assertEquals(3, changedSet.size());
    }

    @Test
    public void replaceStringMap() {
        String newValue1 = "one one";
        String newValue2 = "two two";
        String newValue4 = "four four";

        // The map should be:
        // ["1", ["one"]]
        // ["2", ["two"]]
        // ["3", ["three"]]
        StringMap map = new StringMap();
        map.put("1", "one");
        map.put("2", "two");
        map.put("3", "three");

        Map<String, String> newMap = new HashMap<String, String>();

        // The newMap should be:
        // ["1", ["one one"]]
        // ["2", ["two two"]]
        // ["3", ["three three"]]
        newMap.put("1", newValue1);
        newMap.put("2", newValue2);
        newMap.put("4", newValue4);

        map.replace(newMap);

        Assert.assertEquals(3, map.size());

        Assert.assertEquals(newValue1, map.get("1"));
        Assert.assertEquals(newValue2, map.get("2"));
        Assert.assertEquals(newValue4, map.get("4"));
    }

    @Test
    public void clearStringMap() {
        // The map should be:
        // ["1", ["one"]]
        // ["2", ["two"]]
        // ["3", ["three"]]
        StringMap map = new StringMap();

        map.put("1", "one");
        map.put("2", "two");
        map.put("3", "three");

        map.clear();

        Assert.assertEquals(true, map.isEmpty());

        Set<String> removedKeySet = map.getRemovedKeySet();
        Assert.assertEquals(3, removedKeySet.size());
    }
    
    @Test
    public void shouldRemoveAllOfStringSetWork() {
    	StringSetMap setMap = new StringSetMap();
    	String key = MAP_KEY;
    	StringSet set = new StringSet();
    	set.add(SET_VALUE_ONE);
    	set.add(SET_VALUE_TWO);
    	setMap.put(key, set);
    	StringSet targetSet = setMap.get(key);
    	Assert.assertTrue(!targetSet.isEmpty());
    	targetSet.removeAll(set);
    	
    	Set<String> removedSet = setMap.get(key).getRemovedSet();
    	Assert.assertTrue(!removedSet.isEmpty());
    }
    
    @Test
    public void shouldClearOfStringSetWork() {
    	StringSetMap setMap = new StringSetMap();
    	String key = MAP_KEY;
    	StringSet set = new StringSet();
    	set.add(SET_VALUE_ONE);
    	set.add(SET_VALUE_TWO);
    	setMap.put(key, set);
    	StringSet targetSet = setMap.get(key);
    	Assert.assertTrue(!targetSet.isEmpty());
    	targetSet.clear();
    	
    	Set<String> removedSet = setMap.get(key).getRemovedSet();
    	Assert.assertTrue(!removedSet.isEmpty());
    }
}