package com.emc.storageos.geo.service;

import com.emc.storageos.api.service.ApiTestBase;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.model.tenant.TenantUpdateParam;
import com.emc.storageos.model.user.UserInfo;
import com.emc.storageos.model.varray.VirtualArrayCreateParam;
import com.emc.vipr.model.keystore.RotateKeyAndCertParam;
import com.sun.jersey.api.client.ClientResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.util.*;


public class RoleChangeTest extends ApiTestBase{

    private String remoteVDCVIP;
    private BalancedWebResource rootUser;
    private BalancedWebResource superSanity;
    private String rootToken;
    private String superSanityToken;

    @Before
    public void setup() throws Exception{
        initLoadBalancer(true);
        String remoteVDCVIPvar = System.getenv("REMOTE_VDC_VIP");
        if (remoteVDCVIPvar == null || remoteVDCVIPvar.equals(""))
            Assert.fail("Missing remove VDC vip");
        String remoteVDCTemplate = "https://%1$s:4443";
        remoteVDCVIP = String.format(remoteVDCTemplate, remoteVDCVIPvar);
        rootUser = createHttpsClient(SYSADMIN, SYSADMIN_PASSWORD, baseUrls);
        superSanity = createHttpsClient(SUPERUSER, AD_PASSWORD, baseUrls);

        TenantResponse tenantResp = superSanity.path("/tenant").get(TenantResponse.class);
        superSanityToken = (String)_savedTokens.get(SUPERUSER);
        rootTenantId = tenantResp.getTenant();

        rootUser.path("/tenant").get(TenantResponse.class);
        rootToken = (String)_savedTokens.get("root");
    }

    @After
    public void teardown() throws Exception {
        if (rootUser != null) {
            rootUser.path("/logout");
            rootUser = null;
        }

        if (superSanity != null) {
            superSanity.path("/logout");
            superSanity = null;
        }
    }


    @Test
    public void accessAuthnApis() throws Exception {
        ClientResponse resp = rootUser.path("/vdc/admin/authnproviders").get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());

