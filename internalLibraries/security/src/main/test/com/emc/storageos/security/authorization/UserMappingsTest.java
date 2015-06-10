/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authorization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class UserMappingsTest {

    @Test
    public void testUserMappings() {
        BasePermissionsHelper.UserMapping userMapping1 = new BasePermissionsHelper.UserMapping();
        userMapping1.setDomain("test.com");
        
        BasePermissionsHelper.UserMapping userMapping2 = new BasePermissionsHelper.UserMapping();
        userMapping2.setDomain("other");        
        Assert.assertFalse(userMapping1.isMatch(userMapping2));
        Assert.assertFalse(userMapping2.isMatch(userMapping1));
        
        userMapping2.setDomain("Test.Com");        
        Assert.assertTrue(userMapping1.isMatch(userMapping2));
        Assert.assertTrue(userMapping2.isMatch(userMapping1));
        
        List<String> groups1 = new ArrayList<String>();
        
        groups1.add("testGroup");        
        userMapping1.setGroups(groups1);
        Assert.assertTrue(userMapping1.isMatch(userMapping2));
        Assert.assertTrue(userMapping2.isMatch(userMapping1));
        
        BasePermissionsHelper.UserMappingAttribute mappingAttribute1 = new BasePermissionsHelper.UserMappingAttribute();
        mappingAttribute1.setKey("testkey");
        List<String> values = new ArrayList<String>();
        values.add("testvalue");
        mappingAttribute1.setValues(values);
        List<BasePermissionsHelper.UserMappingAttribute> mappingAttributes1 = new ArrayList<BasePermissionsHelper.UserMappingAttribute>();
        mappingAttributes1.add(mappingAttribute1);
        userMapping2.setAttributes(mappingAttributes1);
        
        Assert.assertFalse(userMapping1.isMatch(userMapping2));
        Assert.assertFalse(userMapping2.isMatch(userMapping1));
        List<String> groups2 = new ArrayList<String>();
        
        groups2.add("otherGroup");
        userMapping2.setGroups(groups2);
        Assert.assertFalse(userMapping1.isMatch(userMapping2));
        Assert.assertFalse(userMapping2.isMatch(userMapping1));
        
        groups2.clear();
        groups2.addAll(groups1);
        Assert.assertTrue(userMapping1.isMatch(userMapping2));
        Assert.assertTrue(userMapping2.isMatch(userMapping1));
        
        groups1.add("testGroup2");
        Assert.assertFalse(userMapping1.isMatch(userMapping2));
        Assert.assertFalse(userMapping2.isMatch(userMapping1));
        groups2.addAll(groups1);
        
        BasePermissionsHelper.UserMappingAttribute mappingAttribute2 = new BasePermissionsHelper.UserMappingAttribute();
        mappingAttribute2.setKey("TESTKEY");
        List<String> values2 = new ArrayList<String>();
        values2.add("testvalue2");
        mappingAttribute2.setValues(values2);
        
        List<BasePermissionsHelper.UserMappingAttribute> mappingAttributes2 = new ArrayList<BasePermissionsHelper.UserMappingAttribute>();
        mappingAttributes2.add(mappingAttribute2);
        userMapping1.setAttributes(mappingAttributes2);
        Assert.assertFalse(userMapping1.isMatch(userMapping2));
        Assert.assertFalse(userMapping2.isMatch(userMapping1));
        
        mappingAttribute2.getValues().addAll(mappingAttribute1.getValues());
        Assert.assertTrue(userMapping1.isMatch(userMapping2));
        Assert.assertTrue(userMapping2.isMatch(userMapping1));

        mappingAttribute1.getValues().add("testValue3");
        Assert.assertFalse(userMapping1.isMatch(userMapping2));
        Assert.assertFalse(userMapping2.isMatch(userMapping1));
        
        BasePermissionsHelper.UserMappingAttribute mappingAttribute3 = new BasePermissionsHelper.UserMappingAttribute();
        mappingAttribute3.setKey("testkey3");
        List<String> values3 = new ArrayList<String>();
        values3.add("testvalue4");
        mappingAttribute3.setValues(values3);
        
        mappingAttributes1.add(mappingAttribute3);
        Assert.assertFalse(userMapping1.isMatch(userMapping2));
        Assert.assertFalse(userMapping2.isMatch(userMapping1));
        
        mappingAttributes2.add(mappingAttribute3);
        Assert.assertFalse(userMapping1.isMatch(userMapping2));
        Assert.assertFalse(userMapping2.isMatch(userMapping1));
        
        mappingAttribute2.getValues().add("TESTVALUE3");
        Assert.assertTrue(userMapping1.isMatch(userMapping2));
        Assert.assertTrue(userMapping2.isMatch(userMapping1));
               
    }
}
