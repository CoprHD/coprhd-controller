package com.emc.storageos.db.client.model;

import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StringMapTest {
    private StringMap map;
    private Map<String, String> subMap;
    private static final String KEY1 = "key1";
    private static final String VALUE1 = "value1";
    private static final String KEY2 = "key2";
    private static final String VALUE2 = "value2";

    @Before
    public void setup() {
        map = new StringMap();
        subMap = new HashMap<String, String>() {{
           this.put(KEY1, VALUE1);
           this.put(KEY2, VALUE2);
        }};
    }

    @Test
    public void shouldStringMapTrackChangeOfPutAllInvokation() {
        Assert.assertTrue(map.isEmpty());
        Assert.assertTrue(map.getChangedKeySet()==null);
        map.putAll(subMap);
        Assert.assertTrue(map.getChangedKeySet()!=null);
        Assert.assertTrue(map.containsKey(KEY1));
        Assert.assertTrue(map.containsKey(KEY2));
        Assert.assertTrue(map.containsValue(VALUE1));
        Assert.assertTrue(map.containsValue(VALUE2));
    }
}
