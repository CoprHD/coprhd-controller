/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.scaleio.api;

import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIODevice;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOFaultSet;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOProtectionDomain;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSDC;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSDS;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOScsiInitiator;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOStoragePool;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSystem;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;
import com.emc.storageos.services.util.EnvConfig;
import java.net.URI;
import static java.text.MessageFormat.format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            Assert.fail();
        }
    }

    // @Test
    public void testQuerySDSdetails() {
        List<ScaleIOSDS> result = null;
        Map<String,ScaleIOProtectionDomain> pdMap = null;
        Map<String,ScaleIOFaultSet> fsMap = null;
        Map<String,ScaleIOStoragePool> spMap = null;
        try {
            result = restClient.queryAllSDS();
            pdMap = restClient.getProtectionDomains().stream().collect(
                    Collectors.toMap(ScaleIOProtectionDomain::getId,p->p));
            List<ScaleIOFaultSet> fsList = restClient.queryAllFaultSets();
            if (null != fsList) {
                fsMap = restClient.queryAllFaultSets().stream().collect(
                        Collectors.toMap(ScaleIOFaultSet::getId, f -> f));
            }
            spMap = restClient.queryAllStoragePools().stream().collect(
                    Collectors.toMap(ScaleIOStoragePool::getId,s->s));

            for (ScaleIOSDS sds : result) {

                List<ScaleIODevice> devices = restClient.getSdsDevices(sds.getId());

                String sdsId = sds.getId();
                String sdsPort = sds.getPort();
                String sdsIp = sds.getIpList().get(0).getIp();
                String sdsPd = null;
                if (null != pdMap) {
                    sdsPd = pdMap.get(sds.getProtectionDomainId()).getName();
                }
                String sdsFs = null;
                if (null != fsMap) {
                    sdsFs = fsMap.get(sds.getFaultSetId()).getName();
                }
                //Gson gson = new GsonBuilder().setPrettyPrinting().create();
                //System.out.println(gson.toJson(sds));
                System.out.printf("Sds id : %s  port : %s  IP: %s  PD: %s FS: %s %n", sdsId, sdsPort, sdsIp,sdsPd,sdsFs);
                for(ScaleIODevice device:devices) {
                    String spName = spMap.get(device.getStoragePoolId()).getName();
                    System.out.printf("\t Storage Pool name: %s device: %s %n",spName,device.getDeviceCurrentPathName());
                }

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
            Assert.fail();
        }
    }

    @Test
    public void testQuerySystemName() {

        try {
            ScaleIOSystem result = restClient.getSystem();
            System.out.println("ScaleIO System Name "+ result.getName());
            System.out.println("ScaleIO Primary MDM IPs ");
            for (String ip:result.getMdmCluster().getMaster().getManagementIPs()){
                System.out.println(ip);
            }

        } catch (Exception e) {
            log.error("Exception: ", e);
            Assert.fail();
        }
    }

    @Test
    public void testQuerySdc() {

        try {
            List<ScaleIOSDC> result = restClient.queryAllSDC();
            System.out.println("ScaleIO SDC GUIDs");
            for (ScaleIOSDC sdc : result) {
                System.out.println(format(
                        "\tName: {0}, IP: {1}, State: {2}, GUID: {3}",
                        sdc.getName(), sdc.getSdcIp(), sdc.getMdmConnectionState(), sdc.getSdcGuid()
                ));
            }

        } catch (Exception e) {
            log.error("Exception: ", e);
            Assert.fail();
        }
    }

    @Test
    public void testQueryAllStoragePools() {

        try {
            List<ScaleIOStoragePool> result = restClient.queryAllStoragePools();
            for (ScaleIOStoragePool pool : result) {
                String poolId = pool.getId();
                String poolProtectionDomainId = pool.getProtectionDomainId();
                String poolName = pool.getName();
                System.out.printf("Pool id : %s  name : %s  Protection Domain: %s %n", poolId, poolName, poolProtectionDomainId);

            }

        } catch (Exception e) {
            log.error("Exception: ", e);
            Assert.fail();
        }
    }

    @Test
    public void testQueryAllFaultSets() {

        try {
            List<ScaleIOFaultSet> result = restClient.queryAllFaultSets();
            if (null != result) {
                for (ScaleIOFaultSet faultSet : result) {
                    String faultSetId = faultSet.getId();
                    String faultSetProtectionDomain = faultSet.getProtectionDomain();
                    String faultSetName = faultSet.getName();
                    System.out.printf("Fault Set id : %s  name : %s  Protection Domain: %s %n", faultSetId, faultSetName, faultSetProtectionDomain);

                }
            } else {
                System.out.println("No Fault Sets to test");
            }

        } catch (Exception e) {
            log.error("Exception: ", e);
            Assert.fail();
        }
    }

    @Test
    public void testGetSdsDevices() {

        try {
            List<ScaleIOSDS> result = restClient.queryAllSDS();
            for (ScaleIOSDS sds : result) {
                List<ScaleIODevice> sdsDevices = restClient.getSdsDevices(sds.getId());
                String sdsId = sds.getId();
                String sdsName = sds.getName();
                System.out.printf("SDS id : %s  name : %s %n", sdsId, sdsName);
                for (ScaleIODevice device : sdsDevices) {
                    String deviceId = device.getId();
                    String deviceStoragePoolId = device.getStoragePoolId();
                    String deviceCurrentPathName = device.getDeviceCurrentPathName();
                    String deviceName = device.getName();
                    System.out.printf("Device id : %s  name : %s  PoolId: %s Path: %s %n", deviceId, deviceName, deviceStoragePoolId, deviceCurrentPathName);

                }

            }

        } catch (Exception e) {
            log.error("Exception: ", e);
            Assert.fail();
        }
    }

    // @Test
    public void testAddVolume() {
        try {
            ScaleIOVolume result = restClient.addVolume("a", "d924dfbf00000002", "volTest3", "1073741824", true);
            System.out.printf("created volume id: %s", result.getId());
        } catch (Exception e) {
            log.error("Exception: ", e);
            Assert.fail();
        }
    }

    // @Test
    public void testRemoveVolume() {
        try {
            restClient.removeVolume("537b42c60000002a");
            System.out.println("removed.");
        } catch (Exception e) {
            log.error("Exception: ", e);
            Assert.fail();
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
            Assert.fail();
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
            Assert.fail();
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
            Assert.fail();
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
            Assert.fail();
        }
    }

    // @Test
    public void testRemoveCGSnaps() {
        try {
            restClient.removeConsistencyGroupSnapshot("ae9c0e7c00000002");
            System.out.println("removed");

        } catch (Exception e) {
            log.error("Exception: ", e);
            Assert.fail();
        }
    }
}
