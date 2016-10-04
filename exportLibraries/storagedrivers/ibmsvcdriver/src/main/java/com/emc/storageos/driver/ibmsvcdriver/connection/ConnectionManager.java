/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.connection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.storagedriver.Registry;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.emc.storageos.driver.ibmsvcdriver.connection.factory.SSHConnectionFactory;
import com.emc.storageos.driver.ibmsvcdriver.exceptions.ConnectionManagerException;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants.ConnectionType;

/**
 * Connection Manager class is used to establish connectivity to the IBM SVC Array using SSH
 * and store connections in a connection pool so it could be re used.
 */
public class ConnectionManager {

    // A list of the connections are managed.
    private ArrayList<SSHConnection> connectionPool = null;
    private Registry driverRegistry;

    private static final Object syncObject = new Object();

    private static ConnectionManager instance;

    // The logger.
    private static final Logger _log = LoggerFactory.getLogger(ConnectionManager.class);

    /**
     * Constructs a connection manager instance and initializes the Connection Pool.
     */
    public ConnectionManager() {
        connectionPool = new ArrayList<>();
    }

    /**
     * Get the instance
     * @return The IBM SVC ConnectionManager instance
     */
    public static ConnectionManager getInstance(){
        synchronized (syncObject) {
            if (instance == null) {
                instance = new ConnectionManager();
            }
        }

        return instance;
    }

