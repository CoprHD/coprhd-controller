/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.authorization;

import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.tenant.TenantUpdateParam;
import com.emc.storageos.model.tenant.UserMappingChanges;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.usermanagement.model.RoleOrAcl;
import com.emc.storageos.usermanagement.setup.TenantMode;
import com.emc.storageos.usermanagement.util.ViPRClientHelper;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TenantModificationTest extends TenantMode {
    static ViPRCoreClient tenantAdminClient;
    static ViPRCoreClient secAdminClient;

    @BeforeClass
    public static void setupTest() throws Exception {
        String rootTenantAdmin = getUserByRole(rootTenantID, RoleOrAcl.TenantAdmin);
        tenantAdminClient = new ViPRCoreClient(controllerNodeEndpoint, true).withLogin(rootTenantAdmin, PASSWORD);

        String secAdmin = getUserByRole(null, RoleOrAcl.SecurityAdmin);
        secAdminClient = new ViPRCoreClient(controllerNodeEndpoint, true).withLogin(secAdmin, PASSWORD);
    }

    @AfterClass
    public static void teardownTest() throws Exception {
        if (tenantAdminClient != null) {
            tenantAdminClient.auth().logout();
            tenantAdminClient = null;
        }

        if (secAdminClient != null) {
            secAdminClient.auth().logout();
            secAdminClient = null;
        }
    }

    @Test
    // negative test
            public
            void tenantAdminModifyUserMapping() throws Exception {
        TenantUpdateParam tenantUpdateParam = new TenantUpdateParam();
        UserMappingChanges changes = new UserMappingChanges();
        List<UserMappingParam> listAdd = new ArrayList<UserMappingParam>();
        UserMappingParam param = new UserMappingParam();
        param.setDomain("Not Exist");
        listAdd.add(param);
        changes.setAdd(listAdd);
        tenantUpdateParam.setUserMappingChanges(changes);

        try {
            tenantAdminClient.tenants().update(rootTenantID, tenantUpdateParam);
            Assert.fail("Tenant admin should has no permission to modify tenant's user mapping");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 3000);
            Assert.assertTrue(see.getMessage().contains("Only users with SECURITY_ADMIN role can"));
        }
    }

    @Test
    // negative test
            public
            void tenantAdminModifyTenantQuota() throws Exception {
        QuotaUpdateParam quotaUpdateParam = new QuotaUpdateParam();
        quotaUpdateParam.setEnable(true);
        quotaUpdateParam.setQuotaInGb(50L);
        try {
            tenantAdminClient.tenants().updateQuota(rootTenantID, quotaUpdateParam);
            Assert.fail("TenantAdmin should has no permission to change tenant's quota");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 3000);
            Assert.assertTrue(see.getMessage().contains("Insufficient permissions"));
        }
    }

    @Test
    // negative test
            public
            void providerTenantAdminCreateTenant() throws Exception {
        ViPRClientHelper viPRClientHelper1 = new ViPRClientHelper(tenantAdminClient);
        try {
            viPRClientHelper1.createTenant("testTenant", "secqe.com", "attr", "value");
            Assert.fail("Provider tenant's TenantAdmin should has no permision to create subtenant");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 3000);
            Assert.assertTrue(see.getMessage().contains("Insufficient permissions"));
        }
    }

    @Test
    // positive test
            public
            void securityAdminModifyUserMapping() throws Exception {
        TenantUpdateParam tenantUpdateParam = new TenantUpdateParam();
        UserMappingChanges changes = new UserMappingChanges();
        List<UserMappingParam> listAdd = new ArrayList<UserMappingParam>();
        UserMappingParam param = new UserMappingParam();
        param.setDomain("Not Exist");
        listAdd.add(param);
        changes.setAdd(listAdd);
        tenantUpdateParam.setUserMappingChanges(changes);

        try {
            secAdminClient.tenants().update(rootTenantID, tenantUpdateParam);
            Assert.fail("fail, as the input contains wrong domain");
        } catch (ServiceErrorException see) {
            // verify the exception is not insufficent permission.
            Assert.assertNotEquals(see.getCode(), 3000);
            Assert.assertTrue(see.getMessage().contains("Parameter was provided but invalid"));
        }
    }

    @Test
    // positive test
            public
            void securityAdminModifyTenantQuota() throws Exception {
        QuotaInfo original = secAdminClient.tenants().getQuota(rootTenantID);

        QuotaUpdateParam quotaUpdateParam = new QuotaUpdateParam();
        quotaUpdateParam.setEnable(true);
        quotaUpdateParam.setQuotaInGb(50L);
        secAdminClient.tenants().updateQuota(rootTenantID, quotaUpdateParam);

        // restore quota
        quotaUpdateParam.setEnable(original.getEnabled());
        quotaUpdateParam.setQuotaInGb(original.getQuotaInGb());
        secAdminClient.tenants().updateQuota(rootTenantID, quotaUpdateParam);

        // should no exception
    }

    @Test
    // positive test
            public
            void securityAdminCreateTenant() throws Exception {
        ViPRClientHelper viPRClientHelper1 = new ViPRClientHelper(secAdminClient);
        try {
            viPRClientHelper1.createTenant("testTenant", "not-exist.com", "attr", "value");
            Assert.fail("should fail, as input domain not exist");
        } catch (ServiceErrorException see) {
            // verify the exception is not insufficent permission.
            Assert.assertNotEquals(see.getCode(), 3000);
            Assert.assertTrue(see.getMessage().contains("Parameter was provided but invalid"));
        }
    }
}
