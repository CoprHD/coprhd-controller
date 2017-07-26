/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.volume;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.smc.EngineFactory;
import com.emc.storageos.driver.univmax.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.StorageGroupEngineTest;

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

    // @Test
    // public void testListVolumesOfSg() {
    //
    // Map<String, String> urlParams = new HashMap<String, String>();
    // urlParams.put("storageGroupId", "stone_test_sg_auto_003");
    // urlParams.put("tdev", "true");
    // Assert.assertTrue(volEngine.listVolumes(urlParams).isSuccessfulStatus());
    // }
    //
    // @Test
    // public void testListVolumesWithName() {
    //
    // Map<String, String> urlParams = new HashMap<String, String>();
    // urlParams.put("volume_identifier", "stone_vol_auto_004-1");
    // urlParams.put("tdev", "true");
    // Assert.assertTrue(volEngine.listVolumes(urlParams).isSuccessfulStatus());
    // }
    //
    // @Test
    // public void testFindValidVolumes() {
    //
    // Map<String, String> filters = new HashMap<String, String>();
    // filters.put("volume_identifier", "stone_vol_auto_004-1");
    // filters.put("tdev", "true");
    // List<String> volumeIds = volEngine.findValidVolumes(filters);
    // Assert.assertEquals(1, volumeIds.size());
    // LOG.info("VolumeId as {}", volumeIds);
    //
    // }
    //
    // @Test
    // public void testFetchVolume() {
    // String volumeId = "00B5A";
    // VolumeType volume = volEngine.fetchVolume(volumeId);
    // Assert.assertNotNull(volume);
    // Assert.assertEquals(volumeId, volume.getVolumeId());
    // LOG.info("Volume as {}", volume);
    // }

    // @Test
    // public void testRemoveStandardVolume() {
    // String volumeId = "00CBA";
    // Assert.assertTrue(volEngine.removeStandardVolume(volumeId).isSuccessfulStatus());
    // }

    // @Test
    // public void testGetNextPageForItarator() {
    // String iteratorId = "e1d92029-0a75-4ee2-81ef-c65ffa4fe7c9_0";
    // Type responseClazzType = new TypeToken<ResultListType<VolumeListResultType>>() {
    // }.getType();
    // ResultListType<VolumeListResultType> responseBean = volEngine.getNextPageForItarator(iteratorId, 1001, 1050);
    // List<VolumeListResultType> volumeList = responseBean.getResult();
    // System.out.println(volumeList.size());
    // }

    @Test
    public void testFindValidVolumes() {
        LOG.info("volume number as {}", volEngine.findValidVolumes(null).size());
    }

}
