/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeUtils;
import com.emc.storageos.vnxe.models.BasicSystemInfo;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCifsShare;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeExportResult;
import com.emc.storageos.vnxe.models.VNXeFCPort;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
import com.emc.storageos.vnxe.models.VNXeHostInitiator.HostInitiatorTypeEnum;
import com.emc.storageos.vnxe.models.VNXeIscsiNode;
import com.emc.storageos.vnxe.models.VNXeLicense;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.vnxe.models.VNXePool;
import com.emc.storageos.vnxe.models.VNXeStorageTier;

public class ApiClientTest {
    private static KHClient _client;
    private static VNXeApiClient apiClient;
    private static String host = "losav161.lss.emc.com";
    private static String userName = "bourne";
    private static String password = "Bourn3!!";
    private static int port = 443;

    @BeforeClass
    public static void setup() throws Exception {
        
        _client = new KHClient(host, port, userName, password, true);
        
        apiClient = new VNXeApiClient(_client);
        
    }

    // @Test
    public void createCifsShare() {

        apiClient.createCIFSShare("fs_5", "fs_5_share2", "FULL", "/");

    }

    // @Test
    public void findCifsShareByName() {
        VNXeCifsShare share = apiClient.findCifsShareByName("fs_5_share2");
        if (share != null) {
            System.out.println(share.getId());
        } else {
            System.out.println("not found");
        }
    }

    // @Test
    public void removeCifsShare() {
        VNXeCommandJob job = apiClient.removeCifsShare("CIFSShare_1", "fs_5");
        System.out.println("job: " + job.getId());
    }

    // @Test
    public void createCifsShareforSnap() {
        VNXeCommandJob job = apiClient.createCifsShareForSnap("98784247817", "snap-share-1", "READ", "/", "fs_5");
        System.out.println("job: " + job.getId());
    }

    // @Test
    public void deleteCifsShareForSnap() {
        VNXeCommandJob job = apiClient.deleteCifsShareForSnapshot("CIFSShare_2");
        System.out.println("job: " + job.getId());
    }

    // @Test
    public void deleteNfsShareForSnap() {
        VNXeCommandJob job = apiClient.deleteNfsShareForSnapshot("");
        System.out.println("job: " + job.getId());
    }

    // @Test
    public void getNetmask() {
        String mask = VNXeUtils.convertCIDRToNetmask(24);
        System.out.println(mask);
    }

    // @Test
    public void getIscsiPorts() {
        List<VNXeIscsiNode> allnodes = apiClient.getAllIscsiPorts();
        for (VNXeIscsiNode node : allnodes) {
            System.out.println("iqn: " + node.getName());
            VNXeBase sp = node.getEthernetPort().getStorageProcessorId();
            System.out.println("sp: " + sp.getId());
            if (node.getIscsiPortal() != null) {
                System.out.println("ip: " + node.getIscsiPortal().getIpAddress());
            }
        }
    }

    // @Test
    public void getPools() {
        List<VNXePool> pools = apiClient.getPools();
        System.out.println(pools.size());
        for (VNXePool pool : pools) {
            String name = pool.getName();
            System.out.println(name);
            String status = pool.getStatus();
            System.out.println(pool.getStatus());
        }
    }

     //@Test
    public void createLun() {
        String name = "vipr-lun1";
        VNXeCommandJob job = apiClient.createLun(name, "pool_1", 2000000000L, true, null);
        System.out.println(job.getId());
    }

    // @Test
    public void getLunByResourceId() {
        List<VNXeLun> luns = apiClient.getLunByStorageResourceId("res_12");
        for (VNXeLun lun : luns) {
            System.out.println(lun.getId());
        }
    }

    // @Test
    public void createLunGroup() {
        VNXeCommandResult result = apiClient.createLunGroup("testGroup1");
        System.out.println(result.getStorageResource().getId());
    }

    // @Test
    public void addLunsToLunGroup() {
        List<String> luns = new ArrayList<String>();
        luns.add("sv_27");
        VNXeCommandResult result = apiClient.addLunsToLunGroup("res_14", luns);
        System.out.println(result.getSuccess());
    }

