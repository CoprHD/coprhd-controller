/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.connections.cim;

// Java security imports
import javax.cim.CIMObjectPath;
import javax.security.auth.Subject;
import javax.wbem.WBEMException;
import javax.wbem.client.PasswordCredential;
import javax.wbem.client.UserPrincipal;
import javax.wbem.client.WBEMClient;
import javax.wbem.client.WBEMClientFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.ConnectionManager;
import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;
import com.emc.storageos.cimadapter.processors.DefaultCimIndicationProcessor;

/**
 * Represents a CIM connection to a CIM provider.
 */
public class CimConnection {

    // The host to which the connection is made.
    protected String _host = CimConstants.DFLT_CIM_CONNECTION_HOST;

    // The host port to which the connection is made.
    protected int _port = 0;

    // The name of the user for which the connection is to be made.
    protected String _user = "";

    // The password for the user.
    protected String _pass_word = "";

    // The interop namespace for the CIMOM.
    protected String _interopNS = "";

    // The implementation namespace for the connection.
    protected String _implNS = "";

    // Indicates whether to use http or https as the connection protocol.
    private boolean _useSSL = false;

    // A unique name given to the connection. The connection name helps the
    // listener identify the connection which generated the indication.
    protected String _connectionName;

    // A reference to the CIM client.
    private WBEMClient _cimClient;

    // A reference to the CIM subscription manager used to manage the
    // subscriptions for this connection.
    private CimSubscriptionManager _subscriptionManager;

    // A reference to the CIM listener that listens for indications generated as
    // a result of this connection.
    private CimListener _listener;

    // A reference to the indication filter map.
    private CimFilterMap _filterMap;

    // A reference to the default indication processor for the connection.
    // Indication consumers can be configured to use the default processor, a
    // custom processor, or no processor at all.
    protected CimIndicationProcessor _dfltIndicationProcessor = null;

    // A reference to a logger.
    protected static final Logger s_logger = LoggerFactory.getLogger(CimConnection.class);

    /**
     * Constructs a CIM connection.
     * 
     * @param connectionInfo The bean containing the connection information.
     * @param listener The CIM indication listener for this connection.
     * @param filterMap The indication filters to be subscribed for this
     *            connection.
     */
    public CimConnection(CimConnectionInfo connectionInfo,
            CimListener listener, CimFilterMap filterMap) throws Exception {
        _host = connectionInfo.getConnectionParameter(CimConstants.CIM_HOST);
        _port = Integer.parseInt(connectionInfo.getConnectionParameter(CimConstants.CIM_PORT));
        _user = connectionInfo.getConnectionParameter(CimConstants.CIM_USER);
        _pass_word = connectionInfo.getConnectionParameter(CimConstants.CIM_PW);
        _interopNS = connectionInfo.getConnectionParameter(CimConstants.CIM_INTEROP_NS);
        _implNS = connectionInfo.getConnectionParameter(CimConstants.CIM_IMPL_NS);
        _useSSL = Boolean.parseBoolean(connectionInfo.getConnectionParameter(CimConstants.CIM_USE_SSL));

        // Create a unique name for the connection. The name is used by the
        // listener to associate an indication that it receives to the
        // connection the generated the indication. Be sure to create
        // the name after the connection info has been extracted.
        _connectionName = createConnectionName();

        // Keep a reference to the listener for when indications are enabled.
        _listener = listener;
        if (_listener == null) {
            throw new Exception("No indication listener");
        }

        // Keep a reference to the subscription filter map for when indications
        // are enabled for the connection.
        _filterMap = filterMap;
        if (_filterMap == null) {
            throw new Exception("No indication filter map");
        }

        s_logger.info("Created new CIM connection {}", _connectionName);
    }

    /**
     * Constructs a name for the connection using the connection type, host, and
     * port.
     * 
     * @return The name created for the connection.
     */
    private String createConnectionName() {
        StringBuilder nameBuffer = new StringBuilder();
        nameBuffer.append(getConnectionType());
        nameBuffer.append("-");
        nameBuffer.append(_host);
        nameBuffer.append("-");
        nameBuffer.append(_port);
        return nameBuffer.toString();
    }

    /**
     * Getter for the connection host.
     * 
     * @return The connection host
     */
    public String getHost() {
        return _host;
    }

    /**
     * Getter for the connection port.
     * 
     * @return The connection port
     */
    public int getPort() {
        return _port;
    }

    /**
     * Getter for the interop namespace for the CIMOM.
     * 
     * @return The interop namespace for the CIMOM.
     */
    public String getInteropNamespace() {
        return _interopNS;
    }

    /**
     * Getter for the implementation namespace for this CIMOM connection.
     * 
     * @return The implementation namespace for this CIMOM connection.
     */
    public String getImplNamespace() {
        return _implNS;
    }

    /**
     * Getter for the connection name.
     * 
     * @return The connection name
     */
    public String getConnectionName() {
        return _connectionName;
    }

    /**
     * Getter for the underlying CIM client.
     * 
     * @return The underlying CIM client.
     */
    public WBEMClient getCimClient() {
        return _cimClient;
    }

    /**
     * Getter for the indication filter map.
     * 
     * @return The indication filter map.
     */
    public CimFilterMap getIndicationFilterMap() {
        return _filterMap;
    }

    /**
     * Getter for the indication listener.
     * 
     * @return The indication listener.
     */
    public CimListener getIndicationListener() {
        return _listener;
    }

