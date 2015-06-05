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
 * JUnit test enum for {@link CimListenerInfo}.
 */
public class CimListenerInfoTest {
    
    private static final String LISTENER_IP = "10.247.66.22";
    private static final String LISTENER2_IP = "10.247.66.23";
    private static final String LISTENER_PROTOCOL = "http";
    private static final String LISTENER2_PROTOCOL = "http2";
    private static final int LISTENER_PORT = 7012;
    private static final int LISTENER2_PORT = 7013;
    private static final int LISTENER_QUEUE_SIZE = 1000;
    private static final int LISTENER2_QUEUE_SIZE = 2000;
    
    /**
     * Tests the CimListenerInfo class.
     */
    @Test
    public void testCimListenerInfo() {
        CimListenerInfo listenerInfo = new CimListenerInfo();
        
        // Test getters/setters.
        listenerInfo.setHostIP(LISTENER_IP);
        listenerInfo.setProtocol(LISTENER_PROTOCOL);
        listenerInfo.setPort(LISTENER_PORT);
        listenerInfo.setQueueSize(LISTENER_QUEUE_SIZE);
        Assert.assertEquals(listenerInfo.getHostIP(), LISTENER_IP);
        Assert.assertEquals(listenerInfo.getProtocol(), LISTENER_PROTOCOL);
        Assert.assertEquals(listenerInfo.getPort(), LISTENER_PORT);
        Assert.assertEquals(listenerInfo.getQueueSize(), LISTENER_QUEUE_SIZE);
        
        // Test equals method.
        Assert.assertTrue(listenerInfo.equals(listenerInfo));
        Assert.assertFalse(listenerInfo.equals(new Object()));
        CimListenerInfo listenerInfo2 = new CimListenerInfo();
        listenerInfo2.setHostIP(LISTENER2_IP);
        Assert.assertFalse(listenerInfo.equals(listenerInfo2));
        listenerInfo2.setHostIP(LISTENER_IP);
        listenerInfo2.setPort(LISTENER2_PORT);
        Assert.assertFalse(listenerInfo.equals(listenerInfo2));
        listenerInfo2.setPort(LISTENER_PORT);
        listenerInfo.setQueueSize(LISTENER2_QUEUE_SIZE);
        Assert.assertFalse(listenerInfo.equals(listenerInfo2));
        listenerInfo.setQueueSize(LISTENER_QUEUE_SIZE);
        listenerInfo.setProtocol(LISTENER2_PROTOCOL);
        Assert.assertFalse(listenerInfo.equals(listenerInfo2));
        listenerInfo.setProtocol(LISTENER_PROTOCOL);
        Assert.assertTrue(listenerInfo.equals(listenerInfo2));
        
        // Test hash
        int listenerInfoHash = listenerInfo.hashCode();
        int listener2InfoHash = listenerInfo2.hashCode();
        Assert.assertEquals(listenerInfoHash, listener2InfoHash);
        
    }
}