    // @Test
    public void getLicense() {
        Map<String, Boolean> map = apiClient.getLicenses();
        System.out.println(map.get(VNXeLicense.FeatureEnum.SNAP.name()));
    }

    // @Test
    public void removeLunsFromLunGroup() {
        List<String> luns = new ArrayList<String>();
        luns.add("sv_1");
        VNXeCommandResult result = apiClient.removeLunsFromLunGroup("res_26", luns);
        System.out.println(result.getSuccess());
    }

    // @Test
    public void deleteLunsFromLunGroup() {
        List<String> luns = new ArrayList<String>();
        luns.add("sv_46");
        VNXeCommandJob result = apiClient.deleteLunsFromLunGroup("res_20", luns);
        System.out.println(result.getId());
    }

    // @Test
    public void deleteLunGroup() {

        VNXeCommandResult result = apiClient.deleteLunGroup("res_14", true, true);
        System.out.println(result.getId());
    }

    // @Test
    public void exportLun() {
        String lunId = "sv_1";
        VNXeHostInitiator init = new VNXeHostInitiator();
        init.setChapUserName("iqn.1998-01.com.vmware:lgly6193-7ae20d76");
        init.setType(HostInitiatorTypeEnum.INITIATOR_TYPE_ISCSI);
        init.setName("lgly6193.lss.emc.com");
        List<VNXeHostInitiator> inits = new ArrayList<VNXeHostInitiator>();
        inits.add(init);
        VNXeExportResult result = apiClient.exportLun(lunId, inits);
        System.out.println(result.getHlu());
    }

    // @Test
    public void unexportLun() {
        String lunId = "sv_26";
        VNXeHostInitiator init = new VNXeHostInitiator();
        init.setChapUserName("iqn.1998-01.com.vmware:lgly6193-7ae20d76");
        init.setType(HostInitiatorTypeEnum.INITIATOR_TYPE_ISCSI);
        init.setName("lgly6193.lss.emc.com");
        List<VNXeHostInitiator> inits = new ArrayList<VNXeHostInitiator>();
        inits.add(init);
        apiClient.unexportLun(lunId, inits);

    }

    // @Test
    public void getLun() {
        apiClient.getLun("sv_4");
        apiClient.getLun("sv_5");
    }

    // @Test
    public void isFastVpEnabled() {
        boolean isenabled = apiClient.isFASTVPEnabled();
        System.out.println(isenabled);
    }

    // @Test
    public void getLunByLunGroup() {
        VNXeLun lun = apiClient.getLunByLunGroup("res_1", "vol9");
        System.out.println(lun.getId());
    }

    // @Test
    public void createLunSnap() {
        apiClient.createLunSnap("sv_37", "test-snap");
    }

    // @Test
    public void createLunGroupSnap() {
        apiClient.createLunGroupSnap("res_4", "test-group-snap");
    }

    //@Test
    public void getFCPort() {
        List<VNXeFCPort> ports = apiClient.getAllFcPorts();
        System.out.println(ports.size());
        for (VNXeFCPort port : ports) {
            System.out.println(port.getName() + " " + port.getPortWwn());
            System.out.println(port.getPortWwn());

        }
    }

    // @Test
    public void getBasicSystemInfo() {
        BasicSystemInfo info = apiClient.getBasicSystemInfo();
        System.out.println(info.getSoftwareVersion());

    }

    // @Test
    public void getSystem() {
        apiClient.getStorageSystem();
        apiClient.getNasServers();
    }

    //@Test
    public void getStorageTier() {
        List<VNXeStorageTier> tiers = apiClient.getStorageTiers();
        for (VNXeStorageTier tier : tiers) {
            System.out.println(tier.getSizeTotal());
            System.out.println(VNXeUtils.convertDoubleSizeToViPRLong(tier.getSizeTotal()));
        }
    }
    
    @Test
    public void createConsistencyGroup() {
        VNXeCommandResult result = apiClient.createConsistencyGroup("testGroup1");
        System.out.println(result.getStorageResource().getId());
    }

}
