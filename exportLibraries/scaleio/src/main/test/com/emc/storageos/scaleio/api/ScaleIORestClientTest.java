/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.scaleio.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSDS;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOScsiInitiator;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;
import com.emc.storageos.services.util.EnvConfig;

public class ScaleIORestClientTest {
    private static Logger log = LoggerFactory.getLogger(ScaleIORestClientTest.class);
    private static ScaleIORestClient restClient;
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String HOST = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.ipaddress");
    private static final String USER = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.user");
    private static final String PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.password");
    private static int PORT = 443;

    @BeforeClass
    static public void setUp() {
        ScaleIORestClientFactory factory = new ScaleIORestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = ScaleIOConstants.getAPIBaseURI(HOST, PORT);
        restClient = (ScaleIORestClient) factory.getRESTClient(URI.create(endpoint), USER, PASSWORD, true);

    }

    // @Test
    public void testGetVersion() {
        String result = null;
        try {
            result = restClient.getVersion();
        } catch (Exception e) {
            log.error("Exception: ", e);
        }

        System.out.printf("version %s", result);
    }

    // @Test
    public void testQuerySDS() {
        List<ScaleIOSDS> result = null;
        try {
            result = restClient.queryAllSDS();
            for (ScaleIOSDS sds : result) {
                String sdsId = sds.getId();
                String sdsPort = sds.getPort();
                String sdsIp = sds.getIpList().get(0).getIp();
                System.out.printf("Sds id : %s  port : %s  IP: %s %n", sdsId, sdsPort, sdsIp);

            }
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testQueryAllScsiInitiators() {

        try {
            List<ScaleIOScsiInitiator> result = restClient.queryAllSCSIInitiators();
            if (result != null && !result.isEmpty()) {
                System.out.println("Has initiators");
                for (ScaleIOScsiInitiator init : result) {
                    System.out.println(init.getIqn());
                }
            } else {
                System.out.println("no initiators");

            }
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testAddVolume() {
        try {
            ScaleIOVolume result = restClient.addVolume("a", "d924dfbf00000002", "volTest3", "1073741824", true);
            System.out.printf("created volume id: %s", result.getId());
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testRemoveVolume() {
        try {
            restClient.removeVolume("537b42c60000002a");
            System.out.println("removed.");
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testExpandVolume() {
        try {
            String volId = "537b69d70000003c";
            restClient.modifyVolumeCapacity(volId, "16");
            ScaleIOVolume vol = restClient.queryVolume(volId);
            String size = vol.getSizeInKb();
            Long sizeInGB = Long.parseLong(size) / 1024L / 1024L;
            System.out.println("size is :" + sizeInGB.toString());
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testMapVolumeToSDC() {
        try {
            String sdcId = "6de2bbb700000001";
            String volId = "537b69d70000003c";
            restClient.mapVolumeToSDC(volId, sdcId);
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testUnmapVolumeToSDC() {
        try {
            String sdcId = "6de2bbb700000001";
            String volId = "537b69d70000003c";
            restClient.unMapVolumeToSDC(volId, sdcId);
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testGetVolumes() {
        try {
            List<String> volIds = new ArrayList<String>();
            volIds.add("537b42d80000002f");
            volIds.add("537b69d70000003c");
            Map<String, String> vols = restClient.getVolumes(volIds);
            for (Map.Entry<String, String> entry : vols.entrySet()) {
                System.out.println("name : " + entry.getKey() + "id: " + entry.getValue());
            }

        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testSnapVolumes() {
        try {
            Map<String, String> map = new HashMap<String, String>();
            map.put("537b42d80000002f", "test123snap");
            map.put("537b69d70000003c", "volTest2Snap");
            restClient.snapshotMultiVolume(map, restClient.getSystemId());

        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    // @Test
    public void testRemoveCGSnaps() {
        try {
            restClient.removeConsistencyGroupSnapshot("ae9c0e7c00000002");
            System.out.println("removed");

        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }
}
