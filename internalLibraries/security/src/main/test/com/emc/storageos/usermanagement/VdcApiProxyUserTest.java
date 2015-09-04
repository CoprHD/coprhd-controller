/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement;

import com.emc.storageos.model.vdc.VirtualDataCenterAddParam;
import com.emc.storageos.model.vdc.VirtualDataCenterModifyParam;
import com.emc.storageos.usermanagement.setup.TenantMode;
import com.emc.storageos.usermanagement.model.RoleOrAcl;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;

import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Random;

public class VdcApiProxyUserTest extends TenantMode {

    private static Logger logger = LoggerFactory.getLogger(VdcApiProxyUserTest.class);
    private static ViPRCoreClient proxyClient;

    @BeforeClass
    public static void setupProxyuser() throws Exception {
        logger.info("Setup for VdcApiProxyUserTest");
        String user = getUserByRole(null, RoleOrAcl.SecurityAdmin);
        ViPRCoreClient client = new ViPRCoreClient(controllerNodeEndpoint, true)
                .withLogin(user, PASSWORD);
        String rootProxyToken = client.auth().proxyToken();
        client.auth().logout();

        proxyClient = new ViPRCoreClient(controllerNodeEndpoint, true).withLogin("proxyuser", "ChangeMe");
        proxyClient.setProxyToken(rootProxyToken);
    }

    @AfterClass
    public static void cleanupProxyUser() throws Exception {
        proxyClient.auth().logout();
    }

    @Test
    public void testPostVdcWithProxyUser_neg() throws Exception {
        try {
            proxyClient.vdcs().create(getVdcAddParam());
            Assert.fail("proxy user add vdc should fail");
        } catch (ServiceErrorException se) {
            Assert.assertEquals(se.getHttpCode(), 403);
        }
    }

    @Test
    public void testGetRoleAssignmentWithProxyUser_neg() throws Exception {
        try {
            proxyClient.vdc().getRoleAssignments();
            Assert.fail("proxy user get vdc role assignment should fail");
        } catch (ServiceErrorException se) {
            Assert.assertEquals(se.getHttpCode(), 403);
        }
    }

    @Test
    public void testUpdateVdcWithProxyUser_neg() throws Exception {
        URI vdcId = proxyClient.vdcs().getAll().get(0).getId();
        VirtualDataCenterModifyParam modifyParam = new VirtualDataCenterModifyParam();
        modifyParam.setDescription("modified description");

        try {
            proxyClient.vdcs().update(vdcId, modifyParam);
            Assert.fail("proxy user update vdc should fail");
        } catch (ServiceErrorException se) {
            Assert.assertEquals(se.getHttpCode(), 403);
        }

    }

    private VirtualDataCenterAddParam getVdcAddParam() {
        VirtualDataCenterAddParam newVdcParam;
        newVdcParam = new VirtualDataCenterAddParam();
        newVdcParam.setName("new_vdc_" + new Random().nextInt());
        newVdcParam.setApiEndpoint("fake-endpoint");
        newVdcParam.setDescription("description");
        newVdcParam.setSecretKey("fake-secretkey");

        return newVdcParam;
    }
}
