/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ClientConfig {
    private static ClientConfig INSTANCE;

    public static String MOUNTPOINT_LIST = "mountPoint.list";
    public static String ISCSIID_LIST = "iSCSCIId.list";
    public static String KEYSTORE_PASSWORD = "keyStorePassword";  // NOSONAR ("retaining hard-coded password")
    public static String KEYSTORE_PATH = "keyStoreFilePath";
    public static String CERT_ALIAS = "certAlias";

    private static final String CONFIG_FILE = "ClientConfig.properties";
    public static final String SERVICE_HOST = "serviceHost";
    public static final String USERNAME = "username"; // NOSONAR ("retaining hard-coded password")
    public static final String PASSWORD = "password"; // NOSONAR ("retaining hard-coded password")
    private Properties properties;

    private ClientConfig() throws IOException {
        properties = loadPropertiesFromFile();
    }

    private Properties loadPropertiesFromFile() throws IOException {

        Properties prop = new Properties();

        InputStream in = ClassLoader.getSystemClassLoader()
                .getResourceAsStream(CONFIG_FILE);
        try {
            if (in == null) {
                throw new IOException("Cannot load " + CONFIG_FILE);
            }
            prop = new Properties();
            prop.load(in);

            return prop;

        } catch (IOException e1) {
            throw e1;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw e;
                }
            }
        }
    }

    public synchronized static ClientConfig getInstance() throws IOException {
        if (INSTANCE == null) {
            INSTANCE = new ClientConfig();
        }

        return INSTANCE;
    }

    public Properties getProperties() {
        return this.properties;
    }

}
