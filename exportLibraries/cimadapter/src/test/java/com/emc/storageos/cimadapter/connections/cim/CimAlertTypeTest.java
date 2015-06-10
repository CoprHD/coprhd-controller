/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cimadapter.connections.cim;

import org.junit.Assert;
import org.junit.Test;

/**
 * JUnit test enum for {@link CimAlertType}.
 */
public class CimAlertTypeTest {

    /**
     * Tests the toString method when the passed value is 0.
     */
    @Test
    public void testToString() {
        Assert.assertEquals(CimAlertType.toString(0), "0");
    }

    /**
     * Tests the toString method when the passed value is out of range.
     */
    @Test
    public void testToString_OutOfRange() {
        int maxValue = CimAlertType.values().length - 1;
        Assert.assertEquals(CimAlertType.toString(maxValue + 1),
            String.valueOf(maxValue + 1));
    }

    /**
     * Tests the toString method when the passed value is the max value.
     */
    @Test
    public void testToString_MaxValue() {
        int maxValue = CimAlertType.values().length - 1;
        Assert.assertFalse(CimAlertType.toString(maxValue).equals(
            String.valueOf(maxValue)));
    }
}
