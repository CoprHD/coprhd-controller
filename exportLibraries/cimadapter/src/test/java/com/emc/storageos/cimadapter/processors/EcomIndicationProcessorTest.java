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
package com.emc.storageos.cimadapter.processors;

import java.util.ArrayList;

import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimFilterMap;
import com.emc.storageos.cimadapter.connections.cim.CimListener;
import com.emc.storageos.cimadapter.connections.cim.CimListenerInfo;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.cimadapter.connections.ecom.EcomConnection;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumer;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumerList;
import com.emc.storageos.services.util.EnvConfig;

/**
 * JUnit test class for {@link EcomIndicationProcessor}.
 */
public class EcomIndicationProcessorTest {

    private static final String ALERT_INDICATION_CLASS_NAME = "OSLS_AlertIndication";
    private static final String ALERTING_MANGED_ELEMENT_KEY = "AlertingManagedElement";
    private static final String ALERTING_MANGED_ELEMENT_VALUE = "//169.254.165.97/root/emc:clar_storagesystem.CreationClassName=\"Clar_StorageSystem\",Name=\"CLARiiON+APM00120400480\"";
    
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    
    private static final String PROVIDER_IP = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.ipaddress");
    private static final String PROVIDER_PORT = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.port");
    private static final String PROVIDER_USER = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.username");
    private static final String PROVIDER_PW = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.password");
    private static final String providerUseSsl = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.usessl");
    private static final String PROVIDER_IMPL_NS = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.namespace");
    private static final String PROVIDER_INTEROP_NS = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.namespace.interop");
    private static boolean isProviderSslEnabled = Boolean.parseBoolean(providerUseSsl);
    
    
    private static final String LISTENER_IP = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.listener.ipaddress");
    private static final String LISTENER_PROTOCOL = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.listener.protocol");
    private static final String LISTENER_PORT_STR = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.listener.port");
    private static final int LISTENER_PORT = Integer.parseInt(LISTENER_PORT_STR);
   
    private static final int LISTENER_QUEUE_SIZE = 1000;

    /**
     * Tests the processIndication method.
     */
    @Test
    public void testProcessIndication() {
        CIMInstance indication = createAlertIndication();
        EcomConnection connection = createEcomConnection();
        EcomIndicationProcessor processor = new EcomIndicationProcessor(connection);
        CimIndicationSet indicationData = processor.processIndication(indication);
        Assert.assertTrue(indicationData.isAlertIndication());
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

    /**
     * Creates a connection required by the ECOM processor.
     * 
     * @return A connection required by the ECOM processor.
     */
    private EcomConnection createEcomConnection() {
        boolean wasException = false;
        EcomConnection connection = null;
        try {
            connection = new EcomConnection(createConnectionInfo(), createListener(),
                new CimFilterMap());
        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        return connection;
    }

    /**
     * Creates the connection info for the connection.
     * 
     * @return The connection info for the connection
     */
    private CimConnectionInfo createConnectionInfo() {

        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(Integer.parseInt(PROVIDER_PORT));
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PW);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(isProviderSslEnabled);
        return connectionInfo;
    }

    /**
     * Creates the listener for the connection.
     * 
     * @return The listener for the connection.
     */
    private CimListener createListener() {
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
}
