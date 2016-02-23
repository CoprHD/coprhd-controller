/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.cimadapter.connections.ConnectionManager;
import com.emc.storageos.cimadapter.connections.ConnectionManagerException;
import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * This class will encapsulate the CIM ConnectionManager.
 */
public class CIMConnectionFactory {
    // Logger
    private static final Logger _log = LoggerFactory
            .getLogger(CIMConnectionFactory.class);
    /**
     * connectionManager to get connections from cache.
     */
    private ConnectionManager _connectionManager = null;
    /**
     * dbClient instance.
     */
    private DbClient _dbClient = null;

    @Autowired
    private RecordableEventManager _evtMgr;

    private CoordinatorClient coordinatorClient;

    private static final String EVENT_SERVICE_TYPE = "StorageProvider";
    private static final String EVENT_SERVICE_SOURCE = "CIMConnectionFactory";
    private static final String STORAGE_PROVIDER_DOWN_DESCRIPTION = "Storage Provider is Down";
    private static final String STORAGE_PROVIDER_UP_DESCRIPTION = "Storage Provider is Up";
    private static final String STORAGE_PROVIDER_DOWN_DESCRIPTION_VNXFILE = "Storage Provider is Down for this VNXFile. Provider IP : ";
    private static final String STORAGE_PROVIDER_UP_DESCRIPTION_VNXFILE = "Storage Provider is Up for this VNXFile. Provider IP : ";
    /**
     * CIM Object Path instance to validate connectivity
     */
    private static final CIMObjectPath _cop = CimObjectPathCreator.createInstance(
            Constants.PROFILECLASS, CimConstants.DFLT_CIM_CONNECTION_INTEROP_NS);

    /**
     * setter method to inject connectionManager.
     * 
     * @param cimConnectionManager
     */
    public void setConnectionManager(ConnectionManager cimConnectionManager) {
        _connectionManager = cimConnectionManager;
    }

    /**
     * setter method to inject dbClient.
     * 
     * @param dbClient
     */
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    /**
     * Return the cimConnection for the given storageDevice.
     * We would end up in scenarios ,where Provider present in StorageDevice is not there in
     * PhysicalStorageSystem.
     * TODO remove once metering integrated.
     * 
     * @param storageDevice
     *            : StorageDevice.
     * @return CimConnection.
     */
    public synchronized CimConnection getConnection(final StorageSystem storageDevice) {
        CimConnection connection = null;
        try {
            _connectionManager.configure(coordinatorClient.getPropertyInfo());
            /**
             * Check cimConnection already exist for vnxfile, if not create new one
             */
            if (StorageSystem.Type.vnxfile.name().equals(storageDevice.getSystemType())) {
                connection = _connectionManager.getConnection(storageDevice.getSmisProviderIP(), storageDevice.getPortNumber());
            } else {
                connection = getConnection(storageDevice.getSmisProviderIP(), storageDevice.getSmisPortNumber().toString());
            }

            if (null == connection) {
                final CimConnectionInfo connInfo = new CimConnectionInfo();
                connInfo.setHost(storageDevice.getSmisProviderIP());
                connInfo.setPort(storageDevice.getSmisPortNumber());
                connInfo.setUser(storageDevice.getSmisUserName());
                connInfo.setPassword(storageDevice.getSmisPassword());
                connInfo.setUseSSL(storageDevice.getSmisUseSSL());
                connInfo.setInteropNS(CimConstants.DFLT_CIM_CONNECTION_INTEROP_NS);

                // Set the type of connection to be created.
                connInfo.setType(getConnectionTypeForDevice(storageDevice.getSystemType()));

                // Set the implementation namespace for this type of storage device
                connInfo
                        .setImplNS(getImplNamespaceForDevice(storageDevice.getSystemType()));

                _connectionManager.addConnection(connInfo);
                connection = getConnection(storageDevice.getSmisProviderIP(), storageDevice.getSmisPortNumber().toString());
            }
        } catch (final ConnectionManagerException ex) {
            _log.error("No CIMOM Connection found for ipaddress due to ",
                    ex);
        }
        return connection;
    }

