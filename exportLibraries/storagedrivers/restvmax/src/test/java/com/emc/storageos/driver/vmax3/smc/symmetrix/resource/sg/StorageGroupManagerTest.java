/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.ManagerFactory;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;

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
        String host = "lglw7150.lss.emc.com";
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

        String sgName = "stone_test_sg_auto_003";
        CreateStorageGroupParameter param = new CreateStorageGroupParameter(sgName);
        param.setCreateEmptyStorageGroup(true);
        param.setEmulation("FBA");
        param.setSrpId("SRP_1");
        List<String> urlFillers = new ArrayList<String>();
        urlFillers.add(sgManager.getAuthenticationInfo().getSn());
        Assert.assertTrue(sgManager.createEmptySg(param, urlFillers).isSuccessfulStatus());
    }

}
