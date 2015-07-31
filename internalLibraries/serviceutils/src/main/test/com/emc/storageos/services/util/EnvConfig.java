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

public class EnvConfig {

    private static String properties_location = System.getenv("PROP_FILE_LOC");
    private static Map<String, Properties> properties = null;

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
            System.out.println("Could not locate the file " + propertyFile + " at " + properties_location);
            e.printStackTrace();
            throw e;
        } catch (IOException ex) {
            System.out.println("Could not read the file " + propertyFile + " at " + properties_location);
            ex.printStackTrace();
            throw ex;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                System.out.println("Failed while closing inputstream");
                e.printStackTrace();
            }
        }
    }

    public static String get(String propertyFile, String propertyName) {
        String propertyValue = "";
        try {
            if (propertyName == null || propertyName.isEmpty()) {
                System.out.println("Property name is not supplied. Please provide a property Name");
                return "";
            }
            if (propertyFile == null || propertyFile.isEmpty()) {
                System.out.println("Property file name is not supplied. Please provide a property file name");
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
                    System.out.println("Property " + propertyName + " not found in the properties file "
                            + propertyFile + " at " + properties_location);
                }
            } else {
                System.out.println("Failed while loading property file " + propertyFile);
            }
        } catch (Exception e) {
            System.out.println("Failed while getting the property " + propertyName + " at " + propertyFile);
            e.printStackTrace();
        }
        return propertyValue;
    }
}
