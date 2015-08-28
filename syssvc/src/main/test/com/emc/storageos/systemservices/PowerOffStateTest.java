/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
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
