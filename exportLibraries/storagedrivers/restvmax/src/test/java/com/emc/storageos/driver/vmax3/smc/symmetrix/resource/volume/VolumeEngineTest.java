/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.EngineFactory;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.StorageGroupEngineTest;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model.VolumeType;

/**
 * @author fengs5
 *
 */
public class VolumeEngineTest {
    private static final Logger LOG = LoggerFactory.getLogger(StorageGroupEngineTest.class);
    static EngineFactory engineFacory;
    static VolumeEngine volEngine;

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
        engineFacory = new EngineFactory(authenticationInfo);
        volEngine = engineFacory.genVolumeEngine();
    }

    @Test
    public void testListVolumesOfSg() {

        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put("storageGroupId", "stone_test_sg_auto_003");
        urlParams.put("tdev", "true");
        Assert.assertTrue(volEngine.listVolumes(urlParams).isSuccessfulStatus());
    }

    @Test
    public void testListVolumesWithName() {

        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put("volume_identifier", "stone_vol_auto_004-1");
        urlParams.put("tdev", "true");
        Assert.assertTrue(volEngine.listVolumes(urlParams).isSuccessfulStatus());
    }

    @Test
    public void testFindValidVolumes() {

        Map<String, String> filters = new HashMap<String, String>();
        filters.put("volume_identifier", "stone_vol_auto_004-1");
        filters.put("tdev", "true");
        List<String> volumeIds = volEngine.findValidVolumes(filters);
        Assert.assertEquals(1, volumeIds.size());
        LOG.info("VolumeId as {}", volumeIds);

    }

    @Test
    public void testFetchVolume() {
        String volumeId = "00B5A";
        VolumeType volume = volEngine.fetchVolume(volumeId);
        Assert.assertNotNull(volume);
        Assert.assertEquals(volumeId, volume.getVolumeId());
        LOG.info("Volume as {}", volume);
    }

}
