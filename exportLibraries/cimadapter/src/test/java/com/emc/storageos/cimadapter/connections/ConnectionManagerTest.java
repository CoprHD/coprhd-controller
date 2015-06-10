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
package com.emc.storageos.cimadapter.connections;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.services.util.EnvConfig;

/**
 * JUnit test class for {@link ConnectionManager}.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/AdapterSpringContext-server.xml"})
public class ConnectionManagerTest {

    // Constants defining provider connection info.
    private static final String INVALID_CONN_TYPE = "InvalidConnectionType";
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String PROVIDER_IP = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.ipaddress");
    private static final String PROVIDER_PORT_STR = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.port");
    private static final int PROVIDER_PORT = Integer.parseInt(PROVIDER_PORT_STR);
    private static final String PROVIDER_USER = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.username");
    private static final String PROVIDER_PWD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.password");
    private static final String providerUseSsl = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.usessl");
    private static final String BLOCK_PROVIDER_IMPL_NS = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.namespace");
    private static final String PROVIDER_INTEROP_NS = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.interop.namespace");
    
    @SuppressWarnings("unused")
    private static final String FILE_PROVIDER_IMPL_NS = "root/emc/celerra";

    private static ConnectionManager _connectionManager = null;

     /**
     * Loads the log service properties before executing any tests.
     */
    @BeforeClass
    public static void getConnectionManager() {
        try {
            ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(
                "AdapterSpringContext-server.xml");
            _connectionManager = (ConnectionManager) ctx.getBean("ConnectionManager");
        } catch (Exception e) {
        }
    }

    /**
     * Tests the addConnection method when null connection info is passed.
     */
    @Test
    public void testAddConnection_NullConnectionInfo() {
        Assert.assertNotNull(_connectionManager);
        boolean wasException = false;
        try {
            _connectionManager.addConnection(null);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertTrue(wasException);
    }

    /**
     * Tests the addConnection method for a generic CIM connection type.
     */
    @Test
    public void testAddConnection_CIM() {
        Assert.assertNotNull(_connectionManager);

        // Create the connection info.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.CIM_CONNECTION_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PWD);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(BLOCK_PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));

        // Add the connection.
        boolean wasException = false;
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is connected.
        try {
            Assert.assertTrue(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Clean up by removing the connection.
        try {
            _connectionManager.removeConnection(PROVIDER_IP);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is no longer connected.
        try {
            Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the addConnection method for an ECOM connection type.
     */
    @Test
    public void testAddConnection_ECOM() {
        Assert.assertNotNull(_connectionManager);

        // Create the connection info.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PWD);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(BLOCK_PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));

        // Add the connection.
        boolean wasException = false;
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is connected.
        try {
            Assert.assertTrue(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Clean up by removing the connection.
        try {
            _connectionManager.removeConnection(PROVIDER_IP);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is no longer connected.
        try {
            Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the addConnection method for an ECOM File connection type.
     */
    /*
     * @Test public void testAddConnection_ECOM_FILE() {
     * Assert.assertNotNull(_connectionManager);
     * 
     * // Create the connection info. CimConnectionInfo connectionInfo = new
     * CimConnectionInfo();
     * connectionInfo.setType(CimConstants.ECOM_FILE_CONNECTION_TYPE);
     * connectionInfo.setHost(PROVIDER_IP);
     * connectionInfo.setPort(PROVIDER_PORT);
     * connectionInfo.setUser(FILE_PROVIDER_USER);
     * connectionInfo.setPassword(FILE_PROVIDER_PW);
     * connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
     * connectionInfo.setImplNS(FILE_PROVIDER_IMPL_NS);
     * connectionInfo.setUseSSL(true);
     * 
     * // Add the connection. boolean wasException = false; try {
     * _connectionManager.addConnection(connectionInfo); } catch
     * (ConnectionManagerException e) { wasException = true; }
     * Assert.assertFalse(wasException);
     * 
     * // Assert the provider is connected. try {
     * Assert.assertTrue(_connectionManager.isConnected(PROVIDER_IP));
     * 
     * } catch (ConnectionManagerException e) { wasException = true; }
     * Assert.assertFalse(wasException);
     * 
     * // Clean up by removing the connection. try {
     * _connectionManager.removeConnection(PROVIDER_IP); } catch
     * (ConnectionManagerException e) { wasException = true; }
     * Assert.assertFalse(wasException);
     * 
     * // Assert the provider is no longer connected. try {
     * Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));
     * 
     * } catch (ConnectionManagerException e) { wasException = true; }
     * Assert.assertFalse(wasException); }
     */

    /**
     * Tests the addConnection method when the provider is already connected.
     */
    @Test
    public void testAddConnection_AlreadyConnected() {
        Assert.assertNotNull(_connectionManager);

        // Create the connection info.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PWD);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(BLOCK_PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));

        // Add the connection.
        boolean wasException = false;
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is connected.
        try {
            Assert.assertTrue(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Try adding the connection again. There should not be an
        // exception, the current connection is used.
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is still connected.
        try {
            Assert.assertTrue(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Clean up by removing the connection.
        try {
            _connectionManager.removeConnection(PROVIDER_IP);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is no longer connected.
        try {
            Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the addConnection method when the connection type is not valid.
     */
    @Test
    public void testAddConnection_InvalidConnectionType() {
        Assert.assertNotNull(_connectionManager);

        // Create the connection info.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(INVALID_CONN_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PWD);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(BLOCK_PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));

        // Add the connection.
        boolean wasException = false;
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertTrue(wasException);

        // Assert the provider is not connected.
        wasException = false;
        try {
            Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the addConnection method when an exception occurs trying to create
     * the CIM client connection.
     */
    @Test
    public void testAddConnection_Exception() {
        Assert.assertNotNull(_connectionManager);

        // Create the connection info with an invalid provider host, which will
        // cause an exception to occur when creating the CIM client connection.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
        connectionInfo.setHost(null);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PWD);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(BLOCK_PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));

        // Add the connection.
        boolean wasException = false;
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertTrue(wasException);

        // Assert the provider is not connected.
        wasException = false;
        try {
            Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the removeConnection method when passed host is null.
     */
    @Test
    public void testRemoveConnection_NullHost() {
        Assert.assertNotNull(_connectionManager);

        // Remove the connection passing a null host.
        boolean wasException = false;
        try {
            _connectionManager.removeConnection(null);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertTrue(wasException);
    }

    /**
     * Tests the removeConnection method when passed host is blank.
     */
    @Test
    public void testRemoveConnection_BlankHost() {
        Assert.assertNotNull(_connectionManager);

        // Remove the connection passing a null host.
        boolean wasException = false;
        try {
            _connectionManager.removeConnection("");
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertTrue(wasException);
    }

    /**
     * Tests the removeConnection method when passed host is not connected.
     */
    @Test
    public void testRemoveConnection_NotConnected() {
        Assert.assertNotNull(_connectionManager);

        // Remove the connection passing a null host.
        boolean wasException = false;
        try {
            _connectionManager.removeConnection(PROVIDER_IP);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertTrue(wasException);
    }

    /**
     * Tests the removeConnection method when passed host is connected.
     */
    @Test
    public void testRemoveConnection_Connected() {
        Assert.assertNotNull(_connectionManager);

        // Create the connection info.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PWD);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(BLOCK_PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));

        // Add the connection.
        boolean wasException = false;
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is connected.
        try {
            Assert.assertTrue(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Remove the connection.
        try {
            _connectionManager.removeConnection(PROVIDER_IP);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is no longer connected.
        try {
            Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the isConnected method when passed host is null.
     */
    @Test
    public void testIsConnected_NullHost() {
        Assert.assertNotNull(_connectionManager);

        // Remove the connection passing a null host.
        boolean wasException = false;
        try {
            _connectionManager.isConnected(null);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertTrue(wasException);
    }

    /**
     * Tests the isConnected method when passed host is blank.
     */
    @Test
    public void testIsConnected_BlankHost() {
        Assert.assertNotNull(_connectionManager);

        // Remove the connection passing a null host.
        boolean wasException = false;
        try {
            _connectionManager.isConnected("");
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertTrue(wasException);
    }

    /**
     * Tests the isConnected method when passed host is not connected.
     */
    @Test
    public void testIsConnected_NotConnected() {
        Assert.assertNotNull(_connectionManager);

        // Remove the connection passing a null host.
        boolean wasException = false;
        try {
            Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the isConnected method when passed host is not connected.
     */
    @Test
    public void testIsConnected_Connected() {
        Assert.assertNotNull(_connectionManager);

        // Create the connection info.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PWD);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(BLOCK_PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));

        // Add the connection.
        boolean wasException = false;
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is connected.
        try {
            Assert.assertTrue(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Remove the connection.
        try {
            _connectionManager.removeConnection(PROVIDER_IP);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the shutdown method.
     */
    @Test
    public void testShutdown() {
        Assert.assertNotNull(_connectionManager);

        // Create the connection info.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
        connectionInfo.setHost(PROVIDER_IP);
        connectionInfo.setPort(PROVIDER_PORT);
        connectionInfo.setUser(PROVIDER_USER);
        connectionInfo.setPassword(PROVIDER_PWD);
        connectionInfo.setInteropNS(PROVIDER_INTEROP_NS);
        connectionInfo.setImplNS(BLOCK_PROVIDER_IMPL_NS);
        connectionInfo.setUseSSL(Boolean.parseBoolean(providerUseSsl));

        // Add the connection.
        boolean wasException = false;
        try {
            _connectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is connected.
        try {
            Assert.assertTrue(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Shutdown connection manager.
        try {
            _connectionManager.shutdown();
        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);

        // Assert the provider is not connected.
        try {
            Assert.assertFalse(_connectionManager.isConnected(PROVIDER_IP));

        } catch (ConnectionManagerException e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }
}