/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.AuthenticationInfo;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreateHostParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.HostType;

/**
 * @author fengs5
 *
 */
public class ExportManagerTest {
    private static final Logger log = LoggerFactory.getLogger(ExportManagerTest.class);
    static ExportManager exportManager = new ExportManager(null, null);

    @BeforeClass
    public static void setup() {
        String protocol = "https";
        String host = "lglw7150.lss.emc.com";
        int port = 8443;
        String user = "smc";
        String password = "smc";
        String sn = "000196801468";

        AuthenticationInfo authenticationInfo = new AuthenticationInfo(protocol, host, port, user, password);
        authenticationInfo.setSn(sn);
        RestClient client = new RestClient(authenticationInfo.getProtocol(), authenticationInfo.getHost(), authenticationInfo.getPort(),
                authenticationInfo.getUserName(),
                authenticationInfo.getPassword());
        exportManager.setAuthenticationInfo(authenticationInfo);
        exportManager.setClient(client);
    }

    @Test
    public void testCreateHost() {
        String hostId = "stone_test_IG_0801";
        List<String> initiators = new ArrayList<>();
        initiators.add("5848756071879150");

        CreateHostParamType param = new CreateHostParamType(hostId);
        HostType host = exportManager.createHost(param);
        Assert.assertTrue(host.isSuccessfulStatus());
        log.info("Created IG as {}", host);

    }
}
