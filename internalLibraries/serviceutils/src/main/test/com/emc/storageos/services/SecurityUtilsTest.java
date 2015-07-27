/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.services;

import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.SecurityUtils;

public class SecurityUtilsTest {

    @BeforeClass
    public static void populateServices() throws Exception {
    }

	/**
	 * Pass a map of asset types and oid's to the stripMapXSS method
	 * Verify contents of the map are unchanged
	 */
    @Test
    public void testSanitizedMap(){
        String string1 = "urn:storageos:VirtualPool:4736ce97-87f1-4c2e-a28e-8f2a9efebbd4:vdc1";
        String string2 = "urn:storageos:Project:c07d53cc-dae4-4902-8802-648d1c9e45fc:global";
        String string3 = "urn:storageos:VirtualArray:d47a0562-2b4d-4155-91cd-f8f485a28425:vdc1";
        Map<String, String> mapToSanitize = new HashMap<String, String>();
        mapToSanitize.put("vipr.blockVirtualPool", string1);
        mapToSanitize.put("vipr.project", string2);
        mapToSanitize.put("vipr.virtualArray", string3);
        Map<String, String> sanitizedMap = SecurityUtils.stripMapXSS(mapToSanitize);

        System.out.println("Sanitized Map keys " + StringUtils.join(sanitizedMap.keySet(), ", "));
        System.out.println("Sanitized Map values " + StringUtils.join(sanitizedMap.values(), ", "));

        Assert.assertNotNull(sanitizedMap);
        int cntr = 0;
        for(String key : sanitizedMap.keySet()){
            switch (cntr++){
                case 0: Assert.assertEquals("Sanitized map key should not have changed", key, "vipr.blockVirtualPool");
                        break;
                case 1: Assert.assertEquals("Sanitized map key should not have changed", key, "vipr.project");
                        break;
                case 2: Assert.assertEquals("Sanitized map key should not have changed", key, "vipr.virtualArray");
                        break;
                default: Assert.fail();
            }
        }
        cntr = 0;
        for(String value : sanitizedMap.values()){
            switch (cntr++){
            	case 0: Assert.assertEquals("Sanitized map value should not have changed", value, string1);
            		break;
            	case 1: Assert.assertEquals("Sanitized map value should not have changed", value, string2);
            		break;
            	case 2: Assert.assertEquals("Sanitized map value should not have changed", value, string3);
                    break;
                default: Assert.fail();
            }
        }
    }
	/**
	 * Pass a map of asset types and oid's that contain <script> to the stripMapXSS method
	 * Verify contents of the map are unchanged besides removing <script> or 
	 * discarding the entire string when <script> is at the beginning
	 */
    @Test
    public void testScriptStrip(){
        String string1 = "urn:storageos:VirtualPool:4736ce97-87f1-4c2e-a28e-8f2a9efebbd4:vdc1";
        String string2 = "urn:storageos:Project:c07d53cc-dae4-4902-8802-648d1c9e45fc:global";
        String string3 = "urn:storageos:VirtualArray:d47a0562-2b4d-4155-91cd-f8f485a28425:vdc1";
        Map<String, String> mapToSanitize = new HashMap<String, String>();
        mapToSanitize.put("vipr.blockVirtualPool", string1.concat("<script>"));
        mapToSanitize.put("vipr.project", string2.concat("</script>"));
        mapToSanitize.put("vipr.virtualArray", "<script>" + string3);
        Map<String, String> sanitizedMap = SecurityUtils.stripMapXSS(mapToSanitize);

        System.out.println("Sanitized Map keys " + StringUtils.join(sanitizedMap.keySet(), ", "));
        System.out.println("Sanitized Map values " + StringUtils.join(sanitizedMap.values(), ", "));

        Assert.assertNotNull(sanitizedMap);
        Assert.assertTrue("Entire String should be discarded because <script> is at beginning", !sanitizedMap.containsValue("<script>" + string3));
        Assert.assertTrue("Entire String should be discarded because <script> is at beginning", !sanitizedMap.containsValue(string3));
        int cntr = 0;
        for(String key : sanitizedMap.keySet()){
            switch (cntr++){
                case 0: Assert.assertEquals("Sanitized map key should not have changed", key, "vipr.blockVirtualPool");
                        break;
                case 1: Assert.assertEquals("Sanitized map key should not have changed", key, "vipr.project");
                        break;
                case 2: Assert.assertEquals("Sanitized map key should not have changed", key, "vipr.virtualArray");
                        break;
                default: Assert.fail();
            }
        }
        cntr = 0;
        for(String value : sanitizedMap.values()){
            switch (cntr++){
                case 0: Assert.assertEquals("Sanitized map value should not contain the string <script>", value, string1);
                        break;
                case 1: Assert.assertEquals("Sanitized map value should not contain the string <script>", value, string2);
                        break;
                case 2: Assert.assertNotEquals("Entire String should be discarded because <script> is at beginning", value, "<script>" + string3);
                        break;
                default: Assert.fail();
            }
        }
    }

	/**
	 * Pass oid type strings to the stripXSS method
	 * Verify the strings are unchanged
	 */
    @Test
    public void testStripXSS(){
        String string1 = "urn:storageos:VirtualPool:4736ce97-87f1-4c2e-a28e-8f2a9efebbd4:vdc1";
        String string2 = "urn:storageos:Project:c07d53cc-dae4-4902-8802-648d1c9e45fc:global";
        String xssString1 = SecurityUtils.stripXSS(string1);
        String xssString2 = SecurityUtils.stripXSS(string2.concat("<script>"));

        System.out.println("Sanitized String 1 " + xssString1);
        System.out.println("Sanitized String 2 " + xssString2);

        Assert.assertNotNull(xssString1);
        Assert.assertNotNull(xssString2);
        Assert.assertEquals("Sanitized string should not have changed", xssString1, string1);
        Assert.assertEquals("Sanitized string should not have changed", xssString2, string2);
    }

}

