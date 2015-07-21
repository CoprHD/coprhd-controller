/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vdc;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;

public class VirtualDataCenterRestRepTest {

    @Test
    public void testNullSafe() {
        VirtualDataCenterRestRep vdc = new VirtualDataCenterRestRep();
        vdc.canDisconnect();
        vdc.canReconnect();
        vdc.setLocal(true);
        vdc.canDisconnect();
        vdc.canReconnect();        
    }
    
    @Test 
    public void testRejectLocal() {
        VirtualDataCenterRestRep vdc = new VirtualDataCenterRestRep();
        vdc.setLocal(true);
        vdc.setConnectionStatus("CONNECTED");
        Assert.assertFalse("disconnect should fail on a local vdc", vdc.canDisconnect());
        vdc.setConnectionStatus("DISCONNECTED");
        Assert.assertFalse("reconnect should fail on a local vdc", vdc.canReconnect());
    }
    
    @Test
    public void testCanDisconnect() {
        VirtualDataCenterRestRep vdc = new VirtualDataCenterRestRep();
        vdc.setLocal(false);
        vdc.setConnectionStatus("CONNECTED");
        Assert.assertTrue("disconnect should work on a CONNECTED non-local vdc", vdc.canDisconnect());
    }
    
    @Test 
    public void testCanReconnect() {
        VirtualDataCenterRestRep vdc = new VirtualDataCenterRestRep();
        vdc.setLocal(false);
        vdc.setConnectionStatus("DISCONNECTED");
        Assert.assertTrue("reconnect should work on a DISCONNECTED non-local vdc", vdc.canReconnect());  
    }    
    
    @Test
    public void testShouldAlarm() {
        VirtualDataCenterRestRep vdc = new VirtualDataCenterRestRep();
        Assert.assertFalse("should not alarm if lastSeenTimeInMillis is null", vdc.shouldAlarm());
        vdc.setLastSeenTimeInMillis(System.currentTimeMillis());
        Assert.assertFalse("should not alarm if lastSeenTimeInMillis is now", vdc.shouldAlarm());
        vdc.setLastSeenTimeInMillis(System.currentTimeMillis() - 24 * 3600 * 1000);
        Assert.assertTrue("should alarm if lastSeenTimeInMillis is 1 days ago", vdc.shouldAlarm());
    }
}
