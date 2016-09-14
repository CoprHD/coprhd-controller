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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.emc.storageos.driver.ibmsvcdriver.connection.factory.SSHConnectionFactory;
import com.emc.storageos.driver.ibmsvcdriver.exceptions.ConnectionManagerException;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants.ConnectionType;

public class ConnectionManager {

    // A list of the connections are managed.
    private ArrayList<SSHConnection> _connections = null;
    
    // The logger.
    private static final Logger _log = LoggerFactory.getLogger(ConnectionManager.class);

    /**
     * Constructs a connection manager instance and initializes the Connection Pool.
     */
    public ConnectionManager() {
        _connections = new ArrayList<SSHConnection>();
    }

    /**
     * Creates a connection to a SSH node using the passed connection info.
     * 
     * @param connectionInfo Contains the information required to establish the
     *            connection.
     * 
     * @throws Exception When an error occurs establishing the connection to the SSH
     * 
     */
    public Connection createConnection(ConnectionInfo connectionInfo, ConnectionType connType) throws Exception {
        String hostName = connectionInfo.get_hostname();

        SSHConnection connection = null;
        
        if (connType.name().equals("SSH")) {

            _log.info("Creating connection to the host {}", hostName);

            try {

                // Create the SSH connection.
                connection = (SSHConnection) new SSHConnectionFactory().getConnection(connectionInfo);
                connection.connect();

                // Adding the connection to the pool
                _connections.add(connection);

                _log.info("Connection Created to the host {}", hostName);
            } catch (Exception e) {
                _log.error("Failed creating connection to the host {}", hostName + e.getMessage());
                throw new Exception(MessageFormatter.format("Failed creating connection to the host {}",
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

        Connection retConnection = null;

        try {
            if (_connections != null && _connections.size() > 0) {
                
                for (SSHConnection connection : _connections) {
                    String connectedHostName = connection.getHostname();
                    if (hostName.equals(connectedHostName)) {
                        retConnection = connection;
                        _log.info("Retrieved connection from the Connection Pool to the host {}", hostName);
                        break;
                    }
                }
            } else {
                retConnection = createConnection(connectionInfo, ConnectionType.SSH);
            }
        } catch (Exception e) {
            _log.info("Failed creating connection to the host {}", connectionInfo.get_hostname());
            throw new ConnectionManagerException(MessageFormatter.format(
                    "Failed creating connection to the host {}", hostName).getMessage(), e);
        }

        _log.info("Returning the retrieved connection from the Connection Pool to the host {}", hostName);
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

        Connection retConnection = null;
        for (SSHConnection connection : _connections) {
            String connectedHostName = connection.getHostname();
            if (hostName.equals(connectedHostName)) {
                retConnection = connection;
                _log.info("Retrieved connection from the Connection Pool to the host {}", hostName);
                break;
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
            for (SSHConnection connection : _connections) {

                String connectedHostName = connection.getHostname();

                if (hostName.equals(connectedHostName)) {
                    connection.disconnect();

                    // Removing the connection to the pool
                    _connections.remove(connection);
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
        for (SSHConnection connection : _connections) {
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

        if (_connections != null) {
            // Need to close the connection which in turns removes all the
            // subscriptions for the connection.
            for (SSHConnection connection : _connections) {
                _log.info("Closing the connection from the Connection Poolto to the host {}", connection.getHostname());
                connection.disconnect();
            }
            _connections.clear();
            _log.info("Closed all the connection from the Connection Pool.");

        }
    }

}
