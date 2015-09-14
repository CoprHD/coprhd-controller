/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.*;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.cluster.ClusterList;
import com.emc.storageos.model.host.vcenter.*;
import com.emc.storageos.model.tasks.TasksList;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.model.usergroup.UserAttributeParam;
import com.emc.storageos.model.usergroup.UserGroupCreateParam;
import com.emc.storageos.model.usergroup.UserGroupRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.services.util.EnvConfig;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.commons.httpclient.HttpStatus;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.Assert;

import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * A test class to validate the vCenter discovery and sharing functionality
 * and APIs. This not cover the exports and datastore portion of it yet.
 *
 */
public class ApiTestVcenter extends ApiTestBase {
    private static final String VCENTER_API = "/compute/vcenters";
    private static final String VCENTER_API_WITH_ID = VCENTER_API + "/%s";
    private static final String VCENTER_ACL_API = VCENTER_API_WITH_ID + "/acl";
    private static final String VCENTER_DATA_CENTERS_API = VCENTER_API_WITH_ID + "/vcenter-data-centers";
    private static final String VCENTER_CLUSTERS_API = VCENTER_API_WITH_ID + "/clusters";
    private static final String VCENTER_HOSTS_API = VCENTER_API_WITH_ID + "/hosts";
    private static final String VCENTER_DEACTIVATE_API = VCENTER_API_WITH_ID + "/deactivate";
    private static final String VCENTER_DISCOVER_API = VCENTER_API_WITH_ID + "/discover";
    private static final String VCENTERS_TENANT_API = "/tenants/%s/vcenters";
    private static final String HOSTS_TENANT_API = "/tenants/%s/hosts";
    private static final String CLUSTERS_TENANT_API = "/tenants/%s/clusters";
    private static final String DATA_CENTER_API_WITH_ID = "/compute/vcenter-data-centers/%s";
    private static final String TENANT_ACL_API = "/tenants/%s/role-assignments";
    private static final String USER_GROUP_API = "/vdc/admin/user-groups";
    private static final String USER_GROUP_API_WITH_ID = USER_GROUP_API + "/%s";
    private static final String VDC_ROLE_ASSIGNMENT_API = "/vdc/role-assignments";
    private static final String USER_WHOAMI_API = "/user/whoami";
    private static final String VDC_TASKS_API = "/vdc/tasks";
    private static final String VDC_TASK_DELETE_API = "/vdc/tasks/%s/delete";

    private static final String DEFAULT_VCENTER_IP = EnvConfig.get("sanity", "vcenter.ip");
    private static final String DEFAULT_VCENTER_USER = EnvConfig.get("sanity", "vcenter.user");
    private static final String DEFAULT_VCENTER_PASSWORD = EnvConfig.get("sanity", "vcenter.password");
    private static final String DEFAULT_VCENTER_NAME = "TestVcenter";

    private static final String SECURITY_ADMIN_ROLE = "SECURITY_ADMIN";
    private static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";
    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private static final String SUBTENANT_NAME = "SubTenant1";

    private static final String TASK_STATUS_PENDING = "pending";
    private static final String NO_TENANT_FILTER = "No-Filter";
    private static final String NO_TENANTS_ASSIGNED = "Not-Assigned";

    private static final int VCENTER_PORT = 443;

    private static String authnProviderDomain = null;
    private ApiTestAuthnProviderUtils apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();;
    private ApiTestTenants apiTestTenants = new ApiTestTenants();
    private List<CleanupResource> _cleanupResourceList = null;
    private List<CleanupResource> _cleanupEnvironmentResourceList = new LinkedList<CleanupResource>();

    private static ApiTestVcenter apiTestVcenter = new ApiTestVcenter();

    @BeforeClass
    public static void setupTestSuite() throws NoSuchAlgorithmException {
        apiTestVcenter.setupHttpsResources();

        apiTestVcenter.apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();
        apiTestVcenter.apiTestTenants = new ApiTestTenants();
        apiTestVcenter.apiTestTenants.rootTenantId = apiTestVcenter.rootTenantId;

        apiTestVcenter.testSetup();
        apiTestVcenter.tearDownHttpsResources();
    }

    @AfterClass
    public static void tearDownTestSuite() throws NoSuchAlgorithmException{
        apiTestVcenter.setupHttpsResources();
        apiTestVcenter.testTeardown();

        CleanupResource.cleanUpTestResources(apiTestVcenter._cleanupEnvironmentResourceList);
        apiTestVcenter.tearDownHttpsResources();
    }

    @Before
    public void setUp() throws NoSuchAlgorithmException {
        setupHttpsResources();
        _cleanupResourceList = new LinkedList<CleanupResource>();

        apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();
        apiTestTenants = new ApiTestTenants();
        apiTestTenants.rootTenantId = rootTenantId;
    }

    @After
    public void tearDown() throws NoSuchAlgorithmException {
        CleanupResource.cleanUpTestResources(_cleanupResourceList);
        tearDownHttpsResources();
    }

    private void registerResourceForCleanup(CleanupResource resource) {
        if (_cleanupResourceList == null) {
            _cleanupResourceList = new LinkedList<CleanupResource>();
        }
        _cleanupResourceList.add(resource);
    }

    private void registerEnvironmentResourceForCleanup(CleanupResource resource) {
        if (_cleanupEnvironmentResourceList == null) {
            _cleanupEnvironmentResourceList = new LinkedList<CleanupResource>();
        }
        _cleanupEnvironmentResourceList.add(resource);
    }

    public String getVcenterApi() {
        return VCENTER_API;
    }

    public String getVcenterApiWithId(URI vCenterId) {
        return String.format(VCENTER_API_WITH_ID, vCenterId.toString());
    }

    public String getVcenterDataCentersApi(URI vCenterId) {
        return String.format(VCENTER_DATA_CENTERS_API, vCenterId.toString());
    }

    public String getVcenterAclApi(URI vCenterId) {
        return String.format(VCENTER_ACL_API, vCenterId.toString());
    }

    public String getVcenterClustersApi(URI vCenterId) {
        return String.format(VCENTER_CLUSTERS_API, vCenterId.toString());
    }

    public String getVcenterHostsApi(URI vCenterId) {
        return String.format(VCENTER_HOSTS_API, vCenterId.toString());
    }

    public String getVcenterDeactivateApi(URI vCenterId) {
        return String.format(VCENTER_DEACTIVATE_API, vCenterId.toString());
    }

    public String getVcenterDiscoverApi(URI vCenterId) {
        return String.format(VCENTER_DISCOVER_API, vCenterId.toString());
    }

    public String getVcenterTenantApi(URI tenantId) {
        return String.format(VCENTERS_TENANT_API, tenantId.toString());
    }

    public String getHostTenantApi(URI tenantId) {
        return String.format(HOSTS_TENANT_API, tenantId.toString());
    }

    public String getClusterTenantApi(URI tenantId) {
        return String.format(CLUSTERS_TENANT_API, tenantId.toString());
    }

    public String getDefaultVcenterIp() {
        return DEFAULT_VCENTER_IP;
    }

    public String getDefaultVcenterUser() {
        return DEFAULT_VCENTER_USER;
    }

    public String getDefaultVcenterPassword() {
        return DEFAULT_VCENTER_PASSWORD;
    }

    public String getDefaultVcenterName() {
        return DEFAULT_VCENTER_NAME;
    }

    public String getDataCenterApiWithId (URI dataCenterId) {
        return String.format(DATA_CENTER_API_WITH_ID, dataCenterId.toString());
    }

    private String getAuthnProviderCreateApi() {
        return apiTestAuthnProviderUtils.getAuthnProviderBaseURL();
    }

