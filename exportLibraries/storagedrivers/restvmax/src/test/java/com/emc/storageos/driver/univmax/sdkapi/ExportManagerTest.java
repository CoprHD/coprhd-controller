/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.AuthenticationInfo;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageVolume;

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
    // String hostId = "stone_test_IG_0801112";
    // List<String> initiators = new ArrayList<>();
    // initiators.add("5848756071879150");
    //
    // CreateHostParamType param = new CreateHostParamType(hostId);
    // param.setInitiatorId(initiators);
    // GenericResultType host = exportManager.createHost(param);
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
    // String pgName = "stone_test_PG_08011";
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

    // @Test
    // public void testCreateMaskingview() {
    // String mvName = "stone_test_MV_0801";
    // String hostId = "stone_test_IG_08011";
    // String pgName = "stone_test_PG_0801";
    // String sgName = "stone_test_sg_auto_015";
    // GenericResultType result = exportManager.createMaskingviewForHost(mvName, hostId, pgName, sgName);
    // log.info("" + result.isSuccessfulStatus());
    // Assert.assertTrue(result.isSuccessfulStatus());
    // log.info("Created MV as {}", result);
    // }

    // @Test
    // public void testFetchMaskingview() {
    // String mvName = "cluster-20170302100545821_468";
    // GetMaskingViewResultType result = exportManager.fetchMaskingview(mvName);
    // log.info("" + result.isSuccessfulStatus());
    // Assert.assertTrue(result.isSuccessfulStatus());
    // log.info("Fetched IG as {}", result);
    //
    // }

    @Test
    public void testExportVolumesToInitiators() {
        String hostName = "stone_test_host";
        List<Initiator> initiators = new ArrayList<>();
        initiators.add(genInitiator(hostName));
        initiators.add(genInitiator(hostName));

        String sgName = "stone_test_sg_auto_015";
        List<StorageVolume> volumes = new ArrayList<>();
        volumes.add(genStorageVolume(sgName, "00CA9"));
        volumes.add(genStorageVolume(sgName, "00CAA"));

        List<StoragePort> recommendedPorts = new ArrayList<>();
        recommendedPorts.add(genStoragePort("FA-1D", "FA-1D:3"));
        recommendedPorts.add(genStoragePort("FA-1D", "FA-1D:4"));

        MutableBoolean usedRecommendedPorts = new MutableBoolean();
        List<StoragePort> selectedPorts = new ArrayList<>();

        DriverTask task = exportManager.exportVolumesToInitiators(initiators, volumes, null, recommendedPorts, null, null,
                usedRecommendedPorts, selectedPorts);
        Assert.assertEquals(task.getStatus(), TaskStatus.READY);
        Assert.assertEquals(selectedPorts.toString(), recommendedPorts.toString());
        Assert.assertEquals(true, usedRecommendedPorts.getValue());

    }

    private StoragePort genStoragePort(String portGroup, String nativeId) {
        StoragePort port = new StoragePort();
        port.setPortGroup(portGroup);
        port.setNativeId(nativeId);
        return port;
    }

    private StorageVolume genStorageVolume(String sgName, String nativeId) {
        StorageVolume volume = new StorageVolume();
        volume.setConsistencyGroup(sgName);
        volume.setNativeId(nativeId);
        return volume;
    }

    private Initiator genInitiator(String hostName) {
        Random random = new Random(System.currentTimeMillis());
        Initiator initiator = new Initiator();
        initiator.setHostName(hostName);
        initiator.setPort(String.format("584875607187%04d", random.nextInt(10000)));
        return initiator;
    }
}
