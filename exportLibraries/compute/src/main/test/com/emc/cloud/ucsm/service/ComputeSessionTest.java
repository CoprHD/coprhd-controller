/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.cloud.platform.ucs.out.model.ComputeBlade;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.services.util.EnvConfig;

@ContextConfiguration(locations = { "classpath:applicationContext.xml" })
public class ComputeSessionTest extends AbstractTestNGSpringContextTests {
    @Autowired
    UCSMService ucsmService;

    @Autowired
    ComputeSessionManager computeSessionManager;

    @Autowired
    EncryptionProvider encryptionProvider;

    @Test(groups = "runByDefault", threadPoolSize = 100, invocationCount = 20)
    public void threadedTestGetBlades() throws ClientGeneralException,
            IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {

        String username = EnvConfig.get("sanity", "uscmServiceUser");
        String password = EnvConfig.get("sanity", "uscmServicePassword");

        List<ComputeBlade> blades = ucsmService.getComputeBlades(
                EnvConfig.get("sanity", "uscmServiceURL"), username, password);
        if (blades == null || blades.isEmpty()) {
            assert (false);
        }
        Long id = Thread.currentThread().getId();
        System.out.println("ThreadId:" + id + " getBlades Passed");
    }

    @Test(groups = "runByDefault", dependsOnMethods = "threadedTestGetBlades")
    public void testClearSession() throws ClientGeneralException,
            IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        String username = EnvConfig.get("sanity", "uscmServiceUser");
        String password = EnvConfig.get("sanity", "uscmServicePassword");
        ucsmService.clearDeviceSession(
                EnvConfig.get("sanity", "uscmServiceURL"), username, password);
    }

    @Test(groups = "runByDefault")
    public void testClearSessionOfUnknownDevice()
            throws ClientGeneralException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        ucsmService.clearDeviceSession(
                EnvConfig.get("sanity", "ucsmService.unknownURL"),
                EnvConfig.get("sanity", "uscmServiceUser"),
                EnvConfig.get("sanity", "uscmServicePassword"));
    }

}
