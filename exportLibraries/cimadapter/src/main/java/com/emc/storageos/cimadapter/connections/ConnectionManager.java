/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.connections;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.emc.storageos.cimadapter.connections.celerra.CelerraConnection;
import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimFilterInfo;
import com.emc.storageos.cimadapter.connections.cim.CimFilterMap;
import com.emc.storageos.cimadapter.connections.cim.CimListener;
import com.emc.storageos.cimadapter.connections.cim.CimListenerInfo;
import com.emc.storageos.cimadapter.connections.ecom.EcomConnection;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumerList;

/**
 * The ConnectionManager manages the connections to the storage arrays whose
 * indications are to be monitored. The ConnectionManager also creates and
 * starts the Listener which is notified when indications occur. It also loads
 * the filter map which specifies the indications for which the connections are
 * subscribed.
 */
public class ConnectionManager {

    private static final int INITIAL_DELAY = 1;
    private static final int PERIOD_MINS = 1;
    private static final long THRESHOLD_TIME = 10 * 60 * 1000; // Total minutes in milliseconds

    // A reference to the connection manager configuration.
    private ConnectionManagerConfiguration _configuration;

    // A reference to the CIM listener;
    private CimListener _listener;

    // A map of cache keys (host/port) to their connections
    private Map<String, CimConnection> _connections = new HashMap<String, CimConnection>();

    // A synchronization object to control access to shared objects
    private Lock connectionLock = new ReentrantLock();

    // A scheduled execution service that cleans up connections
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    // A map of cache keys in _connections to the last time the connection was retrieved
    private Map<String, Long> connectionLastTouch = new HashMap<>();

    // The logger.
    private static final Logger s_logger = LoggerFactory.getLogger(ConnectionManager.class);

    // Separator for the host/port cache connection entry
    private static final String HOST_PORT_SEPARATOR = ":";

    /**
     * Constructs a connection manager instance.
     * 
     * @param configuration A reference to the configuration.
     * 
     * @throws Exception When an error occurs initializing the connection
     *             manager.
     */
    public ConnectionManager(ConnectionManagerConfiguration configuration) throws Exception {

        // Set the configuration.
        _configuration = configuration;
        if (_configuration == null) {
            throw new ConnectionManagerException("Invalid null connection manager configuration.");
        }
        executorService.scheduleAtFixedRate(new CimConnectionReaper(), INITIAL_DELAY, PERIOD_MINS, TimeUnit.MINUTES);
    }

    /**
     * Private default constructor.
     */
    @SuppressWarnings("unused")
    private ConnectionManager() {
    }

    /**
     * Creates a new connection for which indications are to be monitored based
     * on the passed connection information.
     * 
     * @param connectionInfo Specifies the information necessary to establish a
     *            connection.
     * 
     * @throws ConnectionManagerException When a error occurs establishing the
     *             connection.
     */
    public void addConnection(CimConnectionInfo connectionInfo) throws ConnectionManagerException {
        connectionLock.lock();
        try {
            if (connectionInfo == null) {
                throw new ConnectionManagerException("Passed connection information is null.");
            }

            // If the listener has yet to be created, then create it now.
            if (_listener == null) {
                createIndicationListener(connectionInfo);
            }

            String hostAndPort = generateConnectionCacheKey(connectionInfo.getHost(), connectionInfo.getPort());

            // Only add a connection if there is not already a connection to the
            // provider specified in the passed connection information.
            if (isConnected(hostAndPort)) {
                s_logger.info("There is already a connection to the CIM provider on host/port {}", hostAndPort);
                return;
            }

            try {
                s_logger.info("Attempting to connect to the provider on host/port {}", hostAndPort);

                // Pause the listener when adding a new connection.
                _listener.pause();

                // Create a connection as specified by the passed connection
                // information.
                String connectionType = connectionInfo.getType();
                if (connectionType.equals(CimConstants.CIM_CONNECTION_TYPE)) {
                    createCimConnection(connectionInfo);
                } else if (connectionType.equals(CimConstants.ECOM_CONNECTION_TYPE)) {
                    createECOMConnection(connectionInfo);
                } else if (connectionType.equals(CimConstants.ECOM_FILE_CONNECTION_TYPE)) {
                    createCelerraConnection(connectionInfo);
                } else {
                    throw new ConnectionManagerException(MessageFormatter.format("Unsupported connection type {}",
                            connectionType).getMessage());
                }

                /**
                 * Get client's public certificate and persist them into trustStore.
                 */
                _listener.getClientCertificate(connectionInfo);
            } catch (ConnectionManagerException e) {
                throw e;
            } catch (Exception e) {
                throw new ConnectionManagerException(MessageFormatter.format(
                        "Failed establishing a connection to the provider on host/port {}", hostAndPort).getMessage(), e);
            } finally {
                // Now resume the listener.
                _listener.resume();
            }
        } finally {
            connectionLock.unlock();
        }
    }

