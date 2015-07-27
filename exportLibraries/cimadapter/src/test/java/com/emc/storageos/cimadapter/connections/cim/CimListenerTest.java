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

import java.util.ArrayList;

import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.cimadapter.connections.ecom.EcomConnection;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumer;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumerList;
import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;
import com.emc.storageos.services.util.EnvConfig;

/**
 * JUnit test enum for {@link CimListener}.
 */
public class CimListenerTest {

    private static final String ALERT_INDICATION_CLASS_NAME = "OSLS_AlertIndication";
    private static final String ALERTING_MANGED_ELEMENT_KEY = "AlertingManagedElement";
    private static final String ALERTING_MANGED_ELEMENT_VALUE = "//169.254.165.97/root/emc:clar_storagesystem.CreationClassName=\"Clar_StorageSystem\",Name=\"CLARiiON+APM00120400480\"";
    private static final int LISTENER_QUEUE_SIZE = 1000;
    
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

    // Connection reference
    private EcomConnection _connection = null;

    /**
     * Tests the testIndicationOccurred method.
     */
    @Test
    public void testIndicationOccurred() {
        // Create the listener info.
        CimListenerInfo listenerInfo = new CimListenerInfo();
        listenerInfo.setHostIP(LISTENER_IP);
        listenerInfo.setPort(LISTENER_PORT);
        listenerInfo.setProtocol(LISTENER_PROTOCOL);
        listenerInfo.setQueueSize(LISTENER_QUEUE_SIZE);

        // Create the consumers list.
        ArrayList<CimIndicationConsumer> consumers = new ArrayList<CimIndicationConsumer>();
        TestIndicationConsumer consumer = new TestIndicationConsumer();
        consumer.setUseDefaultProcessor(true);
        TestIndicationProcessor processor = new TestIndicationProcessor();
        consumer.setIndicationProcessor(processor);
        consumers.add(consumer);

        // Create the listener.
        CimListener listener = new CimListener(listenerInfo,
            new CimIndicationConsumerList(consumers));

        // Create and register a connection with the listener.
        boolean wasException = false;
        try {
            _connection = new EcomConnection(createConnectionInfo(), listener,
                new CimFilterMap());
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
        listener.register(_connection);

        // Create an indication to be consumed.
        CIMInstance instance = createAlertIndication();

        // Inform the listener that an indication occurred.
        String url = "http://foo/" + _connection.getConnectionName();
        listener.indicationOccured(url, instance);

        // Assert that when the indication occurs the indication is processed
        // and consumed.
        Assert.assertTrue(processor._indicationProcessed);
        Assert.assertTrue(consumer._indicationConsumed);
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
     * Creates a CIMInstance representing an alert indication.
     * 
     * @return A CIMInstance representing an alert indication.
     */
    @SuppressWarnings("rawtypes")
    private CIMInstance createAlertIndication() {
        CIMObjectPath objPath = CimObjectPathCreator.createInstance(ALERT_INDICATION_CLASS_NAME);
        CIMProperty[] properties = new CIMProperty[] { new CIMProperty<String>(
            ALERTING_MANGED_ELEMENT_KEY, CIMDataType.STRING_T,
            ALERTING_MANGED_ELEMENT_VALUE) };
        CIMInstance indication = new CIMInstance(objPath, properties);
        return indication;
    }

    private class TestIndicationConsumer extends CimIndicationConsumer {

        private boolean _indicationConsumed = false;

        public void consumeIndication(Object indication) {
            _indicationConsumed = true;
        }
    }

    private class TestIndicationProcessor extends CimIndicationProcessor {

        private boolean _indicationProcessed = false;

        @Override
        public Object process(Object indication) {
            _indicationProcessed = true;
            return indication;
        }
    }
}
