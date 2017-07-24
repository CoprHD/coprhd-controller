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
import com.emc.storageos.vmax.restapi.model.response.migration.CreateMigrationEnvironmentResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.GetMigrationEnvironmentResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationEnvironmentResponse;

/**
 * 
 * Update unisphereIp, userName and password attributes before executing this test class
 *
 */
public class VMAXRestClientTest {

    private static VMAXApiClient apiClient;
    private static final String unisphereIp = "lglw7150.lss.emc.com";
    private static String userName = "smc";
    private static String password = "smc";
    private static int portNumber = 8443;
    private static final String sourceArraySerialNumber = "000195702161";
    private static final String targetArraySerialNumber = "000196800794";

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
    public void craeteMigrationEnvironmentTest() throws Exception {
        assertNotNull("Api Client object is null", apiClient);
        CreateMigrationEnvironmentResponse response = apiClient.createMigrationEnvironment(sourceArraySerialNumber,
                targetArraySerialNumber);
        assertNotNull("Response object is null", response);
        assertEquals("ArrayId is not correct", targetArraySerialNumber, response.getArrayId());
        assertFalse("Invalid status", !response.isLocal());
        assertEquals("Invalid Migration session count", 1, response.getMigrationSessionCount());
    }

    @Test
    public void apiGetMigrationEnvironmentTest() throws Exception {
        assertNotNull("Api Client object is null", apiClient);

        MigrationEnvironmentResponse response = apiClient.getMigrationEnvironment(sourceArraySerialNumber, targetArraySerialNumber);
        assertEquals("Not the correct state", "OK", response.getState());
        assertEquals("Not the correct symm id", sourceArraySerialNumber, response.getSymmetrixId());
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
        GetMigrationEnvironmentResponse response = apiClient.getMigrationEnvironmentList(sourceArraySerialNumber);
        assertNotNull("Response object is null", response);
        assertNotNull("ArrayIdList object is null", response.getArrayIdList());
        assertEquals("Invalid size ", 2, response.getArrayIdList().size());

    }

    @Test
    public void deletMigrationEnvironmentTest() throws Exception {
        assertNotNull("Api Client object is null", apiClient);
        GetMigrationEnvironmentResponse response = apiClient.getMigrationEnvironmentList(sourceArraySerialNumber);
        assertNotNull("Response object is null", response);
        assertNotNull("ArrayIdList object is null", response.getArrayIdList());
        assertEquals("Invalid size ", 1, response.getArrayIdList().size());
        apiClient.deleteMigrationEnvironment(sourceArraySerialNumber, targetArraySerialNumber);

        response = apiClient.getMigrationEnvironmentList(sourceArraySerialNumber);
        assertNotNull("Response object is null", response);
        assertNotNull("ArrayIdList object is null", response.getArrayIdList());
        assertEquals("Invalid size ", 0, response.getArrayIdList().size());
    }

}