    private void createIndicationListener(CimConnectionInfo connectionInfo) throws ConnectionManagerException {

        CimListenerInfo listenerInfo = _configuration.getListenerInfo();
        if (listenerInfo == null) {
            throw new ConnectionManagerException("CIM listener configuration is null.");
        }

        try {
            // We create a temporary connection to the provider host specified
            // by the passed connection information. We use this temporary
            // connection to extract the IP address of the local host on which
            // the connection manager is executing. We need to dynamically get
            // the IP address of the local host to create the CIM listener on
            // that host.
            Socket tempSocket = new Socket(connectionInfo.getHost(), connectionInfo.getPort());
            String listenerHostIP = tempSocket.getLocalAddress().toString();
            if (listenerHostIP.startsWith("/")) {
                listenerHostIP = listenerHostIP.substring(1);
            }
            s_logger.info("Listener host IP address is {}", listenerHostIP);
            listenerInfo.setHostIP(listenerHostIP);
            try {
                tempSocket.close();
            } catch (IOException ioe) {
                s_logger.warn("Error closing socket connection to provider host.", ioe);
            }
        } catch (IOException ioe) {
            throw new ConnectionManagerException("An error occurred obtaining the listener host IP address", ioe);
        }

        // Set the names for the subscription filters. The filters are named
        // using the IP address for the indication listener host that will
        // receive indications resulting from the filters.
        CimFilterMap filters = _configuration.getIndicationFilterMap();
        Iterator<CimFilterInfo> filtersIter = filters.getFilters().values().iterator();
        while (filtersIter.hasNext()) {
            filtersIter.next().setName(listenerInfo.getHostIP());
        }

        // Now create and start the listener.
        try {
            CimIndicationConsumerList indicationConsumers = _configuration.getIndicationConsumers();
            _listener = new CimListener(listenerInfo, indicationConsumers);
            _listener.startup();
        } catch (Exception e) {
            throw new ConnectionManagerException("Failed creating and starting the indication listener", e);
        }
    }

