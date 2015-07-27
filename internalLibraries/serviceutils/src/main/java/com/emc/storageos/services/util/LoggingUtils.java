/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import java.net.URL;

import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class LoggingUtils {
    /**
     * Configures log4j using the named resource from the classpath if a
     * log4j.configuration property is not already set.
     * 
     * @param resourceName
     *        the name of the resource to use if logging is not configured.
     */
    public static void configureIfNecessary(String resourceName) {
        // Only configure logging if a log4j.configuration was not specified in
        // the environment
        if (System.getProperty("log4j.configuration") == null) {
            URL confUrl = Thread.currentThread().getContextClassLoader().getResource(resourceName);
            if (confUrl != null) {
                LogManager.resetConfiguration();
                PropertyConfigurator.configure(confUrl);
                java.util.logging.LogManager.getLogManager().reset();
                SLF4JBridgeHandler.install();
            }
        }
    }
}
