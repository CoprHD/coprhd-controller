/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cimadapter.connections.ecom;

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
import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;
import com.emc.storageos.cimadapter.processors.EcomIndicationProcessor;
import com.emc.storageos.services.util.EnvConfig;

/**
 * JUnit test class for {@link EcomConnection}.
 */
public class EcomConnectionTest {

    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String providerIP = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.ipaddress");
    private static final String providerPortStr = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.port");
    private static final int providerPort = Integer.parseInt(providerPortStr);
    private static final String providerUser = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.username");
    private static final String providerPassword = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.password");
    private static final String providerUseSsl = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.usessl");
    private static final String providerNamespace = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.namespace");
    private static final String providerInterOpNamespace = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.interop.namespace");
    private static boolean isProviderSslEnabled = Boolean.parseBoolean(providerUseSsl);
    
    private static final String LISTENER_IP = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.listener.ipaddress");
    private static final String LISTENER_PROTOCOL = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.listener.protocol");
    private static final String LISTENER_PORT_STR = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.listener.port");
    private static final int LISTENER_PORT = Integer.parseInt(LISTENER_PORT_STR);
    private static final int LISTENER_QUEUE_SIZE = 1000;

    // Connection reference.
    private static volatile EcomConnection _connection = null;

    /**
     * Creates a connection required by the ECOM processor.
     * 
     * @return A connection required by the ECOM processor.
     */
    @BeforeClass
    public static void createEcomConnection() {
        boolean wasException = false;
        try {
            _connection = new EcomConnection(createConnectionInfo(), createListener(),
                new CimFilterMap());
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
        connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
        connectionInfo.setHost(providerIP);
        connectionInfo.setPort(providerPort);
        connectionInfo.setUser(providerUser);
        connectionInfo.setPassword(providerPassword);
        connectionInfo.setInteropNS(providerInterOpNamespace);
        connectionInfo.setImplNS(providerNamespace);
        connectionInfo.setUseSSL(isProviderSslEnabled);
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
            CimConstants.ECOM_CONNECTION_TYPE);
    }

    /**
     * Tests the getDefaultIndicationProcessor method.
     */
    @Test
    public void testGetDefaultIndicationProcessor() {
        Assert.assertNotNull(_connection);

        CimIndicationProcessor processor = _connection.getDefaultIndicationProcessor();
        Assert.assertNotNull(processor);
        Assert.assertTrue(processor instanceof EcomIndicationProcessor);
    }
}
