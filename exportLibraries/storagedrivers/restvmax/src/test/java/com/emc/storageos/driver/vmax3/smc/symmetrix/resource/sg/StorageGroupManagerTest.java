/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.ManagerFactory;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.CapacityUnitType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.AddVolumeParamType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.EditStorageGroupActionParam;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.EditStorageGroupParameter;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.ExpandStorageGroupParam;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.VolumeAttributeType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.VolumeIdentifierChoiceType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.VolumeIdentifierType;

/**
 * @author fengs5
 *
 */
public class StorageGroupManagerTest {
    private static final Logger LOG = LoggerFactory.getLogger(StorageGroupManagerTest.class);
    static ManagerFactory managerFacory;
    static StorageGroupManager sgManager;

    @BeforeClass
    public static void setup() {
        String host = "lglw7150.lss.emc.com";
        int port = 8443;
        String user = "smc";
        String password = "smc";
        String sn = "000196801468";

        AuthenticationInfo authenticationInfo = new AuthenticationInfo(host, port, user, password);
        authenticationInfo.setSn(sn);
        managerFacory = new ManagerFactory(authenticationInfo);
        sgManager = managerFacory.genStorageGroupManager();
    }

    // @Test
    // public void testCreateEmptySg() {
    //
    // String sgName = "stone_test_sg_auto_003";
    // CreateStorageGroupParameter param = new CreateStorageGroupParameter(sgName);
    // param.setCreateEmptyStorageGroup(true);
    // param.setEmulation("FBA");
    // param.setSrpId("SRP_1");
    // List<String> urlFillers = new ArrayList<String>();
    // urlFillers.add(sgManager.getAuthenticationInfo().getSn());
    // Assert.assertTrue(sgManager.createEmptySg(param, urlFillers).isSuccessfulStatus());
    // }

    // @Test
    // public void testEditSgSlo() {
    // String sgName = "stone_test_sg_auto_003";
    //
    // EditStorageGroupSLOParam sloParam = new EditStorageGroupSLOParam("Bronze");
    // EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
    // actionParam.setEditStorageGroupSLOParam(sloParam);
    // EditStorageGroupParameter param = new EditStorageGroupParameter();
    // param.setEditStorageGroupActionParam(actionParam);
    // List<String> urlFillers = new ArrayList<String>();
    // urlFillers.add(sgManager.getAuthenticationInfo().getSn());
    // Assert.assertTrue(sgManager.editSgWithSlo(sgName, param, urlFillers).isSuccessfulStatus());
    // }
    //
    // @Test
    // public void testEditSgWithWorkload() {
    // String sgName = "stone_test_sg_auto_003";
    //
    // EditStorageGroupWorkloadParam wlParam = new EditStorageGroupWorkloadParam("DSS");
    // EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
    // actionParam.setEditStorageGroupWorkloadParam(wlParam);
    // EditStorageGroupParameter param = new EditStorageGroupParameter();
    // param.setEditStorageGroupActionParam(actionParam);
    // List<String> urlFillers = new ArrayList<String>();
    // urlFillers.add(sgManager.getAuthenticationInfo().getSn());
    // Assert.assertTrue(sgManager.editSgWithSlo(sgName, param, urlFillers).isSuccessfulStatus());
    // }
    //
    // @Test
    // public void testEditSgWithHostIoLimit() {
    // String sgName = "stone_test_sg_auto_003";
    //
    // SetHostIOLimitsParam setHostIOLimitsParam = new SetHostIOLimitsParam("10", "300", DynamicDistributionType.Never);
    // EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
    // actionParam.setSetHostIOLimitsParam(setHostIOLimitsParam);
    // EditStorageGroupParameter param = new EditStorageGroupParameter();
    // param.setEditStorageGroupActionParam(actionParam);
    // List<String> urlFillers = new ArrayList<String>();
    // urlFillers.add(sgManager.getAuthenticationInfo().getSn());
    // Assert.assertTrue(sgManager.editSgWithHostIoLimit(sgName, param, urlFillers).isSuccessfulStatus());
    // }

    @Test
    public void testCreateNewVolInSg() {
        String sgName = "stone_test_sg_auto_003";
        VolumeAttributeType volumeAttribute = new VolumeAttributeType(CapacityUnitType.GB, "1");
        VolumeIdentifierType volumeIdentifier = new VolumeIdentifierType(VolumeIdentifierChoiceType.identifier_name_plus_append_number);
        volumeIdentifier.setIdentifier_name("stone_vol_auto_005-");
        volumeIdentifier.setAppend_number("1");
        AddVolumeParamType addVolumeParam = new AddVolumeParamType(2l, volumeAttribute);
        addVolumeParam.setCreate_new_volumes(true);
        addVolumeParam.setVolumeIdentifier(volumeIdentifier);
        // addVolumeParam.setEmulation(EmulationType.CKD_3380.getValue());
        ExpandStorageGroupParam expandStorageGroupParam = new ExpandStorageGroupParam();
        expandStorageGroupParam.setAddVolumeParam(addVolumeParam);
        EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
        actionParam.setExpandStorageGroupParam(expandStorageGroupParam);
        EditStorageGroupParameter param = new EditStorageGroupParameter();
        param.setEditStorageGroupActionParam(actionParam);

        List<String> urlFillers = new ArrayList<String>();
        urlFillers.add(sgManager.getAuthenticationInfo().getSn());
        Assert.assertTrue(sgManager.createNewVolInSg(sgName, param, urlFillers).isSuccessfulStatus());
    }

}