    /**
     * Refresh the SMISProvider connections. This will be called after loading
     * the SMIS Provider information from DB.
     * 
     * @param smisproviderList
     *            : List of SMISProvider.
     * @return List<URI> : returns the list of active provider URIs.
     */
    public List<URI> refreshConnections(final List<StorageProvider> smisProviderList) {
        _log.debug("In refreshConnections()");
        List<URI> activeProviderURIList = new ArrayList<URI>();
        for (StorageProvider smisProvider : smisProviderList) {
            try {
                CimConnection connection = getConnection(smisProvider.getIPAddress(), smisProvider.getPortNumber().toString());
                if (null == connection) {
                    _log.error("No CIMOM connection found for ip/port {}",
                            ConnectionManager.generateConnectionCacheKey(smisProvider.getIPAddress(), smisProvider.getPortNumber()));
                    // No need to add connection, as getConnection() called from any thread would create it.
                    continue;
                }
                validateProviderConnection(smisProvider, connection,
                        activeProviderURIList);
            } catch (final DatabaseException ex) {
                _log.error(
                        "DatabaseException occurred while fetching the storageDevice for {} due to ",
                        smisProvider.getId(), ex);
            } catch (final ConnectionManagerException ex) {
                _log.error("No CIMOM Connection found for ipaddress due to ",
                        ex);
            } catch (final Exception ex) {
                _log.error("Exception while refreshing connections due to ",
                        ex);
            }
        }
        return activeProviderURIList;
    }

    /**
     * Creates valid CIMConnection instances and removes invalid cimConnection instances from connectionManager for vnxfile StorageSystem's
     * smis provider
     * 1. Get all vnxFile StorageSystem from DB
     * 2. Check if the connection is valid one using liveliness check
     * 3. If the connection can communicate smis provider do nothing
     * 4. else remove connection from _connectionManager
     * 
     * @throws IOException
     * @throws ConnectionManagerException
     */
    public void refreshVnXFileConnections() throws IOException, ConnectionManagerException {
        List<URI> allStorageSystemsURIList = _dbClient
                .queryByType(StorageSystem.class, true);
        List<StorageSystem> allStorageSystemList = _dbClient.queryObject(
                StorageSystem.class, allStorageSystemsURIList);
        for (StorageSystem storageSystem : allStorageSystemList) {
            if (null != storageSystem &&
                    Type.vnxfile.toString().equals(storageSystem.getSystemType())) {
                CimConnection cimConnection = getConnection(storageSystem);
                if (null == cimConnection) {
                    _log.error("No CIMOM connection found for ip/port {}",
                            ConnectionManager.generateConnectionCacheKey(storageSystem.getSmisProviderIP(),
                                    storageSystem.getSmisPortNumber()));
                    recordStorageProviderEvent(OperationTypeEnum.STORAGE_PROVIDER_DOWN,
                            STORAGE_PROVIDER_DOWN_DESCRIPTION_VNXFILE + storageSystem.getSmisProviderIP(),
                            storageSystem.getId());
                    // No need to add connection, as getConnection() called from any thread would create it.
                    continue;
                }
                if (!checkConnectionliveness(cimConnection)) {
                    // If the provider is in NOTCONNECTED state, generating failure event &
                    // changing connection status for storagesystem
                    if (null != storageSystem.getSmisConnectionStatus() &&
                            ConnectionStatus.CONNECTED.toString().equalsIgnoreCase(
                                    storageSystem.getSmisConnectionStatus())) {
                        recordStorageProviderEvent(OperationTypeEnum.STORAGE_PROVIDER_DOWN,
                                STORAGE_PROVIDER_DOWN_DESCRIPTION_VNXFILE + storageSystem.getSmisProviderIP(),
                                storageSystem.getId());
                        storageSystem.setSmisConnectionStatus(ConnectionStatus.NOTCONNECTED.toString());
                        _dbClient.persistObject(storageSystem);
                    }
                    _connectionManager.removeConnection(storageSystem.getSmisProviderIP(), storageSystem.getPortNumber());
                    _log.info("Removed invalid connection for smis {} from connectionManager",
                            ConnectionManager.generateConnectionCacheKey(storageSystem.getSmisProviderIP(),
                                    storageSystem.getSmisPortNumber()));
                }
                else {
                    // If the provider is in CONNECTED state, generating success event &
                    // changing connection status for storagesystem
                    if (null != storageSystem.getSmisConnectionStatus() &&
                            ConnectionStatus.NOTCONNECTED.toString().equalsIgnoreCase(
                                    storageSystem.getSmisConnectionStatus())) {
                        recordStorageProviderEvent(OperationTypeEnum.STORAGE_PROVIDER_UP,
                                STORAGE_PROVIDER_UP_DESCRIPTION_VNXFILE + storageSystem.getSmisProviderIP(),
                                storageSystem.getId());
                        storageSystem.setSmisConnectionStatus(ConnectionStatus.CONNECTED.toString());
                        _dbClient.persistObject(storageSystem);
                    }
                }
            }
        }
    }

