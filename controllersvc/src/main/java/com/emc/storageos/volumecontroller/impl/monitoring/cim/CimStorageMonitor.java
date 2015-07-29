/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim;

// Logger imports
import com.emc.storageos.coordinator.client.service.WorkPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

// StorageOS imports;
import com.emc.storageos.cimadapter.connections.ConnectionManager;
import com.emc.storageos.cimadapter.connections.ConnectionManagerException;
import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.StorageMonitor;
import com.emc.storageos.volumecontroller.StorageMonitorException;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

/**
 * The CimStorageMonitor class implements the {@link StorageMonitor} interface.
 * The methods of this interface are called by the {@link BlockDeviceController
 * Block Device Controller} and the {@link FileDeviceController File Device
 * Controller} classes to add connections for storage devices that are to be
 * monitored for events and also to remove connections for storage devices that
 * are no longer to be monitored by the controller.
 */
public class CimStorageMonitor implements StorageMonitor {

    // A reference to a CIM adapter connection manager.
    private ConnectionManager _cimConnectionManager;

    // The logger.
    private static Logger s_logger = LoggerFactory.getLogger(ConnectionManager.class);

    /**
     * Default constructor for Spring bean creation.
     */
    public CimStorageMonitor() {
    }

    /**
     * Setter for the CIM adapter connection manager.
     * 
     * @param cimConnectionManager The CIM adapter connection manager.
     */
    public void setCimConnectionManager(ConnectionManager cimConnectionManager) {
        _cimConnectionManager = cimConnectionManager;
    }

    /**
     * Starts event monitoring for the passed storage device by creating a
     * connection to the SMI-S provider for the storage device.
     * 
     * @param storageDevice A reference to the storage device.
     * 
     * @throws StorageMonitorException When an error occurs monitoring the
     *             device.
     */
    @Override
    public void startMonitoring(StorageSystem storageDevice, WorkPool.Work work)
            throws StorageMonitorException {
        s_logger.debug("Connecting storage for event monitoring. {}", storageDevice.getSystemType());

        // Verify we got a non-null storage device.
        if (storageDevice == null) {
            throw new StorageMonitorException("Passed storage device is null");
        }

        s_logger.info(
                "Attempting to connect to storage provider {} for event monitoring.",
                storageDevice.getSmisProviderIP());

        // Verify the CIM connection manager reference.
        if (_cimConnectionManager == null) {
            throw new StorageMonitorException(
                    "CIM adapter connection manager reference is null.");
        }

        // Create the CIM connection info for the connection.
        CimConnectionInfo connectionInfo = new CimConnectionInfo();
        connectionInfo.setHost(storageDevice.getSmisProviderIP());
        connectionInfo.setPort(storageDevice.getSmisPortNumber());
        connectionInfo.setUser(storageDevice.getSmisUserName());
        connectionInfo.setPassword(storageDevice.getSmisPassword());
        connectionInfo.setInteropNS(CimConstants.DFLT_CIM_CONNECTION_INTEROP_NS);
        connectionInfo.setUseSSL(storageDevice.getSmisUseSSL());

        // Set the type of connection to be created.
        connectionInfo.setType(getConnectionTypeForDevice(storageDevice.getSystemType()));

        // Set the implementation namespace for this type of storage device
        connectionInfo
                .setImplNS(getImplNamespaceForDevice(storageDevice.getSystemType()));

        // Create a connection to the SMI-S provider for the storage array. Note
        // that the connection manager will only create a connection if the
        // connection is not already managed.
        try {
            _cimConnectionManager.addConnection(connectionInfo);
        } catch (ConnectionManagerException cme) {
            throw new StorageMonitorException(MessageFormatter.format(
                    "Failed attempting to establish a connection to storage provider {}.",
                    storageDevice.getSmisProviderIP()).getMessage(), cme);
        }

        s_logger.info("Connection established for storage provider {}.",
                storageDevice.getSmisProviderIP());
    }

    /**
     * Stops event monitoring for the passed storage device by removing the
     * connection to the SMI-S provider for storage device.
     * 
     * @param storageDevice A reference to the storage device.
     * 
     * @throws StorageMonitorException When an error occurs stopping monitoring
     *             for the device.
     */
    @Override
    public void stopMonitoring(StorageSystem storageDevice)
            throws StorageMonitorException {
        s_logger.debug("Disconnecting storage from event monitoring.");

        // Verify we got a non-null storage device.
        if (storageDevice == null) {
            throw new StorageMonitorException("Passed storage device is null");
        }

        s_logger.info(
                "Attempting to disconnect storage provider {} from event monitoring.",
                storageDevice.getSmisProviderIP());

        // Verify the CIM connection manager reference
        if (_cimConnectionManager == null) {
            throw new StorageMonitorException(
                    "CIM adapter connection manager reference is null.");
        }

        // Use the CIM connection manager to remove the connection. Note that
        // the connection manager will check whether or not a connection to the
        // passed provider is currently being managed.
        try {
            _cimConnectionManager.removeConnection(storageDevice.getSmisProviderIP());
        } catch (ConnectionManagerException cme) {
            throw new StorageMonitorException(MessageFormatter.format(
                    "Failed attempting to remove the connection to storage provider {}",
                    storageDevice.getSmisProviderIP()).getMessage(), cme);
        }

        s_logger.info("Connection to storage provider {} was removed.",
                storageDevice.getSmisProviderIP());
    }

    /**
     * Shuts down the storage monitor so that event monitoring is stopped for
     * all storage devices being monitored and all resources are cleaned up.
     */
    public void shutdown() {
        if (_cimConnectionManager != null) {
            try {
                _cimConnectionManager.shutdown();
            } catch (ConnectionManagerException cme) {
                s_logger.error(
                        "An exception occurred shutting down connection manager reference.",
                        cme);
            }
        } else {
            s_logger
                    .error("Failed shutting down CIM storage monitor due to null connection manager reference.");
        }
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

        if ((storageDeviceType.equals(StorageSystem.Type.vnxblock.name()))
                || (storageDeviceType.equals(StorageSystem.Type.vmax.name()))) {
            return CimConstants.ECOM_CONNECTION_TYPE;
        } else if (storageDeviceType.equals(StorageSystem.Type.vnxfile.name())) {
            return CimConstants.ECOM_FILE_CONNECTION_TYPE;
        } else {
            s_logger.error("Unexpected storage device type {} for CIM event monitoring",
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

        if ((storageDeviceType.equals(StorageSystem.Type.vnxblock.name()))
                || (storageDeviceType.equals(StorageSystem.Type.vmax.name()))) {
            return CimConstants.DFLT_CIM_CONNECTION_IMPL_NS;
        } else if (storageDeviceType.equals(StorageSystem.Type.vnxfile.name())) {
            return CimConstants.FILE_CIM_CONNECTION_IMPL_NS;
        } else {
            s_logger.error("Unexpected storage device type {} for CIM event monitoring",
                    storageDeviceType);
        }

        return CimConstants.DFLT_CIM_CONNECTION_IMPL_NS;
    }
}
