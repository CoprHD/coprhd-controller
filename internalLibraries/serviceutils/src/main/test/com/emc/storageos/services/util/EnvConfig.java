/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvConfig {

    private static volatile String properties_location = System.getenv("PROP_FILE_LOC");
    private static volatile Map<String, Properties> properties = null;
    private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);

    private static void readConfig(String propertyFile) throws Exception {
        Properties props = new Properties();
        if (properties_location == null) {
            properties_location = System.getProperty("user.home");
        }
        InputStream in = null;
        try {
            in = new FileInputStream(properties_location + "/" + propertyFile);
            if (in != null) {
                props.load(in);
            }

            if (properties == null) {
                properties = new ConcurrentHashMap<String, Properties>();
            }

            if (properties.get(propertyFile) == null) {
                properties.put(propertyFile, props);
            }

        } catch (FileNotFoundException e) {
            logger.error(String.format("Could not locate the file %s at %s", propertyFile, properties_location));
            logger.error(e.getMessage(), e);
            throw e;
        } catch (IOException ex) {
            logger.error(String.format("Could not read the file %s at %s", propertyFile, properties_location));
            ex.printStackTrace();
            throw ex;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                logger.error("Failed while closing inputstream");
                logger.error(e.getMessage(),e);
            }
        }
    }

    public static String get(String propertyFile, String propertyName) {
        String propertyValue = "";
        try {
            if (propertyName == null || propertyName.isEmpty()) {
            	logger.error("Property name is not supplied. Please provide a property Name");
                return "";
            }
            if (propertyFile == null || propertyFile.isEmpty()) {
            	logger.error("Property file name is not supplied. Please provide a property file name");
                return "";
            }
            if (!propertyFile.endsWith(".properties")) {
                propertyFile += ".properties";
            }
            readConfig(propertyFile);
            Properties property = properties.get(propertyFile);
            if (property != null) {
                propertyValue = property.getProperty(propertyName);
                if (propertyValue == null) {
                    logger.error(String.format("Property %s not found in the properties file %s at %s"
                            ,propertyName,propertyFile,properties_location));
                }
            } else {
                logger.error("Failed while loading property file {}",propertyFile);
            }
        } catch (Exception e) {
            logger.error(String.format("Failed while getting the property %s at %s ", propertyName, propertyFile));
            logger.error(e.getMessage(),e);
        }
        return propertyValue;
    }
}