    private String getAuthnProviderDeleteApi(URI uri) {
        return apiTestAuthnProviderUtils.getAuthnProviderEditURL(uri);
    }

    private String getUserGroupApi() {
        return USER_GROUP_API;
    }

    private String getUserGroupApiWithId(URI userGroupId) {
        return String.format(USER_GROUP_API_WITH_ID, userGroupId.toString());
    }

    private String getAuthnProviderDomain() {
        return authnProviderDomain;
    }

    private void setAuthnProviderDomain(String domain) {
        authnProviderDomain = domain;
    }

    private String getSubTenantCreateApi() {
        return apiTestTenants.getTestApi();
    }

    private String getTenantEditApi(URI tenantId) {
        return apiTestTenants.getTestEditApi(tenantId);
    }

    private String getTenantDeleteApi(URI id) {
        return apiTestTenants.getTestDeleteApi(id);
    }

    private String getUserWhoAmIApi() {
        return USER_WHOAMI_API;
    }

    private String getVdcTaskApi() {
        return VDC_TASKS_API;
    }

    private String getVdcTaskDeleteApi(URI taskId) {
        return String.format(VDC_TASK_DELETE_API, taskId.toString());
    }

    private String getDevUserGroupName() {
        return apiTestAuthnProviderUtils.getAttributeDepartmentValue(2);
    }

    private String getQeUserGroupName() {
        return apiTestAuthnProviderUtils.getAttributeDepartmentValue(1);
    }

    private String getManageUserGroupName() {
        return apiTestAuthnProviderUtils.getAttributeDepartmentValue(3);
    }

    private String getDeparmentNumberAttribute() {
        return apiTestAuthnProviderUtils.getAttributeKey(0);
    }

    private String getSecurityAdminRole() {
        return SECURITY_ADMIN_ROLE;
    }

    private String getSystemAdminRole() {
        return SYSTEM_ADMIN_ROLE;
    }

    private String getTenantAdminRole() {
        return TENANT_ADMIN_ROLE;
    }

    private String getSubTenantName() {
        return SUBTENANT_NAME;
    }

    private String getTenantAclApi(URI tenantId) {
        return String.format(TENANT_ACL_API, tenantId.toString());
    }

    private String getSubTenantGroup(int index) {
        return apiTestAuthnProviderUtils.getLDAPGroup(index);
    }

    private String getProviderTenantAdminGroup() {
        return apiTestAuthnProviderUtils.getLDAPGroup(4);
    }

    private String getSecurityAdminGroup() {
        return getDevUserGroupName();
    }

    private String getSystemAdminGroup() {
        return getQeUserGroupName();
    }

    private String getSubTenantAdminWithDomain() {
        return apiTestAuthnProviderUtils.getLDAPUser(0) + "@" + getAuthnProviderDomain();
    }

    private String getSubTenantUserWithDomain() {
        return apiTestAuthnProviderUtils.getLDAPUser(1) + "@" + getAuthnProviderDomain();
    }

    private String getSecurityAdminWithDomain() {
        return apiTestAuthnProviderUtils.getLDAPUser(4) + "@" + getAuthnProviderDomain();
    }

    private String getSystemAdminWithDomain() {
        return apiTestAuthnProviderUtils.getLDAPUser(5) + "@" + getAuthnProviderDomain();
    }

    private String getProviderTenantAdminWithDomain() {
        return apiTestAuthnProviderUtils.getLDAPUser(8) + "@" + getAuthnProviderDomain();
    }

    private String getProviderTenantUserWithDomain() {
        return apiTestAuthnProviderUtils.getLDAPUser(7) + "@" + getAuthnProviderDomain();
    }

    private String getVdcRoleAssignmentApi() {
        return VDC_ROLE_ASSIGNMENT_API;
    }

    private String getLDAPUserPassword() {
        return apiTestAuthnProviderUtils.getLDAPUserPassword();
    }

    private BalancedWebResource loginUser(String userName) throws NoSuchAlgorithmException {
        BalancedWebResource user = getHttpsClient(userName, getLDAPUserPassword());
        ClientResponse clientResponse = user.path(getUserWhoAmIApi()).get(ClientResponse.class);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        return user;
    }

    private URI getSubTenantId() throws NoSuchAlgorithmException {
        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        ClientResponse clientResponse = subTenantAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        TenantResponse subTenant = clientResponse.getEntity(TenantResponse.class);
        return subTenant.getTenant();
    }

    private List<URI> getTenantsFromAcls (List<ACLEntry> acls) {
        List<URI> tenantUris = new ArrayList<URI>();
        if (CollectionUtils.isEmpty(acls)) {
            return tenantUris;
        }

        Iterator<ACLEntry> aclEntryIterator = acls.iterator();
        while (aclEntryIterator.hasNext()) {
            ACLEntry acl = aclEntryIterator.next();
            tenantUris.add(URI.create(acl.getTenant()));
        }

        return tenantUris;
    }

    private List<URI> getVcenterIdsFromVcenterList (VcenterList vcenterList) {
        List<URI> vcenterUris = new ArrayList<URI>();
        if (vcenterList == null || CollectionUtils.isEmpty(vcenterList.getVcenters())) {
            return vcenterUris;
        }

        Iterator<NamedRelatedResourceRep> vcentersIterator = vcenterList.getVcenters().iterator();
        while (vcentersIterator.hasNext()) {
            NamedRelatedResourceRep vCenter = vcentersIterator.next();
            vcenterUris.add(vCenter.getId());
        }

        return vcenterUris;
    }

    private List<URI> getDataCenterIdsFromVcenterDataCenterList (VcenterDataCenterList dataCenterList) {
        List<URI> dataCenterUris = new ArrayList<URI>();
        if (dataCenterList == null || CollectionUtils.isEmpty(dataCenterList.getDataCenters())) {
            return dataCenterUris;
        }

        Iterator<NamedRelatedResourceRep> vcentersIterator = dataCenterList.getDataCenters().iterator();
        while (vcentersIterator.hasNext()) {
            NamedRelatedResourceRep dataCenter = vcentersIterator.next();
            dataCenterUris.add(dataCenter.getId());
        }

        return dataCenterUris;
    }

    private List<URI> getClusterIdsFromClusterList (ClusterList clusterList) {
        List<URI> clusterUris = new ArrayList<URI>();
        if (clusterList == null || CollectionUtils.isEmpty(clusterList.getClusters())) {
            return clusterUris;
        }
        Iterator<NamedRelatedResourceRep> vcentersIterator = clusterList.getClusters().iterator();
        while (vcentersIterator.hasNext()) {
            NamedRelatedResourceRep cluster = vcentersIterator.next();
            clusterUris.add(cluster.getId());
        }

        return clusterUris;
    }

    private List<URI> getHostIdsFromHostList (HostList hostList) {
        List<URI> hostUris = new ArrayList<URI>();
        if (hostList == null || CollectionUtils.isEmpty(hostList.getHosts())) {
            return hostUris;
        }

        Iterator<NamedRelatedResourceRep> vcentersIterator = hostList.getHosts().iterator();
        while (vcentersIterator.hasNext()) {
            NamedRelatedResourceRep host = vcentersIterator.next();
            hostUris.add(host.getId());
        }

        return hostUris;
    }

    private UserMappingParam createUserMappingParam(String group, String domain) {
        UserMappingParam param = new UserMappingParam();
        param.getGroups().add(group);
        param.setDomain(domain);

        return param;
    }

