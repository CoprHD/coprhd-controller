/*
 * Copyright (c) 2011-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.ldap.exceptions.DirectoryOrFileNotFoundException;
import com.emc.storageos.api.ldap.exceptions.FileOperationFailedException;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.model.auth.RoleAssignments;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.model.file.policy.FilePolicyParam;
import com.emc.storageos.model.file.policy.FilePolicyScheduleParams;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyParam;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.model.tenant.TenantUpdateParam;
import com.emc.storageos.model.tenant.UserMappingAttributeParam;
import com.emc.storageos.model.tenant.UserMappingChanges;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.model.user.UserInfo;
import com.sun.jersey.api.client.ClientResponse;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFException;

/**
 * 
 * FilePolicyTest class to exercise the core api functionality of File Policy Service.
 */
public class FilePolicyApiTest extends ApiTestBase {
    private static final String CREATE_FILE_POLICY = "/file/file-policies";

    private static final String ERROR_INVALID_VALUE = "Invalid value";
    private static final String ERROR_INSUFFICIENT_PERMISSION_FOR_USER = "Insufficient permissions for user %s";
    private static final String TRACE_SUCCESSFUL_TENANT_CREAETION_WITH_GROUP_ONLY = "Successful creation of tenant with group only";

    private static final String DEFAULT_TEST_TENANT_LABEL = "TestTenant";
    @SuppressWarnings("unused")
    private static final String DEFAULT_TEST_TENANT_DESCRIPTION = "Tenant Provided by LDAP Authenticate Provider";
    private static final String DEFAULT_TEST_TENANT_AUTHN_PROVIDER_DESCRIPTION = "Authenticate Provider created for Api Tenant tests";

    private static final String[] DEFAULT_TEST_TENANT_ROLES = { "TENANT_ADMIN", "PROJECT_ADMIN", "TENANT_APPROVER" };

    private List<CleanupResource> _cleanupResourceList = null;
    private ApiTestAuthnProviderUtils apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();

    private static FilePolicyApiTest apiTestAuthnTenants = new FilePolicyApiTest();

    @BeforeClass
    public static void setupTestSuite() throws LDIFException,
            LDAPException, IOException, FileOperationFailedException,
            GeneralSecurityException, DirectoryOrFileNotFoundException, InterruptedException {
        apiTestAuthnTenants.apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();
        apiTestAuthnTenants.apiTestAuthnProviderUtils.startLdapServer(FilePolicyApiTest.class.getSimpleName());
    }

    @AfterClass
    public static void tearDownTestSuite() {
        apiTestAuthnTenants.apiTestAuthnProviderUtils.stopLdapServer();
    }

    @Before
    public void setUp() throws Exception {
        initLoadBalancer(true);
        setupLicenseAndInitialPasswords();

        _cleanupResourceList = new LinkedList<CleanupResource>();
        apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();
    }

    @After
    public void tearDown() {
        CleanupResource.cleanUpTestResources(_cleanupResourceList);
        // tearDownHttpsResources();
    }

    private void registerResourceForCleanup(CleanupResource resource) {
        if (_cleanupResourceList == null) {
            _cleanupResourceList = new LinkedList<CleanupResource>();
        }

        _cleanupResourceList.add(resource);
    }

    private String getTestDomainName() {
        return apiTestAuthnProviderUtils.getAuthnProviderDomain();
    }

    private String getTestDefaultAuthnProviderDescription() {
        return DEFAULT_TEST_TENANT_AUTHN_PROVIDER_DESCRIPTION;
    }

    private String getTestAuthnProviderApi() {
        return apiTestAuthnProviderUtils.getAuthnProviderBaseURL();
    }

    private String getAuthnProviderDeleteApi(URI uri) {
        return apiTestAuthnProviderUtils.getAuthnProviderEditURL(uri);
    }

    private String getChild1User(int index) {
        return apiTestAuthnProviderUtils.getChild1User(index);
    }

    private String getChild1Group(int index) {
        return apiTestAuthnProviderUtils.getChild1Group(index);
    }

    private String getChild2Group(int index) {
        return apiTestAuthnProviderUtils.getChild2Group(index);
    }

    private String getChild1Domain() {
        return apiTestAuthnProviderUtils.getChild1Domain();
    }

    private String getChild2Domain() {
        return apiTestAuthnProviderUtils.getChild2Domain();
    }