    /**
     * Sets the persistence store.
     *
     * @param driverRegistry The driver persistence registry.
     */
    public void setDriverRegistry(Registry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    /**
     * Creates a connection to a SSH node using the passed connection info.
     * 
     * @param connectionInfo Contains the information required to establish the
     *            connection.
     * @param connType Connection Type
     * @throws ConnectionManagerException When an error occurs establishing the connection to the SSH
     * @return connection
     */
    public Connection createConnection(ConnectionInfo connectionInfo, ConnectionType connType) throws ConnectionManagerException {
        String hostName = connectionInfo.get_hostname();

        SSHConnection connection = null;
        
        if ("SSH".equals(connType.name())) {

            _log.info("Creating connection to the host {}", hostName);

            try {

                // Create the SSH connection.
                connection = (SSHConnection) new SSHConnectionFactory().getConnection(connectionInfo);
                connection.connect();

                // Adding the connection to the pool
                connectionPool.add(connection);

                _log.info("Connection Created to the host {}", hostName);

                _log.info(String.format("Setting conn info in registry %s - %s - %s - %s - %s", connectionInfo.get_systemNativeId(), connectionInfo.get_hostname(),
                        connectionInfo.get_port(), connectionInfo.get_username(), connectionInfo.get_password()));
                setConnInfoToRegistry(connectionInfo.get_systemNativeId(), connectionInfo.get_hostname(),
                        connectionInfo.get_port(), connectionInfo.get_username(), connectionInfo.get_password());

            } catch (Exception e) {
                _log.error("Failed creating connection to the host {}", hostName + e.getMessage());
                throw new ConnectionManagerException(MessageFormatter.format("Failed creating connection to the host {}",
                        hostName).getMessage(), e);
            }
        } else {
            _log.info("Connection type not supported to the host {}", hostName);
        }

        return connection;
    }

    /**
     * Returns a reference to the connection for the provider at the passed
     * host.
     * 
     * @param connectionInfo The connection details of the host on which the provider is executing.
     * 
     * @return A reference to the provider connection.
     * 
     * @throws ConnectionManagerException When the passed host is null or blank.
     */

    public synchronized Connection getConnection(ConnectionInfo connectionInfo) throws ConnectionManagerException {

        _log.info("Getting connection from the Connection Pool to the host {}", connectionInfo.get_hostname());

        String hostName = connectionInfo.get_hostname();

        // Verify the passed host is not null or blank.
        if ((hostName == null) || (hostName.length() == 0)) {
            _log.error("Unable to retrieve connection from the Connection Pool as passed hostName is null or blank");
            throw new ConnectionManagerException("Passed host is null or blank.");
        }

        SSHConnection retConnection;

        try{
                retConnection = (SSHConnection) createConnection(connectionInfo, ConnectionType.SSH);
        }catch (Exception e) {
            _log.info("Failed creating connection to the host {}", connectionInfo.get_hostname());
            throw new ConnectionManagerException(MessageFormatter.format(
                    "Failed creating connection to the host {}", hostName).getMessage(), e);
        }

        _log.info("Returning the retrieved connection from the Connection Pool to the host {}", hostName);

        if(retConnection != null){
            Session clientSession = retConnection.getClientSession();
            if (clientSession == null || !clientSession.isConnected()) {
                //clientSession.connect fails as reconnecting the same disconnected session throws error - com.jcraft.jsch.JSchException: Packet corrupt
                retConnection.connect();
            }
        }



        return retConnection;
    }

    /**
     * Returns a reference to the connection for the provider at the passed
     * host.
     * 
     * @param hostName The name of the host on which the provider is executing.
     * 
     * @return A reference to the provider connection.
     * 
     * @throws ConnectionManagerException When the passed host is null or blank.
     */
    public synchronized Connection getConnection(String hostName)
            throws ConnectionManagerException {

        _log.info("Getting connection from the Connection Pool to the host {}", hostName);

        // Verify the passed host is not null or blank.
        if ((hostName == null) || (hostName.length() == 0)) {
            _log.error("Unable to retrieve connection from the Connection Pool as passed hostName is null or blank");
            throw new ConnectionManagerException("Passed host is null or blank.");
        }

        SSHConnection retConnection = null;
        for (SSHConnection connection : connectionPool) {
            String connectedHostName = connection.getHostname();
            if (hostName.equals(connectedHostName)) {
                retConnection = connection;
                _log.info("Retrieved connection from the Connection Pool to the host {}", hostName);
                break;
            }
        }

        // Ensure session is connected
        if(retConnection != null){
            Session clientSession = retConnection.getClientSession();
            if (clientSession == null || !clientSession.isConnected()) {
                //clientSession.connect fails as reconnecting the same disconnected session throws error - com.jcraft.jsch.JSchException: Packet corrupt
                retConnection.connect();
            }
        }

        _log.info("Returning the retrieved connection from the Connection Pool to the host {}", hostName);
        return retConnection;
    }

    /**
     * Removes an existing connection for which monitoring is no
     * longer desired.
     * 
     * @param hostName Specifies the host for which the CIM connection was
     *            established.
     * 
     * @throws ConnectionManagerException When a error occurs removing the
     *             connection.
     */
    public synchronized void removeConnection(String hostName) throws ConnectionManagerException {
        // Verify the passed host is not null or blank.
        if ((hostName == null) || (hostName.length() == 0)) {
            _log.error("Unable to retrieve connection from the Connection Pool as passed hostName is null or blank");
            throw new ConnectionManagerException("Passed host is null or blank.");
        }

        try {
            // Verify we are managing a connection to the passed host.
            if (!isConnected(hostName)) {
                _log.error("The connection manager is not managing a connection to host", hostName);
                throw new ConnectionManagerException(MessageFormatter.format(
                        "The connection manager is not managing a connection to host {}", hostName).getMessage());
            }

            _log.info("Closing connection to the host {}", hostName);

            // Remove the connection to the passed host.
            for (SSHConnection connection : connectionPool) {

                String connectedHostName = connection.getHostname();

                if (hostName.equals(connectedHostName)) {
                    connection.disconnect();

                    // Removing the connection to the pool
                    connectionPool.remove(connection);
                    _log.info("Removed connection from the Connection Pool to the host {}", hostName);
                    break;
                }
            }
        } catch (ConnectionManagerException e) {
            _log.error("Remove connection failed from the Connection Pool to the host {}", hostName);
            throw e;
        } catch (Exception e) {
            _log.error("Remove connection failed from the Connection Pool to the host {}", hostName);
            throw new ConnectionManagerException(MessageFormatter.format(
                    "Failed removing the connection from the Connection Pool to the host {}", hostName).getMessage(), e);
        }
    }

    /**
     * Determines whether or not a connection has already been established for
     * the passed host.
     * 
     * @param hostName The name of the host to verify.
     * 
     * @return true if a connection has been created for the passed host, false
     *         otherwise.
     * 
     * @throws ConnectionManagerException When the passed host is null or blank.
     */
    public synchronized boolean isConnected(String hostName) throws ConnectionManagerException {
        // Verify the passed host is not null or blank.
        if ((hostName == null) || (hostName.length() == 0)) {
            _log.info("Unable to retrieve connection from the Connection Pool as passed hostName is null or blank");
            throw new ConnectionManagerException("Passed host is null or blank.");
        }

        boolean isConnected = false;
        for (SSHConnection connection : connectionPool) {
            if (connection.isConnected()) {
                String connectedHostName = connection.getHostname();
                if (hostName.equals(connectedHostName)) {
                    isConnected = true;
                    _log.info("Retrieved connection from the Connection Pool to the host {}", hostName);
                    break;
                }
            }
        }

        return isConnected;
    }

    /**
     * Shutdown the application.
     * 
     * Stops the listener (which releases its TCP port).
     * 
     * @throws ConnectionManagerException When an error occurs shutting don the
     *             connection manager.
     */
    public synchronized void shutdown() throws ConnectionManagerException {
        _log.info("Closing all the connections from the Connection Pool.");

        try {
            // Need to close all the connections
            closeAllConnections();

        } catch (Exception e) {
            _log.error("Unable to close all the connections from the Connection Pool.");
            throw new ConnectionManagerException("An error occurred shutting down the connection manager", e);
        }
        _log.info("Closed all the connections from the Connection Pool.");
    }

    /**
     * Closes all the connections being managed.
     */
    private void closeAllConnections() {

        if (connectionPool != null) {
            // Need to close the connection which in turns removes all the
            // subscriptions for the connection.
            for (SSHConnection connection : connectionPool) {
                _log.info("Closing the connection from the Connection Poolto to the host {}", connection.getHostname());
                connection.disconnect();
            }
            connectionPool.clear();
            _log.info("Closed all the connection from the Connection Pool.");

        }
    }

    /**
     * Get connection info from registry
     *
     * @param systemNativeId - System Native ID of Array
     * @param attrName
     *            use string constants in the IBMSVCConstants.java. e.g.
     *            IBMSVCConstants.IP_ADDRESS
     * @return Ip_address, port, username or password for given systemId and
     *         attribute name
     */
    public String getConnInfoFromRegistry(String systemNativeId, String attrName) {

        _log.info(String.format(" DriverRegistry %s.%n", this.driverRegistry.getDriverAttributes(IBMSVCConstants.DRIVER_NAME)));

        Map<String, List<String>> attributes = this.driverRegistry
                .getDriverAttributesForKey(IBMSVCConstants.DRIVER_NAME, systemNativeId);
        if (attributes == null) {
            _log.info("Connection info for " + systemNativeId + " is not set up in the registry");
            return null;
        } else if (attributes.get(attrName) == null) {
            _log.info(attrName + "is not found in the registry");
            return null;
        } else {
            return attributes.get(attrName).get(0);
        }
    }

    /**
     * Set connection information to registry
     *
     * @param systemNativeId - Native System ID
     * @param ipAddress - IP Address of Array
     * @param port - Port of Array
     * @param username - Username of Array
     * @param password - Password of Array
     */
    public void setConnInfoToRegistry(String systemNativeId, String ipAddress, int port, String username,
                                      String password) {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> listIP = new ArrayList<>();
        List<String> listPort = new ArrayList<>();
        List<String> listUserName = new ArrayList<>();
        List<String> listPwd = new ArrayList<>();

        listIP.add(ipAddress);
        attributes.put(IBMSVCConstants.IP_ADDRESS, listIP);
        listPort.add(Integer.toString(port));
        attributes.put(IBMSVCConstants.PORT_NUMBER, listPort);
        listUserName.add(username);
        attributes.put(IBMSVCConstants.USER_NAME, listUserName);
        listPwd.add(password);
        attributes.put(IBMSVCConstants.PASSWORD, listPwd);

        _log.info(String.format("Setting client connection for the Storage System %s - %s.%n", IBMSVCConstants.DRIVER_NAME, systemNativeId));

        this.driverRegistry.setDriverAttributesForKey(IBMSVCConstants.DRIVER_NAME, systemNativeId, attributes);
        _log.info(String.format(" DriverRegistry %s.%n", this.driverRegistry.getDriverAttributes(IBMSVCConstants.DRIVER_NAME)));
    }

    /**
     * Get SSH Client
     *
     * @param systemId
     *            storage system id
     * @return ssh client handler
     */
    public SSHConnection getClientBySystemId(String systemId) {
        String ipAddress;
        String port;
        String username;
        String password;
        SSHConnection client = null;

        if (systemId != null) {
            systemId = systemId.trim();
        }

        _log.info(String.format("Getting client connection for the Storage System %s.%n", systemId));

        synchronized (syncObject) {

                _log.info(String.format("Before getting the connection details from the Registry %s.%n", systemId));

            ipAddress = getConnInfoFromRegistry(systemId, IBMSVCConstants.IP_ADDRESS);
                port = getConnInfoFromRegistry(systemId, IBMSVCConstants.PORT_NUMBER);
                username = getConnInfoFromRegistry(systemId, IBMSVCConstants.USER_NAME);
                password = getConnInfoFromRegistry(systemId, IBMSVCConstants.PASSWORD);

                _log.info(String.format("After getting the connection details from the Registry %s.%n", systemId));

                _log.info(String.format("Get conn info in registry %s - %s - %s - %s", ipAddress, port, username, password));

                if (ipAddress != null && username != null && password != null) {

                    ConnectionInfo connectionInfo = new ConnectionInfo(ipAddress, Integer.parseInt(port), username,
                            password,systemId);
                    try {

                        client = (SSHConnection) getConnection(connectionInfo);

                        _log.info(String.format("Connection has been established for the host %s", ipAddress));

                    } catch (Exception e) {
                        _log.error("Exception when creating ssh client instance for storage system {} ", systemId, e);
                        e.printStackTrace();
                    }
                } else {
                    _log.error(
                            "Some of the following for storage system {} are Missing: IP Address, username, password.",
                            systemId);
                }

        }

        if(client != null){
            Session clientSession = client.getClientSession();
            if (clientSession == null || !clientSession.isConnected()) {
                //clientSession.connect fails as reconnecting the same disconnected session throws error - com.jcraft.jsch.JSchException: Packet corrupt
                client.connect();
            }
        }

        return client;

    }


}
