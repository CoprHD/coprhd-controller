/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.smc.basetype.AuthenticationInfo;
import com.emc.storageos.storagedriver.Registry;

/**
 * @author fengs5
 *
 */
public class RegistryHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RegistryHandler.class);

    private static final String REGISTRY_ATTRIBUTE_NAME_PROTOCOL = "protocol";
    private static final String REGISTRY_ATTRIBUTE_NAME_HOST = "host";
    private static final String REGISTRY_ATTRIBUTE_NAME_PORT = "port";
    private static final String REGISTRY_ATTRIBUTE_NAME_USERNAME = "username";
    private static final String REGISTRY_ATTRIBUTE_NAME_PASSWORD = "password";

    private Registry registry;

    public RegistryHandler(Registry registry) {
        this.registry = registry;
    }

    public void setAccessInfo(String arrayId, String protocol, String host, Integer port, String username,
            String password) {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> schemeList = new ArrayList<>();
        List<String> portList = new ArrayList<>();
        List<String> hostList = new ArrayList<>();
        List<String> usernameList = new ArrayList<>();
        List<String> passwordList = new ArrayList<>();
        schemeList.add(protocol);
        attributes.put(REGISTRY_ATTRIBUTE_NAME_PROTOCOL, schemeList);
        hostList.add(host);
        attributes.put(REGISTRY_ATTRIBUTE_NAME_HOST, hostList);
        portList.add(Integer.toString(port));
        attributes.put(REGISTRY_ATTRIBUTE_NAME_PORT, portList);
        usernameList.add(username);
        attributes.put(REGISTRY_ATTRIBUTE_NAME_USERNAME, usernameList);
        passwordList.add(password);
        attributes.put(REGISTRY_ATTRIBUTE_NAME_PASSWORD, passwordList);
        this.registry.setDriverAttributesForKey(SymConstants.DRIVER_NAME, getAccessInfoKey(arrayId), attributes);
    }

    /**
     * Retrieve the access information from the Registry. If cannot get the access information
     * from the Registry, return null.
     *
     * @param arrayId
     * @return
     */
    public AuthenticationInfo getAccessInfo(String arrayId) {
        Map<String, List<String>> connectionInfo = this.registry.getDriverAttributesForKey(
                SymConstants.DRIVER_NAME, getAccessInfoKey(arrayId));
        if (connectionInfo.isEmpty()) {
            return null;
        }
        String protocol = connectionInfo.get(REGISTRY_ATTRIBUTE_NAME_PROTOCOL).get(0);
        String host = connectionInfo.get(REGISTRY_ATTRIBUTE_NAME_HOST).get(0);
        Integer port = Integer.valueOf(connectionInfo.get(REGISTRY_ATTRIBUTE_NAME_PORT).get(0));
        String username = connectionInfo.get(REGISTRY_ATTRIBUTE_NAME_USERNAME).get(0);
        String password = connectionInfo.get(REGISTRY_ATTRIBUTE_NAME_PASSWORD).get(0);
        AuthenticationInfo result = new AuthenticationInfo(protocol, host, port, username, password);
        result.setSn(arrayId);
        return result;
    }

    /**
     * Composite the access information key for the given array.
     *
     * @param arrayId
     * @return A string like "Array '43432441341' Access Information"
     */
    private String getAccessInfoKey(String arrayId) {
        return String.format("Array '%s' Access Information", arrayId);
    }
}
