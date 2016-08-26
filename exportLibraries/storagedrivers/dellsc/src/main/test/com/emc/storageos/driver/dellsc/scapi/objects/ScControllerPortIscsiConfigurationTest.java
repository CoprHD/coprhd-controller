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
package com.emc.storageos.driver.dellsc.scapi.objects;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the formatting behavior of the MAC address in the iSCSI config.
 * May need to move to util class at some point.
 */
public class ScControllerPortIscsiConfigurationTest {

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPortIscsiConfiguration#getFormattedMACAddress()}.
     */
    @Test
    public void testGetFormattedMACAddress() {
        ScControllerPortIscsiConfiguration config = new ScControllerPortIscsiConfiguration();
        config.macAddress = "00c0dd-1447a9";

        Assert.assertTrue("00:c0:dd:14:47:a9".equals(config.getFormattedMACAddress()));
    }

    /**
     * Test formatting MAC address with invalid input.
     */
    @Test
    public void testGetFormattedMACAddress_EmptyString() {
        ScControllerPortIscsiConfiguration config = new ScControllerPortIscsiConfiguration();
        config.macAddress = "";

        Assert.assertTrue("00:00:00:00:00:00".equals(config.getFormattedMACAddress()));
    }

    /**
     * Test formatting MAC address with invalid input.
     */
    @Test
    public void testGetFormattedMACAddress_NullString() {
        ScControllerPortIscsiConfiguration config = new ScControllerPortIscsiConfiguration();
        Assert.assertTrue("00:00:00:00:00:00".equals(config.getFormattedMACAddress()));
    }

    /**
     * Test formatting MAC address with invalid input.
     */
    @Test
    public void testGetFormattedMACAddress_MalformedString() {
        ScControllerPortIscsiConfiguration config = new ScControllerPortIscsiConfiguration();
        config.macAddress = "abc123";

        Assert.assertTrue("00:00:00:00:00:00".equals(config.getFormattedMACAddress()));
    }

    @Test
    public void testGetNetwork() {
        ScControllerPortIscsiConfiguration config = new ScControllerPortIscsiConfiguration();
        config.ipAddress = "172.23.57.147";
        config.subnetMask = "255.255.255.0";

        Assert.assertTrue("172.23.57.0".equals(config.getNetwork()));
    }

    @Test
    public void testGetNetworkNotOnBoundary() {
        ScControllerPortIscsiConfiguration config = new ScControllerPortIscsiConfiguration();
        config.ipAddress = "172.23.57.147";
        config.subnetMask = "255.255.248.0";

        Assert.assertTrue("172.23.56.0".equals(config.getNetwork()));
    }
}
