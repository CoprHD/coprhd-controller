/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.cloudarray.api.restapi;

import java.net.URI;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.cloudarray.api.restapi.response.CloudArrayCache;
import com.emc.storageos.services.util.EnvConfig;

public class CloudArrayRestClientTest {
    private static Logger log = LoggerFactory.getLogger(CloudArrayRestClientTest.class);
    private static CloudArrayRestClient restClient;
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String HOST = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "cloudarray.host.api.ipaddress");
    private static final String USER = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "cloudarray.host.api.user");
    private static final String PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "cloudarray.host.api.password");
    private static final String PORT = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "cloudarray.host.api.port");

    @BeforeClass
    static public void setUp() {
        CloudArrayRestClientFactory factory = new CloudArrayRestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = CloudArrayConstants.getAPIBaseURI(HOST, PORT);
        restClient = (CloudArrayRestClient) factory.getRESTClient(URI.create(endpoint), USER, PASSWORD, true);
    }

    @Test
    public void testGetCaches() {
        try {
            List<CloudArrayCache> caches = restClient.getCloudArrayCaches();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
