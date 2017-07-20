/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource;

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
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.StorageGroupManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.AddVolumeParamType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.CreateStorageGroupParameter;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.DynamicDistributionType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.EditStorageGroupActionParam;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.EditStorageGroupParameter;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.ExpandStorageGroupParam;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.VolumeAttributeType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.VolumeIdentifierChoiceType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.VolumeIdentifierType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.VolumeManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model.VolumeType;

/**
 * @author fengs5
 *
 */
public class ManagerFunctionTest {

    private static final Logger LOG = LoggerFactory.getLogger(ManagerFunctionTest.class);
    static ManagerFactory managerFactory;
    static StorageGroupManager sgManager;
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
        managerFactory = new ManagerFactory(authenticationInfo);
        sgManager = managerFactory.genStorageGroupManager();
        volManager = managerFactory.genVolumeManager();
    }

    @Test
    public void testCreateOneVolumeWithNewSg() {
        String sgName = "stone_test_sg_auto_008";
        String volumeNamePrefix = "stone_test_vol_008-";
        testCreateEmptySg(sgName);
        testEditSgSlo(sgName);
        testEditSgWithWorkload(sgName);
        testEditSgWithHostIoLimit(sgName);
        testCreateNewVolInSg(sgName, volumeNamePrefix, 2l);
        testListVolumesOfSg(sgName);
        testListVolumesWithName(volumeNamePrefix + "1");
        List<String> volumeIds = testFindValidVolumes(volumeNamePrefix + "1");
        testFetchVolume(volumeIds.get(0));
    }

    private void testCreateEmptySg(String sgName) {

        CreateStorageGroupParameter param = new CreateStorageGroupParameter(sgName);
        param.setCreateEmptyStorageGroup(true);
        param.setEmulation("FBA");
        param.setSrpId("SRP_1");
        Assert.assertTrue(sgManager.createEmptySg(param).isSuccessfulStatus());
    }

    private void testEditSgSlo(String sgName) {

        Assert.assertTrue(sgManager.editSgWithSlo(sgName, "Bronze").isSuccessfulStatus());
    }

    private void testEditSgWithWorkload(String sgName) {

        Assert.assertTrue(sgManager.editSgWithWorkload(sgName, "DSS").isSuccessfulStatus());
    }

    private void testEditSgWithHostIoLimit(String sgName) {

        Assert.assertTrue(sgManager.editSgWithHostIoLimit(sgName, "10", "300", DynamicDistributionType.Never).isSuccessfulStatus());
    }

    private void testCreateNewVolInSg(String sgName, String volumeIdentifierName, long volumeNum) {
        VolumeAttributeType volumeAttribute = new VolumeAttributeType(CapacityUnitType.GB, "1");
        VolumeIdentifierType volumeIdentifier = new VolumeIdentifierType(VolumeIdentifierChoiceType.identifier_name_plus_append_number);
        volumeIdentifier.setIdentifier_name(volumeIdentifierName);
        volumeIdentifier.setAppend_number("1");
        AddVolumeParamType addVolumeParam = new AddVolumeParamType(volumeNum, volumeAttribute);
        addVolumeParam.setCreate_new_volumes(true);
        addVolumeParam.setVolumeIdentifier(volumeIdentifier);
        // addVolumeParam.setEmulation(EmulationType.CKD_3380.getValue());
        ExpandStorageGroupParam expandStorageGroupParam = new ExpandStorageGroupParam();
        expandStorageGroupParam.setAddVolumeParam(addVolumeParam);
        EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
        actionParam.setExpandStorageGroupParam(expandStorageGroupParam);
        EditStorageGroupParameter param = new EditStorageGroupParameter();
        param.setEditStorageGroupActionParam(actionParam);

        Assert.assertTrue(sgManager.createNewVolInSg(sgName, param).isSuccessfulStatus());
    }

    private void testListVolumesOfSg(String sgName) {

        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put("storageGroupId", sgName);
        urlParams.put("tdev", "true");
        Assert.assertTrue(volManager.listVolumes(urlParams).isSuccessfulStatus());
    }

    private void testListVolumesWithName(String volumeIdentifierName) {

        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put("volume_identifier", volumeIdentifierName);
        urlParams.put("tdev", "true");
        Assert.assertTrue(volManager.listVolumes(urlParams).isSuccessfulStatus());
    }

    private List<String> testFindValidVolumes(String volumeIdentifierName) {

        Map<String, String> filters = new HashMap<String, String>();
        filters.put("volume_identifier", volumeIdentifierName);
        filters.put("tdev", "true");
        List<String> volumeIds = volManager.findValidVolumes(filters);
        Assert.assertEquals(1, volumeIds.size());
        LOG.info("VolumeId as {}", volumeIds);
        return volumeIds;

    }

    private void testFetchVolume(String volumeId) {
        VolumeType volume = volManager.fetchVolume(volumeId);
        Assert.assertNotNull(volume);
        Assert.assertEquals(volumeId, volume.getVolumeId());
        LOG.info("Volume as {}", volume);
    }
}
