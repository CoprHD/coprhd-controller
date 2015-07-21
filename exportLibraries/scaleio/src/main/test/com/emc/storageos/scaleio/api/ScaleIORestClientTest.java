/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio.api;


import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;
import com.emc.storageos.services.util.EnvConfig;

public class ScaleIORestClientTest {
    private static ScaleIORestClient restClient;
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String HOST = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.ipaddress");
    private static final String USER = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.user");
    private static final String PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.password");
    static int PORT = 443;
    
    @BeforeClass
    static public void setUp() {
        ScaleIORestClientFactory factory = new ScaleIORestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = ScaleIOContants.getAPIBaseURI(HOST, PORT);
        restClient = (ScaleIORestClient) factory.getRESTClient(URI.create(endpoint), USER, PASSWORD, true);
        
    }
    
    //@Test
    public void testQueryCluster() {
        ScaleIOQueryClusterResult result = null;
        try {
            result = restClient.queryClusterCommand();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        System.out.printf("SIO primary IP %s", result.getPrimaryIP());
    }
    
    //@Test
    public void testGetVersion() {
        String result = null;
        try {
            result = restClient.getVersion();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        System.out.printf("version %s", result);
    }

    @Test
    public void testQueryStoragePool() {
        ScaleIOQueryStoragePoolResult result = null;
        try {
            result = restClient.queryStoragePool(null, "d924dfbf00000002");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        System.out.printf("pool available capacity %s", result.getAvailableCapacity());
    }
    
    //@Test
    public void testQueryAll() {
        ScaleIOQueryAllResult result = null;
        try {
            result = restClient.queryAll();
            for (String domainName : result.getProtectionDomainNames()){
                System.out.printf("domain: %s \n", domainName);
                for (String poolId : result.getStoragePoolsForProtectionDomain(domainName)) {
                    String availSize = result.getStoragePoolProperty(domainName, poolId, ScaleIOQueryAllCommand.POOL_AVAILABLE_CAPACITY);
                    String name = result.getStoragePoolProperty(domainName, poolId, ScaleIOContants.NAME);
                    System.out.printf("pool id: %s pool name : %s available size : %s \n", poolId, name, availSize);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testQuerySDS() {
        ScaleIOQueryAllSDSResult result = null;
        try {
            result = restClient.queryAllSDS();
            Collection<String> domainIds = result.getProtectionDomainIds();
            for (String domainId : domainIds){
                System.out.printf("domain: %s \n", domainId);
                for (ScaleIOAttributes sdsAtts : result.getSDSForProtectionDomain(domainId)) {
                    String sdsId = sdsAtts.get(ScaleIOQueryAllSDSResult.SDS_ID);
                    String sdsPort = sdsAtts.get(ScaleIOQueryAllSDSResult.SDS_PORT);
                    String sdsIp = sdsAtts.get(ScaleIOQueryAllSDSResult.SDS_IP);
                    System.out.printf("Sds id : %s  port : %s  IP: %s \n", sdsId, sdsPort, sdsIp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testQueryAllScsiInitiators() {

        try {
            ScaleIOQueryAllSCSIInitiatorsResult result = restClient.queryAllSCSIInitiators();
            if (result != null && !result.getAllInitiatorIds().isEmpty()) {
                System.out.println("Has initiators");
                Set<String> ids = result.getAllInitiatorIds();
                for (String id : ids) {
                    System.out.println(id);
                }
            } else {
                System.out.println("no initiators");
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testAddVolume() {
        try {
            ScaleIOAddVolumeResult result = restClient.addVolume("a", "d924dfbf00000002", "volTest3", "1073741824", true);        
            System.out.printf("created volume id: %s", result.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testRemoveVolume() {
        try {
            restClient.removeVolume("537b42c60000002a");
            System.out.println("removed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testExpandVolume(){
        try {
            String volId = "537b69d70000003c";
            restClient.modifyVolumeCapacity(volId, "16");
            ScaleIOVolume vol = restClient.queryVolume(volId);
            String size = vol.getSizeInKb();
            Long sizeInGB = Long.parseLong(size)/1024L/1024L;
            System.out.println("size is :" +sizeInGB.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testMapVolumeToSDC(){
        try {
            String sdcId = "6de2bbb700000001";
            String volId = "537b69d70000003c";
            restClient.mapVolumeToSDC(volId, sdcId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testUnmapVolumeToSDC(){
        try {
            String sdcId = "6de2bbb700000001";
            String volId = "537b69d70000003c";
            restClient.unMapVolumeToSDC(volId, sdcId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //@Test
    public void testGetVolumes() {
        try {
            List<String> volIds = new ArrayList<String>();
            volIds.add("537b42d80000002f");
            volIds.add("537b69d70000003c");
            Map<String, String> vols = restClient.getVolumes(volIds);
            for (Map.Entry<String, String> entry : vols.entrySet()) {
                System.out.println("name : " + entry.getKey() + "id: " + entry.getValue());
            }
            
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testSnapVolumes() {
        try {
            Map<String, String> map = new HashMap<String, String>();
            map.put("537b42d80000002f", "test123snap");
            map.put("537b69d70000003c", "volTest2Snap");
            ScaleIOSnapshotMultiVolumeResult result = restClient.snapshotMultiVolume(map, restClient.getSystemId());
            ScaleIOSnapshotVolumeResult snapResult = result.findResult("volTest2Snap");
            
            System.out.println("name : " + snapResult.getName() + " id: " + snapResult.getId());
            
            
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //@Test
    public void testRemoveCGSnaps(){
        try {
            restClient.removeConsistencyGroupSnapshot("ae9c0e7c00000002");
            System.out.println("removed");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
