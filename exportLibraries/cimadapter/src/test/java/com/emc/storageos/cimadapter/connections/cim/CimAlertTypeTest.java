/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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
    public void testToStringOutOfRange() {
        int maxValue = CimAlertType.values().length - 1;
        Assert.assertEquals(CimAlertType.toString(maxValue + 1),
            String.valueOf(maxValue + 1));
    }

    /**
     * Tests the toString method when the passed value is the max value.
     */
    @Test
    public void testToStringMaxValue() {
        int maxValue = CimAlertType.values().length - 1;
        Assert.assertFalse(CimAlertType.toString(maxValue).equals(
            String.valueOf(maxValue)));
    }
}
