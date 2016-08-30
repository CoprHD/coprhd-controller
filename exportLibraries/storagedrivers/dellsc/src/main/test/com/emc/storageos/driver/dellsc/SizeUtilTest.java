/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.driver.dellsc.scapi.SizeUtil;

/**
 * Tests for the SizeUtil class.
 */
public class SizeUtilTest {

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.scapi.SizeUtil#byteToGig(long)}.
     */
    @Test
    public void testByteToGig() {
        int result = SizeUtil.byteToGig(1073741824L);
        Assert.assertTrue(result == 1);
    }

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.scapi.SizeUtil#byteToGig(long)}.
     */
    @Test
    public void testByteToGigLarger() {
        int result = SizeUtil.byteToGig(107374182400L);
        Assert.assertTrue(result == 100);
    }

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.scapi.SizeUtil#byteToMeg(long)}.
     */
    @Test
    public void testByteToMeg() {
        int result = SizeUtil.byteToMeg(1073741824L);
        Assert.assertTrue(result == 1024);
    }

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.scapi.SizeUtil#byteToMeg(long)}.
     */
    @Test
    public void testByteToMegLarger() {
        int result = SizeUtil.byteToMeg(107374182400L);
        Assert.assertTrue(result == 102400);
    }

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.scapi.SizeUtil#sizeStrToBytes(java.lang.String)}.
     */
    @Test
    public void testSizeStrToBytes() {
        long result = SizeUtil.sizeStrToBytes("100 GB");
        Assert.assertTrue(result == 107374182400L);
    }

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.scapi.SizeUtil#sizeStrToBytes(java.lang.String)}.
     */
    @Test
    public void testSizeStrToBytesEngNotation() {
        long result = SizeUtil.sizeStrToBytes("1.073741824E9");
        Assert.assertTrue(result == 1073741824L);
    }

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.scapi.SizeUtil#sizeStrToKBytes(java.lang.String)}.
     */
    @Test
    public void testSizeStrToKBytes() {
        long result = SizeUtil.sizeStrToKBytes("100 GB");
        Assert.assertTrue(result == 104857600L);
    }

    @Test
    public void testSpeedStrToGigabits() {
        long result = SizeUtil.speedStrToGigabits("1 Gbps");
        Assert.assertTrue(result == 1);
    }

    @Test
    public void testSpeedStrToGigabitsUnknown() {
        long result = SizeUtil.speedStrToGigabits("Unknown");
        Assert.assertTrue(result == 0);
    }

    @Test
    public void testSpeedStrToGigabitsFromMegabits() {
        long result = SizeUtil.speedStrToGigabits("1024 Mbps");
        Assert.assertTrue(result == 1);
    }
}