    /**
     * Opens the connection and sets up indication subscriptions on the CIMOM.
     * Call is synchronized by the caller {@link ConnectionManager}. If the
     * passed flag indicates, prior to setting up the indication subscriptions
     * for the connection, the subscription manager should attempt to delete any
     * old/stale subscriptions that exist on the provider to which the
     * connection is being made. The passed identifier is used by the
     * subscription manager to identify the stale subscriptions to be deleted.
     * 
     * Also registers this connection with the indication listener.
     * 
     * @param subscriptionsIdentifier The identifier to be used to identify
     *            subscriptions on the provider.
     * @param deleteStaleSubscriptions true to delete stale subscriptions on the
     *            provider to which the connection is being made.
     * 
     * @throws Exception if the connection cannot be established or if there is
     *             a problem setting up subscriptions.
     */
    public void connect(String subscriptionsIdentifier, boolean deleteStaleSubscriptions) throws Exception {
        s_logger.info("Establising connection for {}", _connectionName);

        String protocol = _useSSL ? CimConstants.SECURE_PROTOCOL : CimConstants.DEFAULT_PROTOCOL;
        CIMObjectPath path = CimObjectPathCreator.createInstance(protocol, _host, Integer.toString(_port), _interopNS, null, null);
        try {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(_user));
            subject.getPrivateCredentials().add(new PasswordCredential(_pass_word));
            _cimClient = WBEMClientFactory.getClient(CimConstants.CIM_CLIENT_PROTOCOL);

            // Operations block by default, so a timeout must be set in case the
            // CIM server becomes unreachable.
            // Commenting out, as timeout had been moved to cimom.properties file
            // _cimClient.setProperty(WBEMClientConstants.PROP_TIMEOUT, CimConstants.CIM_CLIENT_TIMEOUT);
            _cimClient.initialize(path, subject, null);

        } catch (Exception e) {
            s_logger.error("Could not establish connection for {}", _host, e);
            _cimClient.close();
            throw e;
        }
    }

    /**
     * Make new subscription to active SMIS provider to get indications for monitoring
     * 
     * @param subscriptionsIdentifier {@link String} subscriptionIdentifer to make subscription
     * @throws Exception
     */
    public void subscribeForIndications(String subscriptionsIdentifier) throws Exception {
        s_logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        s_logger.info("Initiating subscrption for monitoring use cases");
        try {
            // Register the connection with the listener if indications are enabled
            // for the connection.
            if ((_listener != null)) {
                _listener.register(this);
            }

            _subscriptionManager = new CimSubscriptionManager(this, subscriptionsIdentifier);
            _subscriptionManager.subscribe();
        } catch (Exception e) {
            s_logger.error("Error occurred while making subscription", e);
            _subscriptionManager.unsubscribe();
            throw e;
        }
        s_logger.info("Subscription for the {} is completed", subscriptionsIdentifier);
        s_logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Un-subscribe monitoring connection with passive SMIS provider
     * 
     * @param subscriptionsIdentifier {@link String} subscriptionIdentifer to make Un-Subscription
     */
    public void unsubscribeForIndications(String subscriptionsIdentifier) {
        s_logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        s_logger.info("Initiating unsubscription of passive provider {}", subscriptionsIdentifier);

        _subscriptionManager = new CimSubscriptionManager(this, subscriptionsIdentifier);
        _listener.unregister(this);
        _subscriptionManager.unsubscribe();
        s_logger.info("unsubscription of passive provider {} completed", subscriptionsIdentifier);
        s_logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Deletes Stale subscriptions
     * 
     * @param subscriptionsIdentifier {@link String} subscriptionIdentifer to delete stale subscriptions
     */
    public void deleteStaleSubscriptions(String subscriptionsIdentifier) {
        s_logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        _subscriptionManager = new CimSubscriptionManager(this, subscriptionsIdentifier);
        try {
            _subscriptionManager.deleteStaleSubscriptions();
        } catch (WBEMException e) {
            s_logger.error("Unable to delete Stale Subscriptions", e);
            // throw e;
        }
        s_logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Checks the connection.
     * 
     * Call is synchronized by the caller {@link ConnectionManager}
     * 
     * @return true if the connection is ok, otherwise false.
     */
    public boolean isConnected() {
        boolean connected = false;
        try {
            // Simple operation that should always succeed if the
            // connection is up.
            _cimClient.getClass(CimObjectPathCreator.createInstance(CimConstants.CIM_INDICATION_OBJ_PATH, getInteropNamespace()), true,
                    true, false, null);
            connected = true;
        } catch (Exception e) {
            s_logger.error("Failed checking the CIM client connection", e);
        }
        return connected;
    }

    /**
     * Closes the connection.
     */
    public void close() {
        try {
            if (_cimClient != null) {
                _cimClient.close();
            }
        } catch (Exception e) {
            s_logger.error(e.getMessage(), e);
        }
    }

    /**
     * Returns the connection type.
     * 
     * @return The connection type
     */
    public String getConnectionType() {
        return CimConstants.CIM_CONNECTION_TYPE;
    }

    /**
     * Returns an instance of the default indication processor for CIM
     * connections. Overridden by derived connection classes to return an
     * instance of the default processor for those connection types.
     * 
     * @return An instance of the default indication processor for CIM
     *         connections.
     */
    protected CimIndicationProcessor getDefaultIndicationProcessor() {
        if (_dfltIndicationProcessor == null) {
            _dfltIndicationProcessor = new DefaultCimIndicationProcessor(this);
        }

        return _dfltIndicationProcessor;
    }
}
