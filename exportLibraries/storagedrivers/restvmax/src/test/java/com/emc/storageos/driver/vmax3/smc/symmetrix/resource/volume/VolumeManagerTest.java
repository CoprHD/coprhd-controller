/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.ManagerFactory;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.StorageGroupManagerTest;

/**
 * @author fengs5
 *
 */
public class VolumeManagerTest {
    private static final Logger LOG = LoggerFactory.getLogger(StorageGroupManagerTest.class);
    static ManagerFactory managerFacory;
    static VolumeManager volManager;

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
        managerFacory = new ManagerFactory(authenticationInfo);
        volManager = managerFacory.genVolumeManager();
    }

    @Test
    public void testListVolumesOfSg() {
        List<String> urlFillers = new ArrayList<String>();
        urlFillers.add(volManager.getAuthenticationInfo().getSn());
        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put("storageGroupId", "stone_test_sg_auto_003");
        urlParams.put("tdev", "true");
        Assert.assertTrue(volManager.listVolumes(urlFillers, urlParams).isSuccessfulStatus());
    }

    @Test
    public void testListVolumesWithName() {
        List<String> urlFillers = new ArrayList<String>();
        urlFillers.add(volManager.getAuthenticationInfo().getSn());
        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put("volume_identifier", "stone_vol_auto_004-1");
        urlParams.put("tdev", "true");
        Assert.assertTrue(volManager.listVolumes(urlFillers, urlParams).isSuccessfulStatus());
    }

    @Test
    public void testFindValidVolumes() {
        List<String> urlFillers = new ArrayList<String>();
        urlFillers.add(volManager.getAuthenticationInfo().getSn());
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("volume_identifier", "stone_vol_auto_004-1");
        filters.put("tdev", "true");
        List<String> volumeIds = volManager.findValidVolumes(urlFillers, filters);
        Assert.assertEquals(1, volumeIds.size());
        LOG.info("VolumeId as {}", volumeIds);

    }

}