    /**
     * Removes an existing connection for which indication monitoring is no
     * longer desired.
     * 
     * @param hostAndPort Specifies the host/port for which the CIM connection was
     *            established.
     * 
     * @throws ConnectionManagerException When a error occurs removing the
     *             connection.
     */
    public void removeConnection(String host, Integer port) throws ConnectionManagerException {
        connectionLock.lock();
        try {
            String hostAndPort = ConnectionManager.generateConnectionCacheKey(host, port);
            internalRemoveConnection(hostAndPort);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Determines whether or not a connection has already been established for
     * the passed host.
     * 
     * @param hostAndPort The name of the host to verify.
     * 
     * @return true if a connection has been created for the passed host, false
     *         otherwise.
     * 
     * @throws ConnectionManagerException When the passed host is null or blank.
     */
    public boolean isConnected(String hostAndPort) throws ConnectionManagerException {
        connectionLock.lock();
        boolean isConnected = false;
        try {
            // Verify the passed host/port is not null or blank.
            if ((hostAndPort == null) || (hostAndPort.length() == 0)) {
                throw new ConnectionManagerException("Passed host/port is null or blank.");
            }

            CimConnection connection = _connections.get(hostAndPort);
            if (connection != null) {
                isConnected = true;
            }
        } finally {
            connectionLock.unlock();
        }
        return isConnected;
    }

    /**
     * Generate the key that is used to cache the connection.
     * 
     * @param host hostname
     * @param port port number
     * @return a hash of the two or null if host is null/empty
     */
    public static String generateConnectionCacheKey(String host, int port) {
        return (host == null || host.isEmpty()) ? null : host + HOST_PORT_SEPARATOR + port;
    }

    /**
     * Returns a reference to the connection for the provider at the passed
     * host and port
     * 
     * @param hostAndPort The name of the host/port on which the provider is executing.
     * 
     * @return A reference to the provider connection.
     * 
     * @throws ConnectionManagerException When the passed host is null or blank.
     */
    public CimConnection getConnection(String host, Integer port)
            throws ConnectionManagerException {
        connectionLock.lock();
        CimConnection connection = null;
        try {
            String hostAndPort = generateConnectionCacheKey(host, port);
            // Verify the passed host/port is not null or blank.
            if ((hostAndPort == null) || (hostAndPort.length() == 0)) {
                throw new ConnectionManagerException("Passed host/port is null or blank.");
            }
            connection = _connections.get(hostAndPort);
            if (connection != null) {
                // Every time the connection is returned, update the last get time
                connectionLastTouch.put(hostAndPort, System.currentTimeMillis());
            }
        } finally {
            connectionLock.unlock();
        }
        return connection;
    }

    /**
     * Shutdown the application.
     * 
     * Stops the listener (which releases its TCP port).
     * 
     * @throws ConnectionManagerException When an error occurs shutting don the
     *             connection manager.
     */
    public void shutdown() throws ConnectionManagerException {
        s_logger.info("Shutting down CIM adapter.");
        connectionLock.lock();
        try {
            // Need to close all the connections and undo their subscriptions.
            closeAllConnections();

            // Stop and destroy the listener.
            if (_listener != null) {
                _listener.stop();
                _listener = null;
            }
            executorService.shutdown();
        } catch (Exception e) {
            throw new ConnectionManagerException("An error occurred shutting down the connection manager", e);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Creates a connection to a CIM provider using the passed connection info.
     * 
     * @param connectionInfo Contains the information required to establish the
     *            connection.
     * 
     * @throws Exception When an error occurs establishing the connection to the
     *             CIM provider.
     */
    private void createCimConnection(CimConnectionInfo connectionInfo) throws Exception {
        String hostAndPort = generateConnectionCacheKey(connectionInfo.getHost(), connectionInfo.getPort());
        s_logger.info("Creating connection to CIM provider on host/port {}", hostAndPort);

        try {
            // Create the CIM connection.
            CimConnection connection = new CimConnection(connectionInfo, _listener,
                    _configuration.getIndicationFilterMap());
            connection.connect(_configuration.getSubscriptionsIdentifier(), _configuration.getDeleteStaleSubscriptionsOnConnect());
            _connections.put(hostAndPort, connection);
            connectionLastTouch.put(hostAndPort, System.currentTimeMillis());
        } catch (Exception e) {
            throw new Exception(MessageFormatter.format("Failed creating connection to CIM provider on host/port {}",
                    hostAndPort).getMessage(), e);
        }
    }

    /**
     * Creates a connection to an ECOM provider using the passed connection
     * info.
     * 
     * @param connectionInfo Contains the information required to establish the
     *            connection.
     * 
     * @throws Exception When an error occurs establishing the connection to the
     *             ECOM provider.
     */
    private void createECOMConnection(CimConnectionInfo connectionInfo) throws Exception {
        String hostAndPort = generateConnectionCacheKey(connectionInfo.getHost(), connectionInfo.getPort());
        s_logger.info("Creating connection to ECOM provider on host/port {}", hostAndPort);

        try {
            // Create the ECOM connection.
            EcomConnection connection = new EcomConnection(connectionInfo, _listener,
                    _configuration.getIndicationFilterMap());
            connection.connect(_configuration.getSubscriptionsIdentifier(), _configuration.getDeleteStaleSubscriptionsOnConnect());
            _connections.put(hostAndPort, connection);
            connectionLastTouch.put(hostAndPort, System.currentTimeMillis());
        } catch (Exception e) {
            throw new Exception(MessageFormatter.format("Failed creating connection to ECOM provider on host/port {}",
                    hostAndPort).getMessage(), e);
        }
    }

    /**
     * Creates a connection to an ECOM provider for a Celerra array using the
     * passed connection info.
     * 
     * @param connectionInfo Contains the information required to establish the
     *            connection.
     * 
     * @throws Exception When an error occurs establishing the connection to the
     *             ECOM provider for the Celerra array.
     */
    private void createCelerraConnection(CimConnectionInfo connectionInfo) throws Exception {
        String hostAndPort = generateConnectionCacheKey(connectionInfo.getHost(), connectionInfo.getPort());
        s_logger.info("Creating connection to Celerra ECOM provider on host/port {}", hostAndPort);

        try {
            // Create the ECOM connection.
            CelerraConnection connection = new CelerraConnection(connectionInfo, _listener,
                    _configuration.getIndicationFilterMap(),
                    _configuration.getCelerraMessageSpecs());
            connection.connect(_configuration.getSubscriptionsIdentifier(), _configuration.getDeleteStaleSubscriptionsOnConnect());
            _connections.put(hostAndPort, connection);
            connectionLastTouch.put(hostAndPort, System.currentTimeMillis());
        } catch (Exception e) {
            throw new Exception(MessageFormatter.format(
                    "Failed creating connection to Celerra ECOM provider on host/port {}", hostAndPort).getMessage(), e);
        }
    }

    /**
     * Closes all the connections being managed.
     */
    private void closeAllConnections() {
        // Need to close the connection which in turns removes all the
        // subscriptions for the connection.
        for (Entry<String, CimConnection> connectionEntry : _connections.entrySet()) {
            connectionEntry.getValue().close();
        }
        _connections.clear();
        connectionLastTouch.clear();
    }

    /**
     * Make subscription for the given CIM Connection
     * 
     * @param cimConnection {@link CimConnection} to make subscription for monitoring
     * @throws Exception Exception
     */
    public void subscribe(CimConnection cimConnection) throws Exception {
        s_logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        s_logger.debug("Subscription Identifier for subscribe action :{}", _configuration.getSubscriptionsIdentifier());
        cimConnection.subscribeForIndications(_configuration.getSubscriptionsIdentifier());
        s_logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Un-Subscribe cimConnection for the given passive SMIS provider connection
     * 
     * @param cimConnection {@link CimConnection} clear subscription for the given cimConnection
     */
    public void unsubscribe(CimConnection cimConnection) {
        s_logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        s_logger.debug("Subscription Identifier for unsubscribe action :{}", _configuration.getSubscriptionsIdentifier());
        cimConnection.unsubscribeForIndications(_configuration.getSubscriptionsIdentifier());
        s_logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * 
     * @param cimConnection {@link CimConnection} delete stale subscription for the given cimConnection
     */
    public void deleteStaleSubscriptions(CimConnection cimConnection) {
        s_logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        s_logger.debug("Subscription Identifier for delete subscription action :{}", _configuration.getSubscriptionsIdentifier());
        cimConnection.deleteStaleSubscriptions(_configuration.getSubscriptionsIdentifier());
        s_logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    private void internalRemoveConnection(String hostAndPort) {
        // Verify the passed host is not null or blank.
        if ((hostAndPort == null) || (hostAndPort.length() == 0)) {
            throw new ConnectionManagerException("Passed host/port is null or blank.");
        }

        try {
            // Verify we are managing a connection to the passed host.
            if (!isConnected(hostAndPort)) {
                throw new ConnectionManagerException(MessageFormatter.format(
                        "The connection manager is not managing a connection to host {}", hostAndPort).getMessage());
            }

            // Pause the listener when removing a connection.
            _listener.pause();

            // Remove the connection to the passed host.
            CimConnection connection = _connections.get(hostAndPort);
            if (connection != null) {
                s_logger.info("Closing connection to the CIM provider on host/port {}", hostAndPort);
                connection.close();
                _connections.remove(hostAndPort);
                connectionLastTouch.remove(hostAndPort);
            }
        } catch (ConnectionManagerException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectionManagerException(MessageFormatter.format(
                    "Failed removing the connection to the provider on host/port {}", hostAndPort).getMessage(), e);
        } finally {
            // Now resume the listener.
            _listener.resume();
        }
    }

    /**
     * Implementation to reap CimConnections that have not be in use for some time.
     */
    private class CimConnectionReaper implements Runnable {
        @Override
        public void run() {
            connectionLock.lock();
            Thread currentThread = Thread.currentThread();
            currentThread.setName(String.format("CimConnectionReaper %d", currentThread.getId()));
            try {
                s_logger.info("CimConnectionReaper start");
                int connectionsReaped = 0;
                // Copy the keys to prevent ConcurrentUpdate exception
                Set<String> connectionKeys = new HashSet<>(connectionLastTouch.keySet());
                for (String hostAndPort : connectionKeys) {
                    Long lastTime = connectionLastTouch.get(hostAndPort);
                    Long diff = System.currentTimeMillis() - lastTime;
                    String timeAndDate = new Date(lastTime).toString();
                    if (diff >= THRESHOLD_TIME) {
                        s_logger.info("Reaping connection {} that was last touched at {}", hostAndPort, timeAndDate);
                        internalRemoveConnection(hostAndPort);
                        connectionsReaped++;
                    } else {
                        s_logger.info(String.format("Connection %s was last touched at %s (%s)", hostAndPort, timeAndDate, lastTime));
                    }
                }
                s_logger.info("CimConnectionReaper end - There were {} connections reaped", connectionsReaped);
            } catch (Exception exp) {
                s_logger.info("Exception occurred", exp);
            } finally {
                connectionLock.unlock();
            }
        }
    }
}