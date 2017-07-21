/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.EngineFactory;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.CreateStorageGroupParameter;

/**
 * @author fengs5
 *
 */
public class StorageGroupEngineTest {
    private static final Logger LOG = LoggerFactory.getLogger(StorageGroupEngineTest.class);
    static EngineFactory engineFacory;
    static StorageGroupEngine sgEngine;

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
        sgEngine = engineFacory.genStorageGroupEngine();
    }

    @Test
    public void testCreateEmptySg() {

        String sgName = "stone_test_sg_auto_010";
        CreateStorageGroupParameter param = new CreateStorageGroupParameter(sgName);
        param.setCreateEmptyStorageGroup(true);
        param.setEmulation("FBA");
        param.setSrpId("SRP_1");
        Assert.assertTrue(sgEngine.createEmptySg(param).isSuccessfulStatus());
    }

    // @Test
    // public void testEditSgSlo() {
    // String sgName = "stone_test_sg_auto_003";
    //
    // Assert.assertTrue(sgEngine.editSgWithSlo(sgName, "Bronze").isSuccessfulStatus());
    // }
    //
    // @Test
    // public void testEditSgWithWorkload() {
    // String sgName = "stone_test_sg_auto_003";
    //
    // Assert.assertTrue(sgEngine.editSgWithWorkload(sgName, "DSS").isSuccessfulStatus());
    // }
    //
    // @Test
    // public void testEditSgWithHostIoLimit() {
    // String sgName = "stone_test_sg_auto_003";
    //
    // Assert.assertTrue(sgEngine.editSgWithHostIoLimit(sgName, "10", "300", DynamicDistributionType.Never).isSuccessfulStatus());
    // }
    //
    // @Test
    // public void testCreateNewVolInSg() {
    // String sgName = "stone_test_sg_auto_003";
    // VolumeAttributeType volumeAttribute = new VolumeAttributeType(CapacityUnitType.GB, "1");
    // VolumeIdentifierType volumeIdentifier = new VolumeIdentifierType(VolumeIdentifierChoiceType.identifier_name_plus_append_number);
    // volumeIdentifier.setIdentifier_name("stone_vol_auto_005-");
    // volumeIdentifier.setAppend_number("1");
    // AddVolumeParamType addVolumeParam = new AddVolumeParamType(2l, volumeAttribute);
    // addVolumeParam.setCreate_new_volumes(true);
    // addVolumeParam.setVolumeIdentifier(volumeIdentifier);
    // // addVolumeParam.setEmulation(EmulationType.CKD_3380.getValue());
    // ExpandStorageGroupParam expandStorageGroupParam = new ExpandStorageGroupParam();
    // expandStorageGroupParam.setAddVolumeParam(addVolumeParam);
    // EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
    // actionParam.setExpandStorageGroupParam(expandStorageGroupParam);
    // EditStorageGroupParameter param = new EditStorageGroupParameter();
    // param.setEditStorageGroupActionParam(actionParam);
    //
    // Assert.assertTrue(sgEngine.createNewVolInSg(sgName, param).isSuccessfulStatus());
    // }

}