    private UserMappingAttributeParam getDefaultUserMappingAttributeParam(int index) {
        UserMappingAttributeParam param = new UserMappingAttributeParam();
        final String preAttr = "Attr";
        String attr = preAttr + index;

        List<String> values = new ArrayList<String>();
        values.add(preAttr + index + "_Value1");
        values.add(preAttr + index + "_Value2");
        values.add(preAttr + index + "_Value1");

        param.setKey(attr);
        param.getValues().addAll(values);

        return param;
    }

    private List<UserMappingAttributeParam> getDefaultUserMappingAttributeParamList(int numParams) {
        List<UserMappingAttributeParam> paramList = new ArrayList<UserMappingAttributeParam>();

        for (int i = 1; i <= numParams; i++) {
            paramList.add(getDefaultUserMappingAttributeParam(i));
        }

        return paramList;
    }

    private UserMappingParam getDefaultUserMappingParam() {
        UserMappingParam param = new UserMappingParam();
        param.setDomain(getTestDomainName());
        param.getGroups().add(apiTestAuthnProviderUtils.getLDAPGroup(2));

        return param;
    }

    private List<UserMappingParam> getDefaultUserMappingParamList() {
        List<UserMappingParam> paramList = new ArrayList<UserMappingParam>();
        paramList.add(getDefaultUserMappingParam());

        return paramList;
    }

    public TenantCreateParam getDefaultTenantCreateParam(String description) {
        TenantCreateParam param = new TenantCreateParam();
        param.setLabel(DEFAULT_TEST_TENANT_LABEL);
        param.setDescription(description);
        param.setUserMappings(getDefaultUserMappingParamList());

        return param;
    }

    private List<String> getDefaultTenantRoles() {
        List<String> roles = new ArrayList<String>(Arrays.asList(DEFAULT_TEST_TENANT_ROLES));

        return roles;
    }

    private String getTenantRole(int index) {
        return DEFAULT_TEST_TENANT_ROLES[index];
    }

    private RoleAssignmentEntry getRoleAssignmentEntryForGroup(List<String> roles, String group) {
        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();
        roleAssignmentEntry.getRoles().addAll(roles);
        roleAssignmentEntry.setGroup(group);

        return roleAssignmentEntry;
    }

    private RoleAssignmentEntry getRoleAssignmentEntryForSubjectId(List<String> roles, String subjectId) {
        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();
        roleAssignmentEntry.getRoles().addAll(roles);
        roleAssignmentEntry.setSubjectId(subjectId);

        return roleAssignmentEntry;
    }

    private RoleAssignmentChanges getDefaultRoleAssignmentChanges(boolean isGroup, boolean isWithDomain) {
        RoleAssignmentEntry roleAssignmentEntryParam = null;
        if (isGroup) {
            if (isWithDomain) {
                roleAssignmentEntryParam = getRoleAssignmentEntryForGroup(getDefaultTenantRoles(), getGroupWithDomain(2));
            } else {
                roleAssignmentEntryParam = getRoleAssignmentEntryForGroup(getDefaultTenantRoles(), getGroup(2));
            }
        } else {
            if (isWithDomain) {
                roleAssignmentEntryParam = getRoleAssignmentEntryForSubjectId(getDefaultTenantRoles(), getUserWithDomain(1));
            } else {
                roleAssignmentEntryParam = getRoleAssignmentEntryForSubjectId(getDefaultTenantRoles(), getUser(1));
            }
        }

        RoleAssignmentChanges roleAssignmentChanges = new RoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntryParam);

