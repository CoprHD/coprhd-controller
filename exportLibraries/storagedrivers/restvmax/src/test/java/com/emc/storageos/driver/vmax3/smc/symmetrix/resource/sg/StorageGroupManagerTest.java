/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.manager.ManagerFactory;

/**
 * @author fengs5
 *
 */
public class StorageGroupManagerTest {
    private static final Logger LOG = LoggerFactory.getLogger(StorageGroupManagerTest.class);
    static ManagerFactory managerFacory;
    static StorageGroupManager sgManager;

    @BeforeClass
    public static void setup() {
        String host = "10.247.97.150";
        int port = 8443;
        String user = "smc";
        String password = "smc";
        String sn = "000196801468";

        AuthenticationInfo authenticationInfo = new AuthenticationInfo(host, port, user, password);
        authenticationInfo.setSn(sn);
        managerFacory = new ManagerFactory(authenticationInfo);
        sgManager = managerFacory.genStorageGroupManager();
    }

    @Test
    public void testCreateEmptySg() {

        String sgName = "stone_test_sg_auto_002";
        CreateStorageGroupParameter param = new CreateStorageGroupParameter(sgName);
        param.setCreateEmptyStorageGroup(true);
        param.setEmulation("FBA");
        param.setSrpId("SRP_1");
        String[] urlFillers = { sgManager.getAuthenticationInfo().getSn() };
        Assert.assertNotNull(sgManager.createEmptySg(param, urlFillers));
    }

}
