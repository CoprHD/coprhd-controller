/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices;

import com.emc.storageos.coordinator.exceptions.DecodingException;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;


public class PropertyInfoTest {
    @Test
    public void testDiff() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("node_id", "node2");
        map.put("system_connectemc_username", "username");
        PropertyInfoExt propertyInfo = new PropertyInfoExt(map);

        Map<String, String> newMap = new HashMap<String, String>();
        newMap.put("node_id", "node4");
        PropertyInfoExt propertyInfoNew = new PropertyInfoExt(newMap);
        Assert.assertTrue(propertyInfo.diff(propertyInfoNew));

        // non-existing property
        propertyInfoNew.getAllProperties().put("dummy", "dummy");
        Assert.assertTrue(propertyInfo.diff(propertyInfoNew));

        // existing property
        propertyInfoNew.getAllProperties().put("system_connectemc_username", "newname");
        Assert.assertTrue(propertyInfo.diff(propertyInfoNew));

        // empty value property
        propertyInfoNew.getAllProperties().put("system_connectemc_username", "");
        Assert.assertTrue(propertyInfo.diff(propertyInfoNew));

        PropertyInfoExt prop1 = new PropertyInfoExt();
        PropertyInfoExt prop2 = new PropertyInfoExt();
        Assert.assertTrue(prop1.getDiffProperties(prop2).size() == 0);

        prop2.addProperty("new1", "new1");
        Assert.assertTrue(prop1.getDiffProperties(prop2).size() == 1);
        Assert.assertTrue(prop2.getDiffProperties(prop1).get("new1").equals("new1"));
    }

    @Test
    public void testEncodeDecode() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("node_id", "node2");
        map.put("system_connectemc_username", "username");
        map.put("version", "");
        map.put("twoEquals", "abc==abc==abc");
        PropertyInfoExt propertyInfo = new PropertyInfoExt(map);

        String encodeStr2 = propertyInfo.encodeAsString();
        Assert.assertTrue(encodeStr2.indexOf("node_id") != -1);
        Assert.assertTrue(encodeStr2.indexOf("system_connectemc_username") != -1);
        Assert.assertTrue(encodeStr2.indexOf("version") != -1);
        Assert.assertTrue(encodeStr2.indexOf("twoEquals") != -1);

        try {
            PropertyInfoExt props = PropertyInfoExt.class.newInstance().decodeFromString(encodeStr2);
            Assert.assertTrue(props.getAllProperties().get("system_connectemc_username").equals("username"));
            Assert.assertTrue(props.getAllProperties().get("version").equals(""));
            Assert.assertTrue(props.getAllProperties().get("twoEquals").equals("abc==abc==abc"));
        } catch (DecodingException e) {
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(false);
        }

        PropertyInfoExt propertyInfo1 = new PropertyInfoExt(new String[]{"", "node_id=node2", "system_connectemc_username=username", "multiEquals=a=b=c"});
        String encodeStr1 = propertyInfo1.encodeAsString();
        char[] chars = encodeStr1.toCharArray();
        int len = chars.length;
        int count = 0;
        for (int i = 0; i < len; i++) {
            if (chars[i] == '=')
                count++;
        }
        Assert.assertTrue(count == 5);

    }

    @Test
    public void testGetTargetProps() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("node_id", "node2");
        map.put("system_connectemc_username", "username");
        map.put("version", "");
        PropertyInfoExt propertyInfo = new PropertyInfoExt(map);

        Map<String, String> targetProps = propertyInfo.getAllProperties();
        Assert.assertEquals("username", targetProps.get("system_connectemc_username"));
        Assert.assertEquals("node2", targetProps.get("node_id"));
        Assert.assertEquals("", targetProps.get("version"));
        Assert.assertNull(targetProps.get("nonExistant"));
    }

}