    private RoleAssignmentEntry getRoleAssignmentEntry(String entity, List<String> roles, boolean isGroup) {
        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();
        if (isGroup) {
            roleAssignmentEntry.setGroup(entity);
        } else {
            roleAssignmentEntry.setSubjectId(entity);
        }
        roleAssignmentEntry.getRoles().addAll(roles);

        return roleAssignmentEntry;
    }

    private RoleAssignmentChanges getDefaultVDCRoleAssignmentChanges() {
        return new RoleAssignmentChanges();
    }

    private URI createDefaultAuthnProvider(String description) {
        // Create a default authnprovider.
        AuthnCreateParam authnProviderCreateParam = apiTestAuthnProviderUtils.getDefaultAuthnCreateParam(description);

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getAuthnProviderCreateApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        AuthnProviderRestRep resp = clientAuthnProviderCreateResp.getEntity(AuthnProviderRestRep.class);
        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(resp, clientAuthnProviderCreateResp.getStatus());
        Iterator<String> it = authnProviderCreateParam.getDomains().iterator();
        while (it.hasNext()) {
            setAuthnProviderDomain(it.next());
            break;
        }

        return resp.getId();
    }

    private void createUserGroup(String domain, String key, String value) {
        UserAttributeParam userAttributeParam = new UserAttributeParam();
        userAttributeParam.setKey(key);
        userAttributeParam.getValues().add(value);

        UserGroupCreateParam param = new UserGroupCreateParam();
        param.setLabel(value);
        param.setDomain(domain);
        param.getAttributes().add(userAttributeParam);

        ClientResponse clientResponse = rSys.path(getUserGroupApi()).post(ClientResponse.class, param);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        UserGroupRestRep userGroupRestRep = clientResponse.getEntity(UserGroupRestRep.class);
        Assert.assertNotNull(userGroupRestRep);

        // Add the created tenant to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getUserGroupApiWithId(userGroupRestRep.getId());
        CleanupResource tenantToCleanup = new CleanupResource("delete", deleteObjectURL, rSys, null);

        apiTestVcenter.registerEnvironmentResourceForCleanup(tenantToCleanup);
    }

