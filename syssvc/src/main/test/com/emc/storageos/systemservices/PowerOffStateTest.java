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
package com.emc.storageos.systemservices;

import com.emc.storageos.coordinator.client.model.PowerOffState;
import org.junit.Assert;
import org.junit.Test;

public class PowerOffStateTest {
    @Test
    public void testEncode() {
        {
            PowerOffState pos = new PowerOffState(PowerOffState.State.NONE);
            Assert.assertEquals("NONE", pos.encodeAsString());
        }

        {
            PowerOffState pos = new PowerOffState(PowerOffState.State.NOTICED);
            Assert.assertEquals("NOTICED", pos.encodeAsString());
        }
    }

    @Test
    public void testDecode() {
        {
            PowerOffState pos1 = new PowerOffState(PowerOffState.State.NONE);
            PowerOffState pos2 = pos1.decodeFromString(pos1.encodeAsString());

            Assert.assertEquals(pos1.getPowerOffState(), pos2.getPowerOffState());
        }

        {
            PowerOffState pos1 = new PowerOffState(PowerOffState.State.ACKNOWLEDGED);
            PowerOffState pos2 = pos1.decodeFromString(pos1.encodeAsString());

            Assert.assertEquals(pos1.getPowerOffState(), pos2.getPowerOffState());
        }
    }

    @Test
    public void testPowerOffStateEnum() {
        {
            Assert.assertTrue(PowerOffState.State.ACKNOWLEDGED.compareTo(PowerOffState.State.ACKNOWLEDGED) >= 0);
            Assert.assertTrue(PowerOffState.State.ACKNOWLEDGED.compareTo(PowerOffState.State.NONE) > 0);
            Assert.assertTrue(PowerOffState.State.NONE.compareTo(PowerOffState.State.NOTICED) < 0);
        }
    }
}
