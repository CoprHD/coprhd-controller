/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.DellSCDriverException;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.StorageCenter;
import com.emc.storageos.storagedriver.Registry;

/**
 * Handles persistence for driver data.
 */
public class DellSCConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCConnectionManager.class);

    private static final String DRIVER_NAME = "dellscsystem";
    private static final String HOST_KEY = "HOST";
    private static final String PORT_KEY = "PORT";
    private static final String USER_KEY = "USER";
    private static final String PASS_KEY = "PASS";
    private static final Object lockObj = new Object();

    private static DellSCConnectionManager instance;

    Map<String, StorageCenterAPI> connectionMap;
    Map<String, String> systemLookup;
    private Registry driverRegistry;

    /**
     * Private constructor.
     */
    private DellSCConnectionManager() {
        connectionMap = new HashMap<>();
        systemLookup = new HashMap<>();
    }

    /**
     * Get the instance.
     * 
     * @return The DellSCConnectionManager instance.
     */
    public static DellSCConnectionManager getInstance() {
        synchronized (lockObj) {
            if (instance == null) {
                instance = new DellSCConnectionManager();
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
     * Saves connection information to the registry.
     * 
     * @param systemId The identifier for this entry.
     * @param host The host name or IP.
     * @param port The connection port.
     * @param user The connection user name.
     * @param password The connection password.
     */
    public void saveConnectionInfo(String systemId, String host, int port, String user, String password) {
        LOG.info("Saving connection information for {} - {}:{}", systemId, host, port);

        List<String> listHost = new ArrayList<>();
        List<String> listPort = new ArrayList<>();
        List<String> listUser = new ArrayList<>();
        List<String> listPass = new ArrayList<>();
        listHost.add(host);
        listPort.add(Integer.toString(port));
        listUser.add(user);
        listPass.add(password);

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(HOST_KEY, listHost);
        attributes.put(PORT_KEY, listPort);
        attributes.put(USER_KEY, listUser);
        attributes.put(PASS_KEY, listPass);

        this.driverRegistry.clearDriverAttributesForKey(DRIVER_NAME, systemId);
        this.driverRegistry.setDriverAttributesForKey(DRIVER_NAME, systemId, attributes);
    }

    /**
     * Get a connection from the saved settings.
     * 
     * @param systemId The system ID of the connection.
     * @return The Storage Center API connection.
     * @throws DellSCDriverException on failure.
     */
    public StorageCenterAPI getConnection(String systemId) throws DellSCDriverException {
        LOG.info("Getting saved connection information for {}", systemId);

        String key = systemLookup.get(systemId);
        if (key == null) {
            // Old connection information
            key = systemId;
        }

        try {
            Map<String, List<String>> connectionInfo = this.driverRegistry.getDriverAttributesForKey(
                    DRIVER_NAME, key);
            return getConnection(
                    connectionInfo.get(HOST_KEY).get(0),
                    Integer.parseInt(connectionInfo.get(PORT_KEY).get(0)),
                    connectionInfo.get(USER_KEY).get(0),
                    connectionInfo.get(PASS_KEY).get(0), false);
        } catch (Exception e) {
            LOG.error(String.format("Error getting saved connection information: %s", e), e);
            throw new DellSCDriverException("Error getting saved connection information.", e);
        }
    }

    /**
     * Gets a new Storage Center API connection.
     * 
     * @param host The host name or IP.
     * @param port The connection port.
     * @param user The connection user name.
     * @param password The connection password.
     * @param isProvider The isProvider flag to indicate the provider call
     * @return The API connection.
     * @throws StorageCenterAPIException
     */
    @SuppressWarnings("resource")
    public StorageCenterAPI getConnection(String host, int port, String user, String password, boolean isProvider) throws StorageCenterAPIException {
        // First see if we already have a connection for this system
        StorageCenterAPI result = connectionMap.get(host);

        if (result == null) {
            result = StorageCenterAPI.openConnection(
                    host,
                    port,
                    user,
                    password);
            StorageCenter[] scs = result.getStorageCenterInfo();
            for (StorageCenter sc : scs) {
                // Saving per system for backward compatibility
                saveConnectionInfo(sc.scSerialNumber, host, port, user, password);
                systemLookup.put(sc.scSerialNumber, host);
            }
            saveConnectionInfo(host, host, port, user, password);
            connectionMap.put(host, result);
        } else {
            // Make sure our connection is still good
            result = validateConnection(result, result.getHost());
            
            /*
             * Update the SC Connection Info only during storage provider discovery
             * bugfix-COP-25081-dell-sc-fail-to-recognize-new-arrays
             */
            if(isProvider) {
            	StorageCenter[] scs = result.getStorageCenterInfo();
            	for (StorageCenter sc : scs) {
            		// Saving per system for backward compatibility
            		saveConnectionInfo(sc.scSerialNumber, host, port, user, password);
            		systemLookup.put(sc.scSerialNumber, host);
            	}
            }
            
        }

        return result;
    }

    /**
     * Validate that an API connection is still good.
     *
     * @param api The API connection.
     * @param key The connection key.
     * @return The validated API connection.
     * @throws StorageCenterAPIException
     */
    private StorageCenterAPI validateConnection(StorageCenterAPI api, String key) throws StorageCenterAPIException {
        StorageCenterAPI result = api;

        try {
            // Make a call to validate the connection is still good.
            result.getApiConnection();
        } catch (StorageCenterAPIException e) {
            // Something is wrong with that connection, create new one
            LOG.warn(String.format("Connection failed, attempting to reconnection: %s", e));
            Map<String, List<String>> connectionInfo = this.driverRegistry.getDriverAttributesForKey(
                    DRIVER_NAME, key);
            int port = 3033;
            try {
                port = Integer.parseInt(connectionInfo.get(PORT_KEY).get(0));
            } catch (NumberFormatException nex) {
                LOG.warn(String.format("Invalid port setting: %s", connectionInfo.get(PORT_KEY).get(0)));
            }
            result = StorageCenterAPI.openConnection(
                    connectionInfo.get(HOST_KEY).get(0),
                    port,
                    connectionInfo.get(USER_KEY).get(0),
                    connectionInfo.get(PASS_KEY).get(0));
            connectionMap.put(api.getHost(), result);
        }

        return result;
    }
}