    private void updateProviderTenantAndVdcRoles() {
        createUserGroup(getAuthnProviderDomain(), getDeparmentNumberAttribute(), getSecurityAdminGroup());
        createUserGroup(getAuthnProviderDomain(), getDeparmentNumberAttribute(), getSystemAdminGroup());
        createUserGroup(getAuthnProviderDomain(), getDeparmentNumberAttribute(), getManageUserGroupName());

        UserMappingParam providerTenantAdmin = createUserMappingParam(getProviderTenantAdminGroup(), getAuthnProviderDomain());
        UserMappingParam securityAdmin = createUserMappingParam(getSecurityAdminGroup(), getAuthnProviderDomain());
        UserMappingParam systemAdmin = createUserMappingParam(getSystemAdminGroup(), getAuthnProviderDomain());
        UserMappingParam providerTenantUser = createUserMappingParam(getManageUserGroupName(), getAuthnProviderDomain());

        UserMappingChanges userMappingChanges = new UserMappingChanges();
        userMappingChanges.getAdd().add(providerTenantAdmin);
        userMappingChanges.getAdd().add(securityAdmin);
        userMappingChanges.getAdd().add(systemAdmin);
        userMappingChanges.getAdd().add(providerTenantUser);

        TenantUpdateParam updateParam = new TenantUpdateParam();
        updateParam.setUserMappingChanges(userMappingChanges);

        ClientResponse clientResponse = rSys.path(getTenantEditApi(rootTenantId)).put(ClientResponse.class, updateParam);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        List<String> tenantAdminRoles = new ArrayList<String>();
        tenantAdminRoles.add(getTenantAdminRole());
        RoleAssignmentEntry providerTenantAdminRoleEntry = getRoleAssignmentEntry(getProviderTenantAdminWithDomain(), tenantAdminRoles, false);

        RoleAssignmentChanges providerTenantRoleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        providerTenantRoleAssignmentChanges.getAdd().add(providerTenantAdminRoleEntry);

        clientResponse = rSys.path(getTenantAclApi(rootTenantId)).put(ClientResponse.class, providerTenantRoleAssignmentChanges);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        List<String> securityAdminRoles = new ArrayList<String>();
        securityAdminRoles.add(getSecurityAdminRole());
        RoleAssignmentEntry securityAdminRoleEntry = getRoleAssignmentEntry(getSecurityAdminWithDomain(), securityAdminRoles, false);

        RoleAssignmentChanges securityAdminRoleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        securityAdminRoleAssignmentChanges.getAdd().add(securityAdminRoleEntry);

        clientResponse = rSys.path(getVdcRoleAssignmentApi()).put(ClientResponse.class, securityAdminRoleAssignmentChanges);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        List<String> systemAdminRoles = new ArrayList<String>();
        systemAdminRoles.add(getSystemAdminRole());
        RoleAssignmentEntry systemAdminRoleEntry = getRoleAssignmentEntry(getSystemAdminWithDomain(), systemAdminRoles, false);

        RoleAssignmentChanges systemAdminRoleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        systemAdminRoleAssignmentChanges.getAdd().add(systemAdminRoleEntry);

        clientResponse = rSys.path(getVdcRoleAssignmentApi()).put(ClientResponse.class, systemAdminRoleAssignmentChanges);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());
    }

    private void cleanUpProviderTenantAndVdcRoles() {
        List<String> securityAdminRoles = new ArrayList<String>();
        securityAdminRoles.add(getSecurityAdminRole());
        RoleAssignmentEntry securityAdminRoleEntry = getRoleAssignmentEntry(getSecurityAdminWithDomain(), securityAdminRoles, false);

        RoleAssignmentChanges securityAdminRoleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        securityAdminRoleAssignmentChanges.getRemove().add(securityAdminRoleEntry);

        ClientResponse clientResponse = rSys.path(getVdcRoleAssignmentApi()).put(ClientResponse.class, securityAdminRoleAssignmentChanges);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        List<String> systemAdminRoles = new ArrayList<String>();
        systemAdminRoles.add(getSystemAdminRole());
        RoleAssignmentEntry systemAdminRoleEntry = getRoleAssignmentEntry(getSystemAdminWithDomain(), systemAdminRoles, false);

        RoleAssignmentChanges systemAdminRoleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        systemAdminRoleAssignmentChanges.getRemove().add(systemAdminRoleEntry);

        clientResponse = rSys.path(getVdcRoleAssignmentApi()).put(ClientResponse.class, systemAdminRoleAssignmentChanges);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        List<String> tenantAdminRoles = new ArrayList<String>();
        tenantAdminRoles.add(getTenantAdminRole());
        RoleAssignmentEntry providerTenantAdminRoleEntry = getRoleAssignmentEntry(getProviderTenantAdminWithDomain(), tenantAdminRoles, false);

        RoleAssignmentChanges providerTenantRoleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        providerTenantRoleAssignmentChanges.getRemove().add(providerTenantAdminRoleEntry);

        clientResponse = rSys.path(getTenantAclApi(rootTenantId)).put(ClientResponse.class, providerTenantRoleAssignmentChanges);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        UserMappingParam providerTenantAdmin = createUserMappingParam(getProviderTenantAdminGroup(), getAuthnProviderDomain());
        UserMappingParam securityAdmin = createUserMappingParam(getSecurityAdminGroup(), getAuthnProviderDomain());
        UserMappingParam systemAdmin = createUserMappingParam(getSystemAdminGroup(), getAuthnProviderDomain());
        UserMappingParam providerTenantUser = createUserMappingParam(getManageUserGroupName(), getAuthnProviderDomain());

        UserMappingChanges userMappingChanges = new UserMappingChanges();
        userMappingChanges.getRemove().add(providerTenantAdmin);
        userMappingChanges.getRemove().add(securityAdmin);
        userMappingChanges.getRemove().add(systemAdmin);
        userMappingChanges.getRemove().add(providerTenantUser);

        TenantUpdateParam updateParam = new TenantUpdateParam();
        updateParam.setUserMappingChanges(userMappingChanges);

        clientResponse = rSys.path(getTenantEditApi(rootTenantId)).put(ClientResponse.class, updateParam);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());
    }

    private void createTestTenantWithRoles() {
        TenantCreateParam createParam = new TenantCreateParam();
        createParam.setLabel(getSubTenantName());
        createParam.setDescription("Default sub tenant for vCenter test");

        UserMappingParam userMappingParam = new UserMappingParam();
        userMappingParam.setDomain(getAuthnProviderDomain());
        userMappingParam.getGroups().add(getSubTenantGroup(1));

        createParam.getUserMappings().add(userMappingParam);

        TenantOrgRestRep resp = rSys.path(getSubTenantCreateApi()).post(TenantOrgRestRep.class, createParam);
        Assert.assertNotNull(resp.getId());

        // Add the created tenant to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getTenantDeleteApi(resp.getId());
        CleanupResource tenantToCleanup = new CleanupResource("post", deleteObjectURL, rSys, null);

        apiTestVcenter.registerEnvironmentResourceForCleanup(tenantToCleanup);

        List<String> roles = new ArrayList<String>();
        roles.add(getTenantAdminRole());
        RoleAssignmentEntry role = getRoleAssignmentEntry(getSubTenantAdminWithDomain(), roles, false);

        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(role);

        ClientResponse clientResponse = rSys.path(getTenantAclApi(resp.getId())).put(ClientResponse.class, roleAssignmentChanges);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());
    }

    private void deleteTasks(TasksList tasks) throws NoSuchAlgorithmException{
        if (CollectionUtils.isEmpty(tasks.getTasks())) {
            return;
        }

        Iterator<NamedRelatedResourceRep> tasksIterator = tasks.getTasks().iterator();
        BalancedWebResource user = loginUser(getSystemAdminWithDomain());
        while (tasksIterator.hasNext()) {
            NamedRelatedResourceRep task = tasksIterator.next();
            if (task == null) {
                continue;
            }

            URI taskId = task.getId();
            ClientResponse clientResponse = user.path(getVdcTaskDeleteApi(taskId)).post(ClientResponse.class);
            Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());
        }
    }

    private void cleanUpTenantTasks() throws NoSuchAlgorithmException {
        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        ClientResponse clientResponse = providerTenantAdmin.path(getVdcTaskApi()).get(ClientResponse.class);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        TasksList providerTenentTaskList = clientResponse.getEntity(TasksList.class);
        deleteTasks(providerTenentTaskList);

        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        clientResponse = subTenantAdmin.path(getVdcTaskApi()).get(ClientResponse.class);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        TasksList subTenentTaskList = clientResponse.getEntity(TasksList.class);
        deleteTasks(subTenentTaskList);
    }

    private void testSetup() {
        createDefaultAuthnProvider("Default authn provider creating for vCenter tests");
        updateProviderTenantAndVdcRoles();
        createTestTenantWithRoles();
    }

    private void testTeardown() throws NoSuchAlgorithmException {
        cleanUpTenantTasks();
        cleanUpProviderTenantAndVdcRoles();
    }

    private URI createDefaultVcenter (BalancedWebResource user, int expectedStatus) {
        VcenterCreateParam param = new VcenterCreateParam();

        param.setName(getDefaultVcenterName());
        param.setIpAddress(getDefaultVcenterIp());
        param.setUserName(getDefaultVcenterUser());
        param.setPassword(getDefaultVcenterPassword());
        param.setUseSsl(true);
        param.setPortNumber(VCENTER_PORT);

        ClientResponse clientResponse = user.path(getVcenterApi()).post(ClientResponse.class, param);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK &&
                expectedStatus != HttpStatus.SC_ACCEPTED) {
            return NullColumnValueGetter.getNullURI();
        }

        TaskResourceRep taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
        Assert.assertNotNull(taskResourceRep.getResource().getId());

        // Add the created tenant to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getVcenterDeactivateApi(taskResourceRep.getResource().getId());
        CleanupResource tenantToCleanup = new CleanupResource("post", deleteObjectURL, rSys, null, HttpStatus.SC_ACCEPTED);

        registerResourceForCleanup(tenantToCleanup);

        while (taskResourceRep.getState().equalsIgnoreCase(TASK_STATUS_PENDING)) {
            clientResponse = user.path(getVdcTaskApi() + "/" + taskResourceRep.getId().toString()).get(ClientResponse.class);
            Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

            taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
            Assert.assertNotNull(taskResourceRep.getResource().getId());
        }

        return taskResourceRep.getResource().getId();
    }

    private URI createDefaultVcenterNoCleanUpRegister (BalancedWebResource user, int expectedStatus) {
        VcenterCreateParam param = new VcenterCreateParam();

        param.setName(getDefaultVcenterName());
        param.setIpAddress(getDefaultVcenterIp());
        param.setUserName(getDefaultVcenterUser());
        param.setPassword(getDefaultVcenterPassword());
        param.setUseSsl(true);
        param.setPortNumber(VCENTER_PORT);

        ClientResponse clientResponse = user.path(getVcenterApi()).post(ClientResponse.class, param);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK &&
                expectedStatus != HttpStatus.SC_ACCEPTED) {
            return NullColumnValueGetter.getNullURI();
        }

        TaskResourceRep taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
        Assert.assertNotNull(taskResourceRep.getResource().getId());

        while (taskResourceRep.getState().equalsIgnoreCase(TASK_STATUS_PENDING)) {
            clientResponse = user.path(getVdcTaskApi() + "/" + taskResourceRep.getId().toString()).get(ClientResponse.class);
            Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

            taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
            Assert.assertNotNull(taskResourceRep.getResource().getId());
        }

        return taskResourceRep.getResource().getId();
    }

    private URI editDefaultVcenter (BalancedWebResource user, URI vCenterId, int expectedStatus) {
        VcenterUpdateParam param = new VcenterUpdateParam();

        param.setName(getDefaultVcenterName());
        param.setIpAddress(getDefaultVcenterIp());
        param.setUserName(getDefaultVcenterUser());
        param.setPassword(getDefaultVcenterPassword());
        param.setUseSsl(true);
        param.setPortNumber(VCENTER_PORT);

        ClientResponse clientResponse = user.path(getVcenterApiWithId(vCenterId)).put(ClientResponse.class, param);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK &&
                expectedStatus != HttpStatus.SC_ACCEPTED) {
            return NullColumnValueGetter.getNullURI();
        }

        TaskResourceRep taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
        Assert.assertNotNull(taskResourceRep.getResource().getId());

        while (taskResourceRep.getState().equalsIgnoreCase(TASK_STATUS_PENDING)) {
            clientResponse = user.path(getVdcTaskApi() + "/" + taskResourceRep.getId().toString()).get(ClientResponse.class);
            Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

            taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
            Assert.assertNotNull(taskResourceRep.getResource().getId());
        }

        return taskResourceRep.getResource().getId();
    }

    private URI createVcenterByTenantApi (BalancedWebResource user, URI tenantId, int expectedStatus) {
        VcenterCreateParam param = new VcenterCreateParam();

        param.setName(getDefaultVcenterName());
        param.setIpAddress(getDefaultVcenterIp());
        param.setUserName(getDefaultVcenterUser());
        param.setPassword(getDefaultVcenterPassword());
        param.setUseSsl(true);

        ClientResponse clientResponse = user.path(getVcenterTenantApi(tenantId)).post(ClientResponse.class, param);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK &&
                expectedStatus != HttpStatus.SC_ACCEPTED) {
            return NullColumnValueGetter.getNullURI();
        }

        TaskResourceRep taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
        Assert.assertNotNull(taskResourceRep.getResource().getId());

        // Add the created tenant to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getVcenterDeactivateApi(taskResourceRep.getResource().getId());
        CleanupResource tenantToCleanup = new CleanupResource("post", deleteObjectURL, rSys, null, HttpStatus.SC_ACCEPTED);

        registerResourceForCleanup(tenantToCleanup);

        while (taskResourceRep.getState().equalsIgnoreCase(TASK_STATUS_PENDING)) {
            clientResponse = user.path(getVdcTaskApi() + "/" + taskResourceRep.getId().toString()).get(ClientResponse.class);
            Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

            taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
            Assert.assertNotNull(taskResourceRep.getResource().getId());
        }

        List<URI> aclEntries = getVcenterAcls(user, taskResourceRep.getResource().getId(), HttpStatus.SC_OK);
        Assert.assertTrue(aclEntries.contains(tenantId));

        return taskResourceRep.getResource().getId();
    }

    private void deactivateVcenter (BalancedWebResource user, URI vcenterId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getVcenterDeactivateApi(vcenterId))
                .queryParam("detach-storage", "true")
                .post(ClientResponse.class);

        Assert.assertEquals(expectedStatus, clientResponse.getStatus());
    }

    private void updateDataCenter (BalancedWebResource user, URI dataCenterId, URI tenantId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getDataCenterApiWithId(dataCenterId)).get(ClientResponse.class);
        Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

        VcenterDataCenterRestRep vcenterDataCenterRestRep = clientResponse.getEntity(VcenterDataCenterRestRep.class);
        Assert.assertNotNull(vcenterDataCenterRestRep);

        Assert.assertEquals(vcenterDataCenterRestRep.getId(), dataCenterId);

        VcenterDataCenterUpdate param = new VcenterDataCenterUpdate();
        param.setName(vcenterDataCenterRestRep.getName());
        param.setTenant(tenantId);

        clientResponse = user.path(getDataCenterApiWithId(dataCenterId)).put(ClientResponse.class, param);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus == HttpStatus.SC_OK) {
            vcenterDataCenterRestRep = clientResponse.getEntity(VcenterDataCenterRestRep.class);
            Assert.assertEquals(tenantId, vcenterDataCenterRestRep.getTenant().getId());
        }
    }

    private void addVcenterAcl (BalancedWebResource user, URI vCenterId, URI tenantId, int expectedStatus) {
        ACLAssignmentChanges param = new ACLAssignmentChanges();
        ACLEntry aclEntry = new ACLEntry();

        aclEntry.getAces().add(ACL.USE.name());
        aclEntry.setTenant(tenantId.toString());
        param.getAdd().add(aclEntry);

        ClientResponse clientResponse = user.path(getVcenterAclApi(vCenterId)).put(ClientResponse.class, param);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus == HttpStatus.SC_OK &&
                expectedStatus != HttpStatus.SC_ACCEPTED) {
            TaskResourceRep taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
            Assert.assertEquals(vCenterId, taskResourceRep.getResource().getId());

            clientResponse = user.path(getVcenterAclApi(vCenterId)).get(ClientResponse.class);
            Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

            ACLAssignments aclAssignments = clientResponse.getEntity(ACLAssignments.class);
            Assert.assertNotNull(aclAssignments);

            List<ACLEntry> aclEntries = aclAssignments.getAssignments();
            Assert.assertFalse(CollectionUtils.isEmpty(aclEntries));

            Assert.assertTrue(getTenantsFromAcls(aclEntries).contains(tenantId));

            while (taskResourceRep.getState().equalsIgnoreCase(TASK_STATUS_PENDING)) {
                clientResponse = user.path(getVdcTaskApi() + "/" + taskResourceRep.getId().toString()).get(ClientResponse.class);
                Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

                taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
                Assert.assertNotNull(taskResourceRep.getResource().getId());
            }
        }
    }

    private void removeVcenterAcl (BalancedWebResource user, URI vCenterId, List<URI> tenantIds, int expectedStatus) {
        if (CollectionUtils.isEmpty(tenantIds)) {
            return;
        }

        ACLAssignmentChanges param = new ACLAssignmentChanges();
        while (tenantIds.iterator().hasNext()) {
            URI tenantId = tenantIds.iterator().next();
            ACLEntry aclEntry = new ACLEntry();

            aclEntry.getAces().add(ACL.USE.name());
            aclEntry.setTenant(tenantId.toString());
            param.getRemove().add(aclEntry);
        }

        ClientResponse clientResponse = user.path(getVcenterAclApi(vCenterId))
                                                .queryParam("discover_vcenter", "false")
                                                .put(ClientResponse.class, param);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus == HttpStatus.SC_OK &&
                expectedStatus != HttpStatus.SC_ACCEPTED) {
            TaskResourceRep taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
            Assert.assertEquals(vCenterId, taskResourceRep.getResource().getId());

            clientResponse = user.path(getVcenterAclApi(vCenterId)).get(ClientResponse.class);
            Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

            ACLAssignments aclAssignments = clientResponse.getEntity(ACLAssignments.class);
            Assert.assertNotNull(aclAssignments);

            List<ACLEntry> aclEntries = aclAssignments.getAssignments();
            Assert.assertFalse(getTenantsFromAcls(aclEntries).containsAll(tenantIds));

            while (taskResourceRep.getState().equalsIgnoreCase(TASK_STATUS_PENDING)) {
                clientResponse = user.path(getVdcTaskApi() + "/" + taskResourceRep.getId().toString()).get(ClientResponse.class);
                Assert.assertEquals(HttpStatus.SC_OK, clientResponse.getStatus());

                taskResourceRep = clientResponse.getEntity(TaskResourceRep.class);
                Assert.assertNotNull(taskResourceRep.getResource().getId());
            }
        }
    }

    private List<URI> getVcenterAcls (BalancedWebResource user, URI vCenterId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getVcenterAclApi(vCenterId)).get(ClientResponse.class);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK) {
            return null;
        }

        ACLAssignments aclAssignments = clientResponse.getEntity(ACLAssignments.class);
        Assert.assertNotNull(aclAssignments);

        List<ACLEntry> aclEntries = aclAssignments.getAssignments();
        return getTenantsFromAcls(aclEntries);
    }

    private HostList getVcenterHosts (BalancedWebResource user, URI vCenterId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getVcenterHostsApi(vCenterId)).get(ClientResponse.class);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK) {
            return null;
        }

        HostList hostList = clientResponse.getEntity(HostList.class);
        Assert.assertNotNull(hostList);

        return hostList;
    }

    private ClusterList getVcenterClusters (BalancedWebResource user, URI vCenterId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getVcenterClustersApi(vCenterId)).get(ClientResponse.class);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK) {
            return null;
        }

        ClusterList clusterList = clientResponse.getEntity(ClusterList.class);
        Assert.assertNotNull(clusterList);

        return clusterList;
    }

    private VcenterDataCenterList getVcenterDataCenters (BalancedWebResource user, URI vCenterId,
                                                         URI tenantId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getVcenterDataCentersApi(vCenterId))
                                                .queryParam("tenant", tenantId.toString())
                                                .get(ClientResponse.class);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK) {
            return null;
        }

        VcenterDataCenterList dataCenterList = clientResponse.getEntity(VcenterDataCenterList.class);
        Assert.assertNotNull(dataCenterList);

        return dataCenterList;
    }

    private VcenterList getVcenters (BalancedWebResource user, int expectedStatus) {
        ClientResponse clientResponse = user.path(getVcenterApi()).get(ClientResponse.class);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK) {
            return null;
        }

        VcenterList vcenterList = clientResponse.getEntity(VcenterList.class);
        Assert.assertNotNull(vcenterList);

        return vcenterList;
    }

    private HostList getHostsByTenantApi(BalancedWebResource user, URI tenantId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getHostTenantApi(tenantId)).get(ClientResponse.class);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK) {
            return null;
        }

        HostList hostList = clientResponse.getEntity(HostList.class);
        Assert.assertNotNull(hostList);

        return hostList;
    }

    private ClusterList getClustersByTenantApi(BalancedWebResource user, URI tenantId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getClusterTenantApi(tenantId)).get(ClientResponse.class);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK) {
            return null;
        }

        ClusterList clusterList = clientResponse.getEntity(ClusterList.class);
        Assert.assertNotNull(clusterList);

        return clusterList;
    }

    private VcenterList getVcentersByTenantApi(BalancedWebResource user, URI tenantId, int expectedStatus) {
        ClientResponse clientResponse = user.path(getVcenterTenantApi(tenantId)).get(ClientResponse.class);
        Assert.assertEquals(expectedStatus, clientResponse.getStatus());

        if (expectedStatus != HttpStatus.SC_OK) {
            return null;
        }

        VcenterList vcenterList = clientResponse.getEntity(VcenterList.class);
        Assert.assertNotNull(vcenterList);

        return vcenterList;
    }

    // Function to validate the Authn provider creation and add resource to the cleanup list.
    private void validateAuthnProviderCreateSuccess(AuthnProviderRestRep resp, int status) {
        Assert.assertEquals(HttpStatus.SC_OK, status);

        // Add the created authnprovider to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getAuthnProviderDeleteApi(resp.getId());
        CleanupResource authnProviderToCleanup = new CleanupResource("delete", deleteObjectURL, rSys, null);

        apiTestVcenter.registerEnvironmentResourceForCleanup(authnProviderToCleanup);
    }

    /**
     * System admin creates and discovers the vCenter.
     * Also validating who call can edit it. Only System Admin can edit now
     * as it does not have any acls.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void createAndEditVcenterBySystemAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenter(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        vCenterId = editDefaultVcenter(systemAdmin, vCenterId, HttpStatus.SC_OK);

        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());

        editDefaultVcenter(providerTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN);
        editDefaultVcenter(subTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN);
        editDefaultVcenter(securityAdmin, vCenterId, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Tenant (provider) admin creates and discovers the vCenter.
     * Also validating who call can edit it. Only System Admin and
     * tenant admin of of the tenant who created the vCenter can edit.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void createAndEditVcenterBySubTenantAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(subTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(subTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertFalse(CollectionUtils.isEmpty(vCenterAcls));
        Assert.assertEquals(1, vCenterAcls.size());

        vCenterId = editDefaultVcenter(subTenantAdmin, vCenterId, HttpStatus.SC_OK);

        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());

        editDefaultVcenter(providerTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN);
        editDefaultVcenter(systemAdmin, vCenterId, HttpStatus.SC_OK);
        editDefaultVcenter(securityAdmin, vCenterId, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Tenant (sub) admin creates and discovers the vCenter.
     * Also validating who call can edit it. Only System Admin and
     * tenant admin of of the tenant who created the vCenter can edit.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void createAndEditVcenterByProviderTenantAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(providerTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(providerTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertFalse(CollectionUtils.isEmpty(vCenterAcls));
        Assert.assertEquals(1, vCenterAcls.size());

        vCenterId = editDefaultVcenter(providerTenantAdmin, vCenterId, HttpStatus.SC_OK);

        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());

        editDefaultVcenter(subTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN);
        editDefaultVcenter(systemAdmin, vCenterId, HttpStatus.SC_OK);
        editDefaultVcenter(securityAdmin, vCenterId, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Security admin cannot create vCenter.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void createVcenterBySecurityAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());
        createDefaultVcenter(securityAdmin, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Any tenant user cannot create vCenter.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void createVcenterByProviderTenantUser() throws NoSuchAlgorithmException {
        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        createDefaultVcenter(providerTenantUser, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Any tenant user cannot create vCenter.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void createVcenterBySubTenantUser() throws NoSuchAlgorithmException {
        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        createDefaultVcenter(subTenantUser, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * System admin creates and discovers the vCenter. Also,
     * System admin and Security admin adds the ACLs to the vCenter.
     * Tenant admin does not have privileges to update the vCenter's acls.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void createAndSetAclVcenterBySystemAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenter(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        addVcenterAcl(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());

        URI subTenantId = getSubTenantId();

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());

        addVcenterAcl(securityAdmin, vCenterId, subTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(2, vCenterAcls.size());

        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        addVcenterAcl(providerTenantAdmin, vCenterId, subTenantId, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Tenant admin creates and discovers the vCenter. When the vCenter is created
     * by a Tenant admin, by default that tenant has access to it. So the default
     * acl of that vCenter contains the tenant information. Also,
     * Security admin and System admin updates the vCenter acls.
     * Tenant admin does not privileges to update the vCenter's acls.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void createAndSetAclVcenterByTenantAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(subTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(subTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());

        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        addVcenterAcl(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(2, vCenterAcls.size());

        URI subTenantId = getSubTenantId();

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());

        addVcenterAcl(securityAdmin, vCenterId, subTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(2, vCenterAcls.size());

        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        addVcenterAcl(providerTenantAdmin, vCenterId, subTenantId, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Tenant admin creates and discovers a vCenter and the vCenter is
     * shared with multiple tenants, still system admin deactivates it.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void deactivateTenantVcenterBySystemAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenterNoCleanUpRegister(subTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(subTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());

        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        addVcenterAcl(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(2, vCenterAcls.size());

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        List<URI> subTenantVcenters = getVcenterIdsFromVcenterList(getVcentersByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(subTenantVcenters.contains(vCenterId));

        subTenantVcenters = getVcenterIdsFromVcenterList(getVcenters(subTenantUser, HttpStatus.SC_OK));
        Assert.assertTrue(subTenantVcenters.contains(vCenterId));

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        List<URI> providerTenantVcenters = getVcenterIdsFromVcenterList(getVcentersByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(providerTenantVcenters.contains(vCenterId));

        providerTenantVcenters = getVcenterIdsFromVcenterList(getVcenters(providerTenantUser, HttpStatus.SC_OK));
        Assert.assertTrue(providerTenantVcenters.contains(vCenterId));

        deactivateVcenter(systemAdmin, vCenterId, HttpStatus.SC_ACCEPTED);
    }

    /**
     * System admin creates and discovers the vCenter and shares the vCenter
     * with multiple tenants. When it is shared with multiple tenants, still
     * system admin can deactivate it.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void deactivateSystemVcenterBySystemAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenterNoCleanUpRegister(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        addVcenterAcl(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        List<URI> providerTenantVcenters = getVcenterIdsFromVcenterList(getVcentersByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(providerTenantVcenters.contains(vCenterId));

        providerTenantVcenters = getVcenterIdsFromVcenterList(getVcenters(providerTenantUser, HttpStatus.SC_OK));
        Assert.assertTrue(providerTenantVcenters.contains(vCenterId));

        deactivateVcenter(systemAdmin, vCenterId, HttpStatus.SC_ACCEPTED);
    }

    /**
     * System admin creates and discovers the vCenter and assigns the vCenter
     * to only one tenant. Since, the vCenter is shared with only one tenant
     * though it is created by a System admin, tenant admin can still deactivate
     * it.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void deactivateSystemCreatedVcenterByTenantAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenterNoCleanUpRegister(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        addVcenterAcl(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());

        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        deactivateVcenter(providerTenantAdmin, vCenterId, HttpStatus.SC_ACCEPTED);
    }

    /**
     * Tenant admin creates and discovers the vCenter and by default it
     * gets assigned to  the vCenter to the tenant who created it.
     * Since, the vCenter is shared with only one tenant,
     * tenant admin can deactivate it.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void deactivateTenantVcenterByTenantAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenterNoCleanUpRegister(subTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(subTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());

        deactivateVcenter(subTenantAdmin, vCenterId, HttpStatus.SC_ACCEPTED);
    }

    /**
     * System admin creates and discovers the vCenter and assigns the vCenter
     * to multiple tenants. Since, the vCenter is shared with multiple tenants
     * tenant admin can't deactivate it. Only System admin can deactivate it.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void deactivateSystemVcenterByTenantAdmin() throws NoSuchAlgorithmException {
        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(subTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(subTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());

        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        addVcenterAcl(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(2, vCenterAcls.size());

        deactivateVcenter(subTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Tenant Admin creates and discovers the vCenter by the tenant
     * flavour of vCenter creation api "/tenants/{id}/vcenters/"
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void tenantCreateByVcenterTenantApi() throws NoSuchAlgorithmException {
        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        URI vCenterId = createVcenterByTenantApi(subTenantAdmin, getSubTenantId(), HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(subTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());
        Assert.assertTrue(vCenterAcls.contains(getSubTenantId()));

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        List<URI> subTenantVcenters = getVcenterIdsFromVcenterList(getVcentersByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(subTenantVcenters.contains(vCenterId));

        subTenantVcenters = getVcenterIdsFromVcenterList(getVcenters(subTenantUser, HttpStatus.SC_OK));
        Assert.assertTrue(subTenantVcenters.contains(vCenterId));
    }

    /**
     * System admin creates and discovers the vCenter. Since the vCenter
     * is not shared with any tenant at the time of system admin creating it,
     * the vCenterDataCenters or Cluster or Hosts of the vCenter is not
     * assigned to any tenant.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void testSystemVcentersDataCenters() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenter(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        List<URI> dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(systemAdmin, vCenterId, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(systemAdmin, vCenterId, URI.create(NO_TENANT_FILTER), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(systemAdmin, vCenterId, URI.create(NO_TENANTS_ASSIGNED), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));
    }

    /**
     * Tenant admin creates and discovers the vCenter. Since the vCenter
     * is assigned to the tenant who creates it by default, all
     * the vCenterDataCenters or Cluster or Hosts of the vCenter is
     * assigned to that tenant.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void testTenantVcentersDataCenters() throws NoSuchAlgorithmException {
        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(providerTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(providerTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertFalse(CollectionUtils.isEmpty(vCenterAcls));
        Assert.assertEquals(1, vCenterAcls.size());

        List<URI> dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(providerTenantAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(providerTenantAdmin, vCenterId, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());
        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(securityAdmin, vCenterId, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(providerTenantAdmin, vCenterId, URI.create(NO_TENANT_FILTER), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(securityAdmin, vCenterId, URI.create(NO_TENANT_FILTER), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(providerTenantAdmin, vCenterId, URI.create(NO_TENANTS_ASSIGNED), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(securityAdmin, vCenterId, URI.create(NO_TENANTS_ASSIGNED), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(dataCenters));
    }

    /**
     * System admin creates and discovers the vCenter. Since the vCenter
     * is not shared with any tenant at the time of system admin creating it,
     * the vCenterDataCenters or Cluster or Hosts of the vCenter is not
     * assigned to any tenant.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void testSystemVcenterClusters() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenter(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        List<URI> clusters = getClusterIdsFromClusterList(getVcenterClusters(systemAdmin, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));
    }

    /**
     * Tenant admin creates and discovers the vCenter. Since the vCenter
     * is assigned to the tenant who creates it by default, all
     * the vCenterDataCenters or Cluster or Hosts of the vCenter is
     * assigned to that tenant.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void testTenantVcenterClusters() throws NoSuchAlgorithmException {
        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(providerTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(providerTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertFalse(CollectionUtils.isEmpty(vCenterAcls));
        Assert.assertEquals(1, vCenterAcls.size());

        List<URI> clusters = getClusterIdsFromClusterList(getVcenterClusters(providerTenantAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        clusters = getClusterIdsFromClusterList(getVcenterClusters(subTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());
        clusters = getClusterIdsFromClusterList(getVcenterClusters(securityAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));
    }

    /**
     * System admin creates and discovers the vCenter. Since the vCenter
     * is not shared with any tenant at the time of system admin creating it,
     * the vCenterDataCenters or Cluster or Hosts of the vCenter is not
     * assigned to any tenant.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void testSystemVcenterHosts() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenter(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        List<URI> hosts = getHostIdsFromHostList(getVcenterHosts(systemAdmin, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        hosts = getHostIdsFromHostList(getHostsByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        hosts = getHostIdsFromHostList(getHostsByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));
    }

    /**
     * Tenant admin creates and discovers the vCenter. Since the vCenter
     * is assigned to the tenant who creates it by default, all
     * the vCenterDataCenters or Cluster or Hosts of the vCenter is
     * assigned to that tenant.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void testTenantVcenterHosts() throws NoSuchAlgorithmException {
        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(providerTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(providerTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertFalse(CollectionUtils.isEmpty(vCenterAcls));
        Assert.assertEquals(1, vCenterAcls.size());

        List<URI> hosts = getHostIdsFromHostList(getVcenterHosts(providerTenantAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        hosts = getHostIdsFromHostList(getVcenterHosts(subTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        hosts = getHostIdsFromHostList(getHostsByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());
        hosts = getHostIdsFromHostList(getVcenterHosts(securityAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        hosts = getHostIdsFromHostList(getHostsByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));
    }

    /**
     * System admin creates and discovers the vCenter. So, vCenter is not shared
     * with any tenants. Now, try to assign a tenant to the vCenterDataCenter
     * and this should return back an error.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void updateDataCenterTenantWhenVcenterHaveNoTenants() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenter(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        List<URI> dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(providerTenantUser, vCenterId, rootTenantId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(dataCenters));

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(subTenantUser, vCenterId, getSubTenantId(), HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(systemAdmin, vCenterId, URI.create(NO_TENANT_FILTER), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());

        Iterator<URI> dataCenterIds = dataCenters.iterator();
        while(dataCenterIds.hasNext()) {
            updateDataCenter(securityAdmin, dataCenterIds.next(), rootTenantId, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * System admin creates and discovers the vCenter. So, vCenter is not shared
     * with any tenants. Now, share the vCenter with one tenant and
     * try to assign that same  tenant to the vCenterDataCenter
     * and this should be successful.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void updateDataCenterTenant() throws NoSuchAlgorithmException {
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenter(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        addVcenterAcl(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(1, vCenterAcls.size());

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        List<URI> dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(providerTenantUser, vCenterId, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(dataCenters));

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(subTenantUser, vCenterId, getSubTenantId(), HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(dataCenters));

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(systemAdmin, vCenterId, URI.create(NO_TENANT_FILTER), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());

        Iterator<URI> dataCenterIds = dataCenters.iterator();
        while(dataCenterIds.hasNext()) {
            updateDataCenter(securityAdmin, dataCenterIds.next(), rootTenantId, HttpStatus.SC_OK);
        }

        dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(providerTenantUser, vCenterId, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        List<URI> hosts = getHostIdsFromHostList(getHostsByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        hosts = getHostIdsFromHostList(getVcenterHosts(providerTenantUser, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        hosts = getHostIdsFromHostList(getHostsByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        hosts = getHostIdsFromHostList(getVcenterHosts(subTenantUser, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        List<URI> clusters = getClusterIdsFromClusterList(getClustersByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        clusters = getClusterIdsFromClusterList(getVcenterClusters(providerTenantUser, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));

        clusters = getClusterIdsFromClusterList(getVcenterClusters(subTenantUser, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));
    }

    /**
     * Tenant admin creates and discovers the vCenter. So, vCenter is assigned
     * to that tenant. Since the vCenter was discovered by the tenant admin,
     * all the vCenterDataCenters are assigned to that same tenant.
     * Now, share the vCenter with multiple tenants and try to change the
     * vCenterDataCenter's tenant infomation. This should be successful.
     *
     * @throws NoSuchAlgorithmException
     */
    //@Test
    public void updateTenantDataCentersTenant() throws NoSuchAlgorithmException {
        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(providerTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(providerTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertFalse(CollectionUtils.isEmpty(vCenterAcls));
        Assert.assertEquals(1, vCenterAcls.size());

        List<URI> hosts = getHostIdsFromHostList(getVcenterHosts(providerTenantAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        hosts = getHostIdsFromHostList(getVcenterHosts(subTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        BalancedWebResource providerTenantUser = loginUser(getProviderTenantUserWithDomain());
        hosts = getHostIdsFromHostList(getHostsByTenantApi(providerTenantUser, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());
        hosts = getHostIdsFromHostList(getVcenterHosts(securityAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        BalancedWebResource subTenantUser = loginUser(getSubTenantUserWithDomain());
        hosts = getHostIdsFromHostList(getHostsByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        List<URI> dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(providerTenantAdmin, vCenterId, URI.create(NO_TENANT_FILTER), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        addVcenterAcl(securityAdmin, vCenterId, getSubTenantId(), HttpStatus.SC_OK);

        Iterator<URI> dataCenterIds = dataCenters.iterator();
        while(dataCenterIds.hasNext()) {
            URI dataCenterURI = dataCenterIds.next();
            updateDataCenter(providerTenantAdmin, dataCenterURI, getSubTenantId(), HttpStatus.SC_FORBIDDEN);
            updateDataCenter(securityAdmin, dataCenterURI, getSubTenantId(), HttpStatus.SC_OK);
        }

        hosts = getHostIdsFromHostList(getHostsByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        hosts = getHostIdsFromHostList(getVcenterHosts(subTenantUser, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        List<URI> clusters = getClusterIdsFromClusterList(getClustersByTenantApi(subTenantUser, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        clusters = getClusterIdsFromClusterList(getVcenterClusters(subTenantUser, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        hosts = getHostIdsFromHostList(getVcenterHosts(securityAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));
    }

    /*
    These below tests are problematic because of vCenter discovery refresh rate.
    If we second discover happens with 60secs of first discovery, the second disccvery
    will not even run. But, here we would need that.
    */

    //@Test
    public void removeTenantCreatedVcentersAcl() throws NoSuchAlgorithmException {
        // Create a System Admin user and create a vCenter by that System Admin user.
        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        URI vCenterId = createDefaultVcenter(providerTenantAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(providerTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertFalse(CollectionUtils.isEmpty(vCenterAcls));
        Assert.assertEquals(1, vCenterAcls.size());

        List<URI> hosts = getHostIdsFromHostList(getHostsByTenantApi(providerTenantAdmin, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        hosts = getHostIdsFromHostList(getVcenterHosts(providerTenantAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        List<URI> clusters = getClusterIdsFromClusterList(getClustersByTenantApi(providerTenantAdmin, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        clusters = getClusterIdsFromClusterList(getVcenterClusters(providerTenantAdmin, vCenterId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        BalancedWebResource securityAdmin = loginUser(getSecurityAdminWithDomain());

        List<URI> tenantIds = new ArrayList<URI>();
        tenantIds.add(rootTenantId);
        removeVcenterAcl(securityAdmin, vCenterId, tenantIds, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(providerTenantAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        hosts = getHostIdsFromHostList(getHostsByTenantApi(providerTenantAdmin, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        hosts = getHostIdsFromHostList(getVcenterHosts(providerTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(providerTenantAdmin, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));

        clusters = getClusterIdsFromClusterList(getVcenterClusters(providerTenantAdmin, vCenterId, HttpStatus.SC_FORBIDDEN));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));
    }

    //@Test
    public void removeSystemCreatedVcentersAcl() throws NoSuchAlgorithmException {
        // Create a System Admin user and create a vCenter by that System Admin user.
        BalancedWebResource systemAdmin = loginUser(getSystemAdminWithDomain());
        URI vCenterId = createDefaultVcenter(systemAdmin, HttpStatus.SC_ACCEPTED);

        List<URI> vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertTrue(CollectionUtils.isEmpty(vCenterAcls));

        addVcenterAcl(systemAdmin, vCenterId, rootTenantId, HttpStatus.SC_OK);
        addVcenterAcl(systemAdmin, vCenterId, getSubTenantId(), HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(2, vCenterAcls.size());

        List<URI> dataCenters = getDataCenterIdsFromVcenterDataCenterList(getVcenterDataCenters(systemAdmin, vCenterId, URI.create(NO_TENANT_FILTER), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(dataCenters));

        Iterator<URI> dataCenterIds = dataCenters.iterator();
        boolean providerTenant = false;
        while(dataCenterIds.hasNext()) {
            URI dataCenterURI = dataCenterIds.next();
            if (providerTenant) {
                updateDataCenter(systemAdmin, dataCenterURI, rootTenantId, HttpStatus.SC_OK);
                providerTenant = false;
            } else {
                updateDataCenter(systemAdmin, dataCenterURI, getSubTenantId(), HttpStatus.SC_OK);
                providerTenant = true;
            }
        }

        BalancedWebResource providerTenantAdmin = loginUser(getProviderTenantAdminWithDomain());
        List<URI> hosts = getHostIdsFromHostList(getHostsByTenantApi(providerTenantAdmin, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        List<URI> clusters = getClusterIdsFromClusterList(getClustersByTenantApi(providerTenantAdmin, rootTenantId, HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        BalancedWebResource subTenantAdmin = loginUser(getSubTenantAdminWithDomain());
        hosts = getHostIdsFromHostList(getHostsByTenantApi(subTenantAdmin, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(hosts));

        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(subTenantAdmin, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertFalse(CollectionUtils.isEmpty(clusters));

        List<URI> tenantIds = new ArrayList<URI>();
        tenantIds.add(rootTenantId);
        tenantIds.add(getSubTenantId());

        removeVcenterAcl(systemAdmin, vCenterId, tenantIds, HttpStatus.SC_OK);

        vCenterAcls = getVcenterAcls(systemAdmin, vCenterId, HttpStatus.SC_OK);
        Assert.assertEquals(0, vCenterAcls.size());

        hosts = getHostIdsFromHostList(getHostsByTenantApi(providerTenantAdmin, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(providerTenantAdmin, rootTenantId, HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));

        hosts = getHostIdsFromHostList(getHostsByTenantApi(subTenantAdmin, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(hosts));

        clusters = getClusterIdsFromClusterList(getClustersByTenantApi(subTenantAdmin, getSubTenantId(), HttpStatus.SC_OK));
        Assert.assertTrue(CollectionUtils.isEmpty(clusters));
    }
}