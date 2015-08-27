/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.auth;


import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.password.Constants;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to interact with sytem properties stored in zookeeper
 */
public class SystemPropertyUtil {

    private static final Logger _log = LoggerFactory.getLogger(SystemPropertyUtil.class);

    public static final int DEFAULT_LDAP_CONNECTION_TIMEOUT_IN_SECS = 10;


    /**
     * retrieve value from system properties of ZooKeeper.
     * if the properties do not exist, or exception when loading, return null.
     */
    private static String retreiveSystemProperty(CoordinatorClient coordinator,
                                                String propertyName) {
        try {
            PropertyInfoExt params = coordinator.getTargetInfo(PropertyInfoExt.class);
            String propertyValue = params.getProperty(propertyName);
            _log.info("retrieve property: " + propertyName + " = " + propertyValue);
            return propertyValue;
        } catch (Exception e) {
            _log.warn("retrieve property: " + propertyName + " from ZK error");
            return null;
        }
    }


    /**
     * retrieve value of ldap_connection_timeout from system properties of ZooKeeper.
     * if the property dese not exist, or exception when loading, use default value 10.
     */
    public static int getLdapConnectionTimeout(CoordinatorClient coordinatorClient) {
        String strTimeout = retreiveSystemProperty(coordinatorClient,
                Constants.LDAP_CONNECTION_TIMEOUT);
        return NumberUtils.toInt(strTimeout, DEFAULT_LDAP_CONNECTION_TIMEOUT_IN_SECS);
    }

}
