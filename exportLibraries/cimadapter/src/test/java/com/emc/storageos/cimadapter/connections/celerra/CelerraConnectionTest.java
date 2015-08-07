/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cimadapter.connections.celerra;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimFilterMap;
import com.emc.storageos.cimadapter.connections.cim.CimListener;
import com.emc.storageos.cimadapter.connections.cim.CimListenerInfo;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumer;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumerList;
import com.emc.storageos.cimadapter.processors.CelerraIndicationProcessor;
import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;

/**
 * JUnit test class for {@link CelerraConnection}.
 */
public class CelerraConnectionTest {

    private static final String PROVIDER_IP = "10.247.66.249";
    private static final String PROVIDER_USER = "nasadmin";
    private static final String PROVIDER_PW = "nasadmin";
    private static final String PROVIDER_IMPL_NS = "root/emc/celerra";
    private static final String PROVIDER_INTEROP_NS = "interop";
    private static final int PROVIDER_PORT = 5989;
    private static final String LISTENER_IP = "10.247.66.22";
    private static final String LISTENER_PROTOCOL = "http";
    private static final int LISTENER_PORT = 7012;
    private static final int LISTENER_QUEUE_SIZE = 1000;

    // Connection reference.
    private static volatile CelerraConnection _connection = null;
    
    // Message Specs list reference.
    private static volatile CelerraMessageSpecList _msgSpecList = null;

    /**
     * Creates a Celerra connection.
     * 
     * @return A Celerra connection.
     */
    @BeforeClass
    public static void createCelerraConnection() {
        boolean wasException = false;
        try {
            ArrayList<CelerraMessageSpec> msgSpecs = new ArrayList<CelerraMessageSpec>();
            _msgSpecList = new CelerraMessageSpecList(msgSpecs);
            _connection = new CelerraConnection(createConnectionInfo(), createListener(),
                new CimFilterMap(), _msgSpecList);
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Creates the connection info for the connection.
     * 
     * @return The connection info for the connection
     */
    private static CimConnectionInfo createConnectionInfo() {

        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.ECOM_FILE_CONNECTION_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PW);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(true);
        return connectionInfo;
    }

    /**
     * Creates the listener for the connection.
     * 
     * @return The listener for the connection.
     */
    private static CimListener createListener() {
        CimListenerInfo listenerInfo = new CimListenerInfo();
        listenerInfo.setHostIP(LISTENER_IP);
        listenerInfo.setPort(LISTENER_PORT);
        listenerInfo.setProtocol(LISTENER_PROTOCOL);
        listenerInfo.setQueueSize(LISTENER_QUEUE_SIZE);

        ArrayList<CimIndicationConsumer> consumers = new ArrayList<CimIndicationConsumer>();
        CimListener listener = new CimListener(listenerInfo,
            new CimIndicationConsumerList(consumers));
        return listener;
    }

    /**
     * Tests the getConnectionType method.
     */
    @Test
    public void testGetConnectionType() {
        Assert.assertNotNull(_connection);
        Assert.assertEquals(_connection.getConnectionType(),
            CimConstants.ECOM_FILE_CONNECTION_TYPE);
    }

    /**
     * Tests the getDefaultIndicationProcessor method.
     */
    @Test
    public void testGetDefaultIndicationProcessor() {
        Assert.assertNotNull(_connection);

        CimIndicationProcessor processor = _connection.getDefaultIndicationProcessor();
        Assert.assertNotNull(processor);
        Assert.assertTrue(processor instanceof CelerraIndicationProcessor);
        
        processor = _connection.getDefaultIndicationProcessor();
        Assert.assertNotNull(processor);
        Assert.assertTrue(processor instanceof CelerraIndicationProcessor);
    }
    
    /**
     * Tests the getMessageSpecs method.
     */
    @Test
    public void testGetMessageSpecs() {
        Assert.assertNotNull(_connection);

        Assert.assertNotNull(_msgSpecList);
        Assert.assertEquals(_connection.getMessageSpecs(), _msgSpecList);
    }
}