    /**
     * This will be an indication to the ConnectionManager that we need the connection to remain alive
     * until there is a call to unsetKeepAliveForConnection.
     *
     * @param storageSystem [IN] - StorageSystem
     */
    public void setKeepAliveForConnection(StorageSystem storageSystem) {
        _connectionManager.pinConnection(storageSystem.getSmisProviderIP(), storageSystem.getSmisPortNumber());
    }

    /**
     * This will be an indication to the ConnectionManager that we may no longer need to keep the connection alive.
     *
     * @param storageSystem [IN] - StorageSystem
     */
    public void unsetKeepAliveForConnection(StorageSystem storageSystem) {
        _connectionManager.unpinConnection(storageSystem.getSmisProviderIP(), storageSystem.getSmisPortNumber());
    }

    /**
     * Validates the connection active status and liveness.
     * 
     * @param smisProvider
     * @param connection
     * @param activeProviderURIList
     * @throws ConnectionManagerException
     */
    private void validateProviderConnection(
            final StorageProvider smisProvider, final CimConnection connection,
            final List<URI> activeProviderURIList) throws ConnectionManagerException {
        _log.debug("In validateProviderConnection()");
        // Don't add the inactive SMIS Provider.
        if (!smisProvider.getInactive()) {
            _log.info("{} is the active smis provider", smisProvider.getId());
            // If Provider Connection is active then do the scanner
            // otherwise no point of doing the scanner.
            try {
                if (checkConnectionliveness(connection)) {
                    if (StorageProvider.ConnectionStatus.NOTCONNECTED.toString().equalsIgnoreCase(
                            smisProvider.getConnectionStatus())) {
                        recordStorageProviderEvent(OperationTypeEnum.STORAGE_PROVIDER_UP,
                                STORAGE_PROVIDER_UP_DESCRIPTION, smisProvider.getId());
                    }
                    smisProvider
                            .setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED
                                    .toString());
                    activeProviderURIList.add(smisProvider.getId());
                } else {
                    if (StorageProvider.ConnectionStatus.CONNECTED.toString().equalsIgnoreCase(
                            smisProvider.getConnectionStatus())) {
                        recordStorageProviderEvent(OperationTypeEnum.STORAGE_PROVIDER_DOWN,
                                STORAGE_PROVIDER_DOWN_DESCRIPTION, smisProvider.getId());
                    }
                    _connectionManager.removeConnection(smisProvider.getIPAddress(), smisProvider.getPortNumber());
                    _log.error("Connection Liveness Failed {}",
                            smisProvider.getIPAddress());
                    smisProvider
                            .setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED
                                    .toString());
                }
                _dbClient.persistObject(smisProvider);
            } catch (DatabaseException ioEx) {
                _log.error(
                        "Not able to update connectionStatus for Provider due to ",
                        ioEx);
            }
        } else {
            _log.info("{} is not the active smis provider", smisProvider.getId());
            if (null != connection) {
                _connectionManager.removeConnection(smisProvider.getIPAddress(), smisProvider.getPortNumber());
            }
        }
    }

    public void recordStorageProviderEvent(OperationTypeEnum opType,
            String description, URI storageProvider) {
        String evType;
        evType = opType.getEvType(true);
        _log.info("Recording {} event", evType);

        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */evType,
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* VirtualPool */null,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */storageProvider,
                /* description */description,
                /* timestamp */System.currentTimeMillis(),
                /* extensions */"",
                /* native guid */null,
                /* record type */RecordType.Event.name(),
                /* Event Source */EVENT_SERVICE_SOURCE,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");

        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: {}.",
                    description, ex);
        }
    }

    /**
     * To-Do: check for Connection liveness 1. if null, add a new Connection. 2.
     * if connection present, then check for liveness.
     * 
     * @return boolean
     */
    public boolean checkConnectionliveness(CimConnection connection) {
        boolean isLive = false;
        if (null == connection) {
            return isLive;
        }
        WBEMClient wbemClient = connection.getCimClient();
        _log.debug("copPath:{}", _cop);
        try {
            // Call the provider to get computer systems.
            wbemClient.enumerateInstanceNames(_cop);
            isLive = true;
        } catch (WBEMException wbemEx) {
            _log.error("Invalid connection found for ipAddress: {}", connection.getHost());
        }
        return isLive;
    }

    /**
     * Get a CimConnection for a given hostName.
     * if connection is null, then add a new Connection.
     * getConnection, at any point of time will get a new Connection Object.
     * 
     * @param hostName
     *            : Provider hostName, if not existing.
     * @return CimConnection.
     * @throws IOException
     */
    public synchronized CimConnection getConnection(String ipAddress, String port) {
        CimConnection connection = null;
        try {
            _connectionManager.configure(coordinatorClient.getPropertyInfo());
            connection = _connectionManager.getConnection(ipAddress, Integer.parseInt(port));
            if (null == connection) {
                connection = addConnection(ipAddress, port);

            }
        } catch (final ConnectionManagerException ex) {
            _log.error(
                    "Problem establishing connection to the SMI-S Provider: {} due to ",
                    ipAddress, ex);
            throw new IllegalStateException(
                    "Problem establishing connection to the SMI-S Provider: " + ipAddress,
                    ex);
        }
        return connection;
    }

    /**
     * If connection is null, create a new Connection
     * 
     * @param smisIPAddress
     */
    private synchronized CimConnection addConnection(String smisIPAddress, String port) {
        CimConnection connection = null;
        try {
            connection = _connectionManager
                    .getConnection(smisIPAddress, Integer.parseInt(port));
            if (null == connection) {
                String smisAltId = smisIPAddress + "-" + port;
                List<StorageProvider> providers = CustomQueryUtility.getActiveStorageProvidersByProviderId(_dbClient, smisAltId);
                if (providers.isEmpty()) {
                    _log.error("No SMISProvider found with id {}", smisAltId);
                    return connection;
                }
                StorageProvider smisProvider = providers.get(0);
                final CimConnectionInfo connInfo = new CimConnectionInfo();
                connInfo.setHost(smisProvider.getIPAddress());
                connInfo.setPort(smisProvider.getPortNumber());
                connInfo.setUser(smisProvider.getUserName());
                connInfo.setPassword(smisProvider.getPassword());
                connInfo.setUseSSL(smisProvider.getUseSSL());
                if (smisProvider.getInterfaceType().equals(StorageProvider.InterfaceType.ibmxiv.name()) ||
                        "IBM".equals(smisProvider.getManufacturer())) {
                    connInfo.setType(CimConstants.CIM_CONNECTION_TYPE);
                    connInfo.setImplNS(CimConstants.DFLT_IBM_CIM_CONNECTION_IMPL_NS);
                }
                else {
                    connInfo.setType(CimConstants.ECOM_CONNECTION_TYPE);
                    connInfo.setImplNS(CimConstants.DFLT_CIM_CONNECTION_IMPL_NS);
                }

                connInfo.setInteropNS(CimConstants.DFLT_CIM_CONNECTION_INTEROP_NS);
                _connectionManager.addConnection(connInfo);
                connection = _connectionManager.getConnection(smisIPAddress, Integer.parseInt(port));
                _log.info("Connection Added to Cache {}", ConnectionManager.generateConnectionCacheKey(
                        smisProvider.getIPAddress(), smisProvider.getPortNumber()));
            }
        } catch (ConnectionManagerException ex) {
            _log.error("Exception occurred while adding connections due to ",
                    ex);
        } catch (Exception ex) {
            _log.error("Exception occurred while adding connections due to ",
                    ex);
        }
        return connection;
    }

    /**
     * Un-Subscribe connection for the given Passive SMIS provider
     * 
     * @param smisProviderURI {@link String} Passive SMIS Provider's URI
     * @return success flag. True means unsubscription for the given smisProvider is success, else returns false
     */
    public boolean unsubscribeSMIProviderConnection(String smisProviderURI) {
        _log.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        boolean isSuccess = false;
        try {
            _log.debug("Un-Subscribe initiated for SMIS provider :{}", smisProviderURI);
            CimConnection cimConnection = getSMISProviderConnection(smisProviderURI);
            if (null != cimConnection) {
                _connectionManager.unsubscribe(cimConnection);
                isSuccess = true;
            }
        } catch (Exception e) {
            _log.error("Un-subscription for the SMIS provider {} is failed", smisProviderURI);
            _log.error(e.getMessage(), e);
            // throw e;
        }
        _log.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        return isSuccess;
    }

    /**
     * Make subscription to the given SMIS provider for monitoring use case
     * 
     * @param smisProviderURI {@link String} Active SMIS Provider's URI for subscription
     * @return success flag. True means subscription for the given smisProvider is success, else returns false
     */
    public boolean subscribeSMIProviderConnection(String smisProviderURI) {
        _log.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        boolean isSuccess = false;
        try {
            _log.debug("smisProviderURI :{}", smisProviderURI);
            CimConnection cimConnection = getSMISProviderConnection(smisProviderURI);
            if (null != cimConnection) {
                _connectionManager.subscribe(cimConnection);
                isSuccess = true;
            }
        } catch (Exception e) {
            _log.error("subscription for the SMIS provider {} is failed", smisProviderURI);
            _log.error(e.getMessage(), e);
            // throw e;
        }
        _log.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        return isSuccess;
    }

    /**
     * Deletes stale subscriptions
     * 
     * @param smisProviderURI {@link String} SMIS Provider's URI for delete stale subscription
     * @return boolean success flag. True means delete stale subscription for the given smisProvider is success, else returns false
     */
    public boolean deleteStaleSubscriptions(String smisProviderURI) {
        _log.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        boolean isSuccess = false;
        try {
            _log.debug("smisProviderURI :{}", smisProviderURI);
            CimConnection cimConnection = getSMISProviderConnection(smisProviderURI);
            if (null != cimConnection) {
                _connectionManager.deleteStaleSubscriptions(cimConnection);
                isSuccess = true;
            }
        } catch (Exception e) {
            _log.error("Delete stale subscription for the SMIS provider {} is failed", smisProviderURI);
            _log.error(e.getMessage(), e);
            // throw e;
        }
        _log.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        return isSuccess;
    }

    /**
     * Make subscription to the given vnxfile storageSystem for monitoring use case
     * 
     * @param smisProviderURI {@link String} vnxfile StorageSystem's URI for subscription
     * @return success flag. True means subscription for the given storageSystem is success, else returns false
     */
    public boolean subscribeVnxFileForIndication(String storageSystemURI) {
        _log.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        boolean isSuccess = false;
        try {
            _log.debug("storageSystemURI :{}", storageSystemURI);
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, URI.create(storageSystemURI));
            CimConnection cimConnection = getConnection(storageDevice);
            if (null != cimConnection) {
                _connectionManager.subscribe(cimConnection);
                isSuccess = true;
            }
        } catch (Exception e) {
            _log.error("subscription for the StoargeSystem {} is failed", storageSystemURI);
            _log.error(e.getMessage(), e);
            // throw e;
        }
        _log.debug("vnx file subscription status :{}", isSuccess);
        _log.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        return isSuccess;
    }

    public boolean deleteVnxFileStaleSubscriptions(String storageSystemURI) {
        _log.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        boolean isSuccess = false;
        try {
            _log.debug("storageSystemURI :{}", storageSystemURI);
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, URI.create(storageSystemURI));
            CimConnection cimConnection = getConnection(storageDevice);
            if (null != cimConnection) {
                _connectionManager.deleteStaleSubscriptions(cimConnection);
                isSuccess = true;
            }
        } catch (Exception e) {
            _log.error("Delete stale subscription for the vnx file {} is failed", storageSystemURI);
            _log.error(e.getMessage(), e);
            // throw e;
        }
        _log.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        return isSuccess;
    }

    private CimConnection getSMISProviderConnection(String smisProviderURI) {
        CimConnection cimConnection = null;
        try {
            if (null != smisProviderURI) {
                StorageProvider smisProvider = _dbClient.queryObject(StorageProvider.class, URI.create(smisProviderURI));
                if (null != smisProvider) {
                    cimConnection = getConnection(smisProvider.getIPAddress(), smisProvider.getPortNumber().toString());
                }
            }
        } catch (final DatabaseException e) {
            _log.error("Exception occured while creating cim connection for the provider :{}", smisProviderURI);
        }

        return cimConnection;
    }

    public String getNamespace(StorageSystem storageDevice) {
        return getConnection(storageDevice).getImplNamespace();
    }

    /**
     * Determines the connection type to establish, depending upon the passed
     * storage device type. Currently the connection manager supports three
     * types of connections.
     * 
     * cim - A connection to an SMI-S CIM provider.
     * 
     * ecom - A connection to an SMI-S ECOM provider.
     * 
     * ecomCelerra - A connection to an SMI-S ECOM provider for a file storage
     * array.
     * 
     * @param storageDeviceType The type of the storage device.
     * 
     * @return The CIM connection type.
     */
    private String getConnectionTypeForDevice(String storageDeviceType) {

        if ((StorageSystem.Type.vnxblock.name().equals(storageDeviceType))
                || (StorageSystem.Type.vmax.name().equals(storageDeviceType))) {
            return CimConstants.ECOM_CONNECTION_TYPE;
        } else if (StorageSystem.Type.vnxfile.name().equals(storageDeviceType)) {
            return CimConstants.ECOM_FILE_CONNECTION_TYPE;
        } else if (StorageSystem.Type.ibmxiv.name().equals(storageDeviceType)) {
            return CimConstants.CIM_CONNECTION_TYPE;
        } else {
            _log.error("Unexpected storage device type {} for CIM event monitoring",
                    storageDeviceType);
        }

        return CimConstants.CIM_CONNECTION_TYPE;
    }

    /**
     * Determines the CIM implementation namespace for the type of device.
     * 
     * @param storageDeviceType The type of the storage device.
     * 
     * @return The CIM implementation namespace for the device.
     */
    private String getImplNamespaceForDevice(String storageDeviceType) {

        if ((StorageSystem.Type.vnxblock.name().equals(storageDeviceType))
                || (StorageSystem.Type.vmax.name().equals(storageDeviceType))) {
            return CimConstants.DFLT_CIM_CONNECTION_IMPL_NS;
        } else if (StorageSystem.Type.vnxfile.name().equals(storageDeviceType)) {
            return CimConstants.FILE_CIM_CONNECTION_IMPL_NS;
        } else if (StorageSystem.Type.ibmxiv.name().equals(storageDeviceType)) {
            return CimConstants.DFLT_IBM_CIM_CONNECTION_IMPL_NS;
        } else {
            _log.error("Unexpected storage device type {} for CIM event monitoring",
                    storageDeviceType);
        }

        return CimConstants.DFLT_CIM_CONNECTION_IMPL_NS;
    }
}
