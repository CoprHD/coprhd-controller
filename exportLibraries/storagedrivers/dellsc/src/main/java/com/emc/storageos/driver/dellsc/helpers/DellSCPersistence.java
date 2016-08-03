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
import com.emc.storageos.storagedriver.Registry;

/**
 * Handles persistence for driver data.
 */
public class DellSCPersistence {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCPersistence.class);

    private static final String HOST_KEY = "HOST";
    private static final String PORT_KEY = "KEY";
    private static final String USER_KEY = "USER";
    private static final String PASS_KEY = "PASS";

    private String driverName;
    private Registry driverRegistry;

    /**
     * Initializes the class.
     * 
     * @param driverName The driver name.
     */
    public DellSCPersistence(String driverName) {
        this.driverName = driverName;
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

        this.driverRegistry.clearDriverAttributesForKey(this.driverName, systemId);
        this.driverRegistry.setDriverAttributesForKey(this.driverName, systemId, attributes);
    }

    /**
     * Get a connection from the saved settings.
     * 
     * @param systemId The system ID of the connection.
     * @return The Storage Center API connection.
     * @throws DellSCDriverException on failure.
     */
    public StorageCenterAPI getSavedConnection(String systemId) throws DellSCDriverException {
        LOG.info("Getting saved connection information for {}", systemId);

        try {
            Map<String, List<String>> connectionInfo = this.driverRegistry.getDriverAttributesForKey(
                    this.driverName, systemId);
            return StorageCenterAPI.openConnection(
                    connectionInfo.get(HOST_KEY).get(0),
                    Integer.parseInt(connectionInfo.get(PORT_KEY).get(0)),
                    connectionInfo.get(USER_KEY).get(0),
                    connectionInfo.get(PASS_KEY).get(0));
        } catch (Exception e) {
            LOG.error(String.format("Error getting saved connection information: %s", e), e);
            throw new DellSCDriverException("Error getting saved connection information.", e);
        }
    }
}
