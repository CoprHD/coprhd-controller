/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.vmax.restapi.errorhandling.VMAXException;
import com.emc.storageos.vmax.restapi.model.response.migration.CreateMigrationEnvironmentResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.GetMigrationEnvironmentResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.GetMigrationStorageGroupListResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.GetMigrationStorageGroupResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationEnvironmentResponse;

/**
 * 
 * Update unisphereIp, userName and password attributes before executing this test class
 *
 */
public class VMAXApiClientTest {

    private static VMAXApiClient apiClient;
    private static final String unisphereIp = "lglw7150.lss.emc.com";
    private static String userName = "smc";
    private static String password = "smc";
    private static int portNumber = 8443;
    private static final String sourceArraySerialNumber = "000195702161";
    private static final String targetArraySerialNumber = "000196800794";
    private static final String SG_NAME = "test_mig_161";
    private static final String version = "8.4.0.10";
    private static final Set<String> localSystems = new HashSet<>(Arrays.asList("000196701343", "000196801612", "000197000197",
            "000197000143", "000196800794", "000196801468"));

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
         * apiClient = (VMAXApiClient) apiClientFactory
         * .getRESTClient(VMAXRestUtils.getUnisphereRestServerInfo(unisphereIp, portNumber, true), userName, password, true);
         */
        apiClient = apiClientFactory.getClient(unisphereIp, portNumber, true, userName, password);
        assertNotNull("Api Client object is null", apiClient);
    }

    @Test
    public void getApiVersionTest() throws Exception {
        assertEquals(version, apiClient.getApiVersion());
    }

    @Test
    public void getLocalSystemsTest() throws Exception {
        assertEquals(localSystems, apiClient.getLocalSystems());
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
    public void deleteMigrationEnvironmentTest() throws Exception {
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

    @Test
    public void getAllMigrationStorageGroupsTest() throws Exception {
        assertNotNull("Api Client object is null", apiClient);
        GetMigrationStorageGroupListResponse getMigrationStorageGroupListResponse = apiClient
                .getMigrationStorageGroups(sourceArraySerialNumber);
        assertNotNull("Response object is null", getMigrationStorageGroupListResponse);
        assertNotNull("getNameList object is null", getMigrationStorageGroupListResponse.getNameList());
        assertEquals("Name List size should be greater than zero", true, getMigrationStorageGroupListResponse.getNameList().size() > 0);
    }

    @Test
    public void getMigrationStorageGroupTest() throws Exception {
        assertNotNull("Api Client object is null", apiClient);
        GetMigrationStorageGroupResponse getMigrationStorageGroupResponse = apiClient.getMigrationStorageGroup(sourceArraySerialNumber,
                SG_NAME);
        assertNotNull("Response object is null", getMigrationStorageGroupResponse);
        assertEquals("Invalid sourceArray response", sourceArraySerialNumber, getMigrationStorageGroupResponse.getSourceArray());
        assertEquals("Invalid targetArray response", targetArraySerialNumber, getMigrationStorageGroupResponse.getTargetArray());
        assertEquals("Invalid storageGroup response", SG_NAME, getMigrationStorageGroupResponse.getStorageGroup());
        assertEquals("Invalid state response", "CutoverReady", getMigrationStorageGroupResponse.getState());
        assertEquals("Invalid totalCapacity response", 1.0, getMigrationStorageGroupResponse.getTotalCapacity(), 1);
        assertNotNull("Device Pair object is null", getMigrationStorageGroupResponse.getDevicePairs());
        assertEquals("Device Pair List size should be greater than zero", true,
                getMigrationStorageGroupResponse.getDevicePairs().size() > 0);

        assertNotNull("Source Masking View list object is null", getMigrationStorageGroupResponse.getSourceMaskingViewList());
        assertEquals("Source Masking View List size should be greater than zero", true,
                getMigrationStorageGroupResponse.getSourceMaskingViewList().size() > 0);
        assertNotNull("Target Masking View list object is null", getMigrationStorageGroupResponse.getTargetMaskingViewList());
        assertEquals("Target Masking View List size should be greater than zero", true,
                getMigrationStorageGroupResponse.getTargetMaskingViewList().size() > 0);

    }

}
