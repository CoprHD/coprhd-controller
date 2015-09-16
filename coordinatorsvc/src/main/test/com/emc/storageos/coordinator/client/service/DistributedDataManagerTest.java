/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;

public class DistributedDataManagerTest extends CoordinatorTestBase {
    private CoordinatorClientImpl client;
    private DistributedDataManager dataManager;
    private String basePath = "/config";
    private int maxCreationTime = 35000;

    @Before
    public void setUp() throws Exception {
        client = (CoordinatorClientImpl) connectClient();
        client.start();

    }

    @After
    public void tearDown() throws Exception {
        client.stop();
    }

    @Test
    public void testCreateDistributedDataManagerClose() throws Exception {
        boolean isExpected = true;
        try {
            int i = 0;
            while (i < maxCreationTime) {
                dataManager = client.createDistributedDataManager(basePath);
                dataManager.close();
                i++;
            }
        } catch (Exception ex) {
            isExpected = false;
        }
        Assert.assertTrue(isExpected);
    }
}