        return roleAssignmentChanges;
    }

    public ProjectParam getDefaultProjectParam(String projectName) {
        ProjectParam param = new ProjectParam();
        param.setName(projectName);

        return param;
    }

    private void addUserMapping(URI tenantId, String group) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(getTestDomainName());
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getAdd().add(rootMapping);

    }

    private void addUserMapping(URI tenantId, String group, String domain) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(domain);
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getAdd().add(rootMapping);

    }

    private void addUserMappingAndExpectFailure(URI tenantId, String group, BalancedWebResource user) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(getTestDomainName());
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getAdd().add(rootMapping);

    }

    private void removeUserMapping(URI tenantId, String group) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(getTestDomainName());
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getRemove().add(rootMapping);

    }

    private AuthnCreateParam getDefaultAuthnCreateParam(String description) {
        return apiTestAuthnProviderUtils.getDefaultAuthnCreateParam(description);
    }

    private String getGroup(int index) {
        return apiTestAuthnProviderUtils.getLDAPGroup(index);
    }

    private String getGroupWithDomain(int index) {
        return getGroup(index) + "@" + getTestDomainName();
    }

    private String getUserPassword() {
        return apiTestAuthnProviderUtils.getLDAPUserPassword();
    }

    private String getUser(int index) {
        return apiTestAuthnProviderUtils.getLDAPUser(index);
    }

    private String getUserWithDomain(int index) {
        return apiTestAuthnProviderUtils.getUserWithDomain(index);
    }

    private String getGroupObjectClass(int index) {
        return apiTestAuthnProviderUtils.getGroupObjectClass(index);
    }

    private String getGroupMemberAttribute(int index) {
        return apiTestAuthnProviderUtils.getGroupMemberAttribute(index);
    }

    private String getLDAPUserPassword() {
        return apiTestAuthnProviderUtils.getLDAPUserPassword();
    }

    private TenantUpdateParam getTenantUpdateParamFromTenantCreateRestResp(TenantOrgRestRep createResponse) {
        TenantUpdateParam param = new TenantUpdateParam();
        param.setLabel(createResponse.getName());
        param.setDescription(createResponse.getDescription());

        UserMappingChanges userMappingChagnes = new UserMappingChanges();
        userMappingChagnes.getAdd().addAll(createResponse.getUserMappings());
        userMappingChagnes.getRemove().addAll(new ArrayList<UserMappingParam>());

        param.setUserMappingChanges(userMappingChagnes);

        return param;
    }

    // Function to validate the Authn provider creation and add resource to the cleanup list.
    private void validateAuthnProviderCreateSuccess(ClientResponse clientResp) {
        Assert.assertEquals(HttpStatus.SC_OK, clientResp.getStatus());

        AuthnProviderRestRep resp = clientResp.getEntity(AuthnProviderRestRep.class);

        // Add the created authnprovider to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getAuthnProviderDeleteApi(resp.getId());
        CleanupResource authnProviderToCleanup = new CleanupResource("delete", deleteObjectURL, rSys, null);

        registerResourceForCleanup(authnProviderToCleanup);
    }

    private void validateTenantEditSuccess(TenantUpdateParam expected, TenantOrgRestRep actual) {
        Assert.assertTrue(actual.getName().equalsIgnoreCase(expected.getLabel()));
        Assert.assertTrue(expected.getLabel().equalsIgnoreCase(actual.getName()));

        for (UserMappingParam expectedUserMappingParam : expected.getUserMappingChanges().getAdd()) {
            boolean userMappingsFound = false;
            for (UserMappingParam actualUserMappingParam : actual.getUserMappings()) {
                if (expectedUserMappingParam.getDomain().equalsIgnoreCase(actualUserMappingParam.getDomain()) &&
                        expectedUserMappingParam.getGroups().containsAll(actualUserMappingParam.getGroups())) {
                    userMappingsFound = true;
                }
            }
            Assert.assertTrue(userMappingsFound);
        }
    }

    private void validateTenantCreateAndEditBadRequest(int expectedStatus, String expectedErrorMsg, ClientResponse actual) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expectedStatus, actual.getStatus());

        final ServiceErrorRestRep actualErrorMsg = actual.getEntity(ServiceErrorRestRep.class);

        Assert.assertTrue(actualErrorMsg.getDetailedMessage().startsWith(expectedErrorMsg));
    }

    private RoleAssignmentEntry getRoleAssginementByGroup(String group, List<RoleAssignmentEntry> roleAssignmentEnties) {
        RoleAssignmentEntry roleAssignmentEntry = null;
        for (RoleAssignmentEntry actualRoleAssignmentEntry : roleAssignmentEnties) {
            if (StringUtils.isNotBlank(actualRoleAssignmentEntry.getGroup()) &&
                    group.equalsIgnoreCase(actualRoleAssignmentEntry.getGroup())) {
                roleAssignmentEntry = actualRoleAssignmentEntry;
                break;
            }
        }

        return roleAssignmentEntry;
    }

    private RoleAssignmentEntry getRoleAssginementBySubjectId(String subjectId, List<RoleAssignmentEntry> roleAssignmentEnties) {
        RoleAssignmentEntry roleAssignmentEntry = null;
        for (RoleAssignmentEntry actualRoleAssignmentEntry : roleAssignmentEnties) {
            if (StringUtils.isNotBlank(actualRoleAssignmentEntry.getSubjectId()) &&
                    subjectId.equalsIgnoreCase(actualRoleAssignmentEntry.getSubjectId())) {
                roleAssignmentEntry = actualRoleAssignmentEntry;
                break;
            }
        }

        return roleAssignmentEntry;
    }

    private void validateRoleAssignmentCreateSuccess(RoleAssignmentChanges expectedRoleAssignmentChanges, RoleAssignments actual) {
        for (RoleAssignmentEntry expectedRoleAssignment : expectedRoleAssignmentChanges.getAdd()) {
            if (StringUtils.isNotBlank(expectedRoleAssignment.getGroup())) {
                RoleAssignmentEntry actualRoleAssignmentEntry = getRoleAssginementByGroup(expectedRoleAssignment.getGroup(),
                        actual.getAssignments());

                Assert.assertNotNull(actualRoleAssignmentEntry);
                Assert.assertEquals(actualRoleAssignmentEntry.getRoles().size(), expectedRoleAssignment.getRoles().size());
            }

            if (StringUtils.isNotBlank(expectedRoleAssignment.getSubjectId())) {
                RoleAssignmentEntry actualRoleAssignmentEntry = getRoleAssginementBySubjectId(expectedRoleAssignment.getSubjectId(),
                        actual.getAssignments());

                Assert.assertNotNull(actualRoleAssignmentEntry);
                Assert.assertEquals(actualRoleAssignmentEntry.getRoles().size(), expectedRoleAssignment.getRoles().size());
            }
        }
    }

    private void validateRoleAssignmentBadRequest(int expectedStatus, String expectedErrorMsg, ClientResponse actual) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expectedStatus, actual.getStatus());

        final ServiceErrorRestRep actualErrorMsg = actual.getEntity(ServiceErrorRestRep.class);
        Assert.assertTrue(actualErrorMsg.getDetailedMessage().startsWith(expectedErrorMsg));
    }

    private void validateGetTenantSuccess(TenantOrgRestRep expected, TenantResponse actual) {
        Assert.assertEquals(expected.getId(), actual.getTenant());
    }

    private void validateGetProjectSuccess(String expectedProjectName, com.emc.storageos.model.project.ProjectList actual) {
        boolean projectFound = false;
        for (NamedRelatedResourceRep resource : actual.getProjects()) {
            if (expectedProjectName.equalsIgnoreCase(resource.getName())) {
                projectFound = true;
            }
        }

        Assert.assertTrue(projectFound);
    }

    private void validateUserTenantRoles(UserInfo actual, List<String> expectedRoles) {
        Assert.assertNotNull(actual);
        Assert.assertFalse(CollectionUtils.isEmpty(actual.getHomeTenantRoles()));
        Assert.assertTrue(actual.getHomeTenantRoles().containsAll(expectedRoles));
    }

    private void validateVDCRoleAssignmentsRemove(RoleAssignments actual, String expectedEntity,
            boolean isGroup) {
        Assert.assertNotNull(actual);

        boolean found = false;
        for (RoleAssignmentEntry roleAssignmentEntry : actual.getAssignments()) {
            Assert.assertNotNull(roleAssignmentEntry);
            if (isGroup) {
                if (expectedEntity.equalsIgnoreCase(roleAssignmentEntry.getGroup())) {
                    found = true;
                }
            } else {
                if (expectedEntity.equalsIgnoreCase(roleAssignmentEntry.getSubjectId())) {
                    found = true;
                }
            }
        }
        Assert.assertFalse(found);
    }

    @Test
    public void testFileSnapshotPolicyCreate() throws NoSuchAlgorithmException {
        final String testName = "testTenantEditToCreateRoleAssignment - ";

        FilePolicyParam param = new FilePolicyParam();
        param.setPolicyName("snapshot policy 1");
        param.setPolicyDescription("snapshot policy 1");
        param.setPolicyType(FilePolicyType.file_snapshot.name());
        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        policySchedule.setScheduleFrequency("DAYS");
        policySchedule.setScheduleRepeat(6L);
        policySchedule.setScheduleTime("12:00");
        param.setPolicySchedule(policySchedule);
        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();
        snapshotPolicyParam.setSnapshotNamePattern("snapshot policy 1");
        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();

        snapshotExpireParams.setExpireType(SnapshotExpireType.NEVER.toString());
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        ClientResponse createFilePolicyResp = rSys.path("/file/file-policies").post(ClientResponse.class,
                param);
        System.out.println(createFilePolicyResp.getClientResponseStatus().getReasonPhrase());

        Assert.assertEquals(200, createFilePolicyResp.getStatus());

    }
}
