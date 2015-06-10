/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
// UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
// notice above does not evidence any actual  or  intended publication of this
// software. Disclosure and dissemination are pursuant to separate agreements.
// Unauthorized use, distribution or dissemination are strictly prohibited.

package com.emc.storageos.cimadapter.app;

// Java imports
import java.util.Timer;
import java.util.TimerTask;



// Spring imports
import org.springframework.context.support.ClassPathXmlApplicationContext;

//Logger imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



// StorageOS imports
import com.emc.storageos.cimadapter.connections.ConnectionManager;
import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.services.util.EnvConfig;

/**
 * Simple test application.
 */
public class CimAdapterTest {

    // The name of our Spring application context file.
    private static final String SPRING_CONFIG_FILE = "AdapterSpringContext-server.xml";

    // The id for the connection manager bean in the spring configuration file.
    private static final String CONNECTION_MANAGER_BEAN = "ConnectionManager";

    // A reference to the connection manager.
    private static ConnectionManager _connectionManager;

    // The time delay after the connection manager is started when it will be
    // shut down. Default is 2 minutes.
    private static long _shutdownDelay = 120000;
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

    // The logger.
    private static final Logger s_logger = LoggerFactory.getLogger(CimAdapterTest.class);

    public static void main(String[] args) {
        try {
            // Set the shutdown delay is one is passed.
            if (args.length > 0) {
                _shutdownDelay = Long.parseLong(args[0]);
            }

            // Create and start the connection manager.
            ClassPathXmlApplicationContext springContext = new ClassPathXmlApplicationContext(SPRING_CONFIG_FILE);
            _connectionManager = (ConnectionManager) springContext.getBean(CONNECTION_MANAGER_BEAN);

            // Create a CIM connection info and add the connection.
            CimConnectionInfo connectionInfo = new CimConnectionInfo();
            connectionInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);

            connectionInfo.setHost(providerIP);
            connectionInfo.setPort(providerPort);
            connectionInfo.setUser(providerUser);
            connectionInfo.setPassword(providerPassword);
            connectionInfo.setInteropNS(providerInterOpNamespace);
            connectionInfo.setImplNS(providerNamespace);
            connectionInfo.setUseSSL(isProviderSslEnabled);
            _connectionManager.addConnection(connectionInfo);

            // Create the shut down task and schedule it.
            ShutdownTask shutDownTask = new ShutdownTask();
            Timer timer = new Timer();
            timer.schedule(shutDownTask, _shutdownDelay);
        } catch (Exception e) {
            s_logger.error("Exception adding connection.", e);
        }
    }

    /**
     * Getter for the connection manager.
     * 
     * @return A reference to the connection manager.
     */
    public static ConnectionManager getConnectionManager() {
        return _connectionManager;
    }
}

/**
 * Internal class used to shutdown the connection manager and close the
 * connection opened by the test after a specified time interval.
 */
class ShutdownTask extends TimerTask {

    /**
     * Shuts down the connection manager.
     */
    public void run() {
        try {
            ConnectionManager connectionManager = CimAdapterTest.getConnectionManager();
            connectionManager.shutdown();
            System.exit(0);
        } catch (Exception e) {
        }
    }
}
