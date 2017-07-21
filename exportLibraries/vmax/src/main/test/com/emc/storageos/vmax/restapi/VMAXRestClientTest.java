/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.vmax.VMAXRestUtils;
import com.emc.storageos.vmax.restapi.errorhandling.VMAXException;
import com.emc.storageos.vmax.restapi.model.response.migration.GetMigrationEnvironmentResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationEnvironmentResponse;

/**
 * 
 * Update unisphereIp, userName and password attributes before executing this test class
 *
 */
public class VMAXRestClientTest {

    private static VMAXApiClient apiClient;
    private static final String unisphereIp = "xxxxxx";
    private static String userName = "username";
    private static String password = "password";
    private static int portNumber = 8443;
    private static final String sourceArraySerialNo = "000195701430";
    private static final String targetArraySerialNumber = "000196701405";

    @BeforeClass
    public static void setup() throws Exception {
        VMAXApiClientFactory apiClientFactory = VMAXApiClientFactory.getInstance();
        apiClientFactory.setConnectionTimeoutMs(30000);
        apiClientFactory.setConnManagerTimeout(60000);
        apiClientFactory.setMaxConnections(300);
        apiClientFactory.setMaxConnectionsPerHost(100);
        apiClientFactory.setNeedCertificateManager(true);
        apiClientFactory.setSocketConnectionTimeoutMs(3600000);

        apiClientFactory.init();
        /*
         * StorageProvider provider = new StorageProvider();
         * provider.setUseSSL(true);
         * provider.setIPAddress("lglw7150.lss.emc.com");
         * provider.setPortNumber(8443);
         */
        apiClient = (VMAXApiClient) apiClientFactory
                .getRESTClient(VMAXRestUtils.getUnisphereRestServerInfo(unisphereIp, portNumber, true), userName, password, true);
    }

    @Test
    public void apiGetMigrationEnvironmentTest() throws Exception {
        assertNotNull("Api Client object is null", apiClient);

        MigrationEnvironmentResponse response = apiClient.getMigrationEnvironment(sourceArraySerialNo, targetArraySerialNumber);
        assertEquals("Not the correct state", "OK", response.getState());
        assertEquals("Not the correct symm id", sourceArraySerialNo, response.getSymmetrixId());
        assertEquals("Not the correct other symm id", targetArraySerialNumber, response.getOtherSymmetrixId());
        assertFalse("Not the correct status", response.isInvalid());
        // fail("Not yet implemented");
    }

    @Test(expected = VMAXException.class)
    public void apiGetMigrationEnvironmentNegativeTest() throws Exception {
        assertNotNull("Api Client object is null", apiClient);
        MigrationEnvironmentResponse response = apiClient.getMigrationEnvironment("xyz", "abc");
    }

    @Test
    public void getMigrationEnvironmentTest() throws Exception {
        assertNotNull("Api Client object is null", apiClient);
        GetMigrationEnvironmentResponse response = apiClient.getMigrationEnvironmentList(sourceArraySerialNo);
        assertNotNull("Response object is null", response);
        assertNotNull("ArrayIdList object is null", response.getArrayIdList());
        assertEquals("Invalid size ", 2, response.getArrayIdList().size());

    }

}