        resp = superSanity.path("/vdc/admin/authnproviders").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
    }

    @Test
    public void accessTenantApis() throws Exception {

        // list Tenants ok for root, as root has System Monitor role
        ClientResponse resp = rootUser.path("/tenants/" + rootTenantId + "/subtenants")
                .get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // root should no permisson to update tenant
        TenantUpdateParam tenantUpdateParam = new TenantUpdateParam();
        tenantUpdateParam.setLabel("updated_tenant_lable" + new Random().nextInt());
        resp = rootUser.path("/tenants/" + rootTenantId).put(ClientResponse.class, tenantUpdateParam);
        Assert.assertEquals(403, resp.getStatus());

        // root should no permission to get role-assignment
        resp = rootUser.path("/tenants/" + rootTenantId + "/role-assignments")
                .get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());


    }

    @Test
    public void whoAmi() {

        // root whoami
        UserInfo info = rootUser.path("/user/whoami").get(UserInfo.class);
        Assert.assertEquals(SYSADMIN, info.getCommonName());
        Assert.assertEquals(4, info.getVdcRoles().size());
        Assert.assertEquals(0, info.getHomeTenantRoles().size());
        Assert.assertEquals(0, info.getSubTenantRoles().size());

        // check the root user's default vdc roles.
        List<String> roles = new ArrayList<String>(
                Arrays.asList("RESTRICTED_SECURITY_ADMIN", "RESTRICTED_SYSTEM_ADMIN","SYSTEM_MONITOR", "SYSTEM_AUDITOR"));
        Assert.assertTrue(info.getVdcRoles().containsAll(roles));


        // superSanity whoami
        info = superSanity.path("/user/whoami").get(UserInfo.class);
        Assert.assertEquals(SUPERUSER, info.getCommonName());
        Assert.assertTrue(info.getVdcRoles().size() >= 2);
        Assert.assertTrue(info.getVdcRoles().contains("SECURITY_ADMIN"));
        Assert.assertTrue(info.getVdcRoles().contains("SYSTEM_ADMIN"));
    }

    @Test
    public void accessVarray() throws Exception{
        VirtualArrayCreateParam virtualArrayCreateParam = new VirtualArrayCreateParam();
        virtualArrayCreateParam.setLabel("array_created_by_root" + new Random().nextInt());

        ClientResponse resp = rootUser.path("/vdc/varrays").header(AUTH_TOKEN_HEADER, rootToken).post(ClientResponse.class, virtualArrayCreateParam);
        Assert.assertEquals(200, resp.getStatus());
    }



    /**
     *  verify TenantAdmin can do something: list RoleAssignment, whoami, create project
     */
    @Test
    public void tenantAdmin() throws Exception {

        // assign Provider Tenant's Tenant admin to AD user
        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();
        roleAssignmentEntry.setSubjectId(TENANTADMIN);
        roleAssignmentEntry.setRoles(new ArrayList<String>(Arrays.asList("TENANT_ADMIN")));
        List<RoleAssignmentEntry> add = new ArrayList<RoleAssignmentEntry>();
        add.add(roleAssignmentEntry);
        RoleAssignmentChanges roleAssignmentChanges = new RoleAssignmentChanges();
        roleAssignmentChanges.setAdd(add);
        ClientResponse resp = superSanity.path("/tenants/" + rootTenantId + "/role-assignments")
                .header(AUTH_TOKEN_HEADER, superSanityToken)
                .put(ClientResponse.class, roleAssignmentChanges);
        Assert.assertEquals(200, resp.getStatus());

        // list tenant's role-assignments
        BalancedWebResource tenantAdmin = createHttpsClient(TENANTADMIN, AD_PASSWORD, baseUrls);
        resp = tenantAdmin.path("/tenants/" + rootTenantId + "/role-assignments")
                .get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String tenantAdminToken = (String)_savedTokens.get(TENANTADMIN);

        // tenantadmin whoami
        UserInfo info = tenantAdmin.path("/user/whoami").get(UserInfo.class);
        Assert.assertEquals(TENANTADMIN, info.getCommonName());
        Assert.assertEquals(0, info.getVdcRoles().size());
        Assert.assertEquals(1, info.getHomeTenantRoles().size());
        Assert.assertEquals(0, info.getSubTenantRoles().size());
        Assert.assertTrue(info.getHomeTenantRoles().contains("TENANT_ADMIN"));

        // create project
        ProjectParam projectParam = new ProjectParam();
        projectParam.setName("project_unittest" + new Random().nextInt());
        resp = tenantAdmin.path("/tenants/" + rootTenantId + "/projects")
                .header(AUTH_TOKEN_HEADER, tenantAdminToken)
                .post(ClientResponse.class, projectParam);
        Assert.assertEquals(200, resp.getStatus());
    }


    /**
     *  verify root has permission on vdc role assignment APIs of local vdc
     */
    @Test
    public void rootUpdateVdcRoleAssignment() {

        // assign SecurityAdmin to AD user
        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();
        roleAssignmentEntry.setSubjectId(SUPERUSER);
        roleAssignmentEntry.setRoles(new ArrayList<String>(Arrays.asList("SECURITY_ADMIN")));
        List<RoleAssignmentEntry> add = new ArrayList<RoleAssignmentEntry>();
        add.add(roleAssignmentEntry);
        RoleAssignmentChanges roleAssignmentChanges = new RoleAssignmentChanges();
        roleAssignmentChanges.setAdd(add);

        ClientResponse resp = rootUser.path("/vdc/role-assignments")
                .header(AUTH_TOKEN_HEADER, rootToken)
                .put(ClientResponse.class, roleAssignmentChanges);
        Assert.assertEquals(200, resp.getStatus());

        resp = rootUser.path("/vdc/role-assignments").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
    }

    @Test
    public void putKeystore_neg() {
        RotateKeyAndCertParam param = new RotateKeyAndCertParam();
        param.setSystemSelfSigned(true);

        ClientResponse resp = rootUser.path("/vdc/keystore")
                .header(AUTH_TOKEN_HEADER, rootToken)
                .put(ClientResponse.class, param);
        Assert.assertEquals(405, resp.getStatus());
    }

}
