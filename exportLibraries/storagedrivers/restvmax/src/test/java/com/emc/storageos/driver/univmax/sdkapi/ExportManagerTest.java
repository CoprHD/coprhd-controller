/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.AuthenticationInfo;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.type.common.GenericResultType;

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

    // @Test
    // public void testCreateHost() {
    // String hostId = "stone_test_IG_0801";
    // List<String> initiators = new ArrayList<>();
    // initiators.add("5848756071879150");
    //
    // CreateHostParamType param = new CreateHostParamType(hostId);
    // HostType host = exportManager.createHost(param);
    // log.info("" + host.isSuccessfulStatus());
    // Assert.assertTrue(host.isSuccessfulStatus());
    // log.info("Created IG as {}", host);
    //
    // }

    // @Test
    // public void testFetchHost() {
    // String hostId = "stone_test_IG_08011";
    // GetHostResultType getHostResultType = exportManager.fetchHost(hostId);
    // log.info("" + getHostResultType.isSuccessfulStatus());
    // Assert.assertTrue(getHostResultType.isSuccessfulStatus());
    // log.info("Fetched IG as {}", getHostResultType);
    // }

    // @Test
    // public void testCreatePortGroup() {
    // String pgName = "stone_test_PG_0801";
    // String directorId = "FA-1D";
    // String portId = "4";
    // CreatePortGroupParamType param = new CreatePortGroupParamType(pgName);
    // SymmetrixPortKeyType port = new SymmetrixPortKeyType(directorId, portId);
    // param.addSymmetrixPortKey(port);
    // GenericResultType result = exportManager.createPortGroup(param);
    // log.info("" + result.isSuccessfulStatus());
    // Assert.assertTrue(result.isSuccessfulStatus());
    // log.info("Created PG as {}", result);
    //
    // }

    // @Test
    // public void testFetchPortGroup() {
    // String pgName = "stone_test_PG_0801";
    // GetPortGroupResultType result = exportManager.fetchPortGroup(pgName);
    // log.info("" + result.isSuccessfulStatus());
    // Assert.assertTrue(result.isSuccessfulStatus());
    // log.info("Fetched PG as {}", result);
    // }

    @Test
    public void testCreateMaskingview() {
        String mvName = "stone_test_MV_0801";
        String hostId = "stone_test_IG_08011";
        String pgName = "stone_test_PG_0801";
        String sgName = "stone_test_sg_auto_015";
        GenericResultType result = exportManager.createMaskingviewForHost(mvName, hostId, pgName, sgName);
        log.info("" + result.isSuccessfulStatus());
        Assert.assertTrue(result.isSuccessfulStatus());
        log.info("Created MV as {}", result);
    }

    // @Test
    // public void testFetchMaskingview() {
    // String mvName = "cluster-20170302100545821_468";
    // GetMaskingViewResultType result = exportManager.fetchMaskingview(mvName);
    // log.info("" + result.isSuccessfulStatus());
    // Assert.assertTrue(result.isSuccessfulStatus());
    // log.info("Fetched IG as {}", result);
    //
    // }
}
