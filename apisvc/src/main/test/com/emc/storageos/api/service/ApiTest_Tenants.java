/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2011-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.*;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.model.user.UserInfo;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * ApiTest_Tenants class to exercise the core api functionality of Tenants Service.
 */
public class ApiTest_Tenants extends ApiTestBase {
    private final String TEST_ROOT_TENANT_BASEURL = "/tenants/" + "%s";
    private final String TEST_API = "%s" + "/subtenants";
    private final String TEST_EDIT_API = TEST_ROOT_TENANT_BASEURL;
    private final String TEST_DELETE_API = TEST_ROOT_TENANT_BASEURL + "/deactivate";
    private final String TEST_ROLE_ASSIGNMENTS_API = TEST_ROOT_TENANT_BASEURL + "/role-assignments";
    private final String TEST_CREATE_PROJECT_API = TEST_ROOT_TENANT_BASEURL + "/projects";
    private final String TEST_GET_PROJECT_API = TEST_ROOT_TENANT_BASEURL + "/projects";
    private final String TEST_DELETE_PROJECT_API = "/projects/%s/deactivate";
    private final String TEST_GET_TENANT_API = "/tenant";
    private final String TEST_USER_WHOAMI_API = "/user/whoami";

    private final static String DEFAULT_TEST_TENANT_LABEL = "TestTenant";
    @SuppressWarnings("unused")
    private final static String DEFAULT_TEST_TENANT_DESCRIPTION = "Tenant Provided by LDAP Authenticate Provider";
    private final static String DEFAULT_TEST_TENANT_AUTHN_PROVIDER_DESCRIPTION = "Authenticate Provider created for Api Tenant tests";

    private final static String[] DEFAULT_TEST_TENANT_ROLES = { "TENANT_ADMIN", "PROJECT_ADMIN", "TENANT_APPROVER" };

    private LinkedList<CleanupResource> _cleanupResourceList = null;
    private ApiTest_AuthnProviderUtils apiTestAuthnProviderUtils = new ApiTest_AuthnProviderUtils();

    @Before
    public void setUp() throws Exception {
        setupHttpsResources();

        _cleanupResourceList = new LinkedList<CleanupResource>();
        apiTestAuthnProviderUtils = new ApiTest_AuthnProviderUtils();
    }

    @After
    public void tearDown() {
        CleanupResource.cleanUpTestResources(_cleanupResourceList);
        tearDownHttpsResources();
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

    private String getTestRootTenantBaseUrl() {
        return String.format(TEST_ROOT_TENANT_BASEURL, rootTenantId.toString());
    }

    public String getTestApi() {
        return String.format(TEST_API, getTestRootTenantBaseUrl());
    }

    public String getTestEditApi(URI uri) {
        return String.format(TEST_EDIT_API, uri.toString());
    }

    public String getTestRoleAssignmentsApi(URI uri) {
        return String.format(TEST_ROLE_ASSIGNMENTS_API, uri.toString());
    }

    public String getTestDeleteApi(URI uri) {
        return String.format(TEST_DELETE_API, uri.toString());
    }

    private String getAuthnProviderDeleteApi(URI uri) {
        return apiTestAuthnProviderUtils.getAuthnProviderEditURL(uri);
    }

    public String getProjectCreateApi(URI uri) {
        return String.format(TEST_CREATE_PROJECT_API, uri.toString());
    }

    public String getProjectGetApi(URI uri) {
        return String.format(TEST_GET_PROJECT_API, uri.toString());
    }

    public String getProjectDeleteApi(URI uri) {
        return String.format(TEST_DELETE_PROJECT_API, uri.toString());
    }

    private String getGetTenantApi() {
        return TEST_GET_TENANT_API;
    }

    private UserMappingAttributeParam getDefaultUserMappingAttributeParam(int index) {
        UserMappingAttributeParam param = new UserMappingAttributeParam();
        String attr = "Attr" + index;

        List<String> values = new ArrayList<String>();
        values.add("Attr" + index + "_Value1");
        values.add("Attr" + index + "_Value2");
        values.add("Attr" + index + "_Value1");

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

        TenantOrgRestRep getTenantResp = rSys.path(getTestEditApi(tenantId)).get(TenantOrgRestRep.class);
        Assert.assertNotNull(getTenantResp.getName());
        tenantUpdate.setLabel(getTenantResp.getName());

        ClientResponse resp = rSys.path(getTestEditApi(tenantId)).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
    }

    private void addUserMappingAndExpectFailure(URI tenantId, String group, BalancedWebResource user) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(getTestDomainName());
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getAdd().add(rootMapping);

        TenantOrgRestRep getTenantResp = rSys.path(getTestEditApi(tenantId)).get(TenantOrgRestRep.class);
        Assert.assertNotNull(getTenantResp.getName());
        tenantUpdate.setLabel(getTenantResp.getName());

        ClientResponse resp = user.path(getTestEditApi(tenantId)).put(ClientResponse.class, tenantUpdate);

        // Since the tenant creation is done by the provider tenant admin,
        // the operation will fail.
        String partialExpectedErrorMsg = "Only users with SECURITY_ADMIN role can modify user-mapping properties";
        validateTenantCreateAndEditBadRequest(403, partialExpectedErrorMsg, resp);
    }

    private void removeUserMapping(URI tenantId, String group) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(getTestDomainName());
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getRemove().add(rootMapping);

        TenantOrgRestRep getTenantResp = rSys.path(getTestEditApi(tenantId)).get(TenantOrgRestRep.class);
        Assert.assertNotNull(getTenantResp.getName());
        tenantUpdate.setLabel(getTenantResp.getName());

        ClientResponse resp = rSys.path(getTestEditApi(tenantId)).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
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

    private String getUserWhoAmIApi() {
        return TEST_USER_WHOAMI_API;
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
        Assert.assertEquals(200, clientResp.getStatus());

        AuthnProviderRestRep resp = clientResp.getEntity(AuthnProviderRestRep.class);

        // Add the created authnprovider to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getAuthnProviderDeleteApi(resp.getId());
        CleanupResource authnProviderToCleanup = new CleanupResource("delete", deleteObjectURL, rSys, null);

        registerResourceForCleanup(authnProviderToCleanup);
    }

    private void validateTenantCreateSuccess(TenantCreateParam expected, TenantOrgRestRep actual) {
        Assert.assertTrue(actual.getName().equalsIgnoreCase(expected.getLabel()));

        for (UserMappingParam expectedUserMappingParam : expected.getUserMappings()) {
            boolean userMappingsFound = false;
            for (UserMappingParam actualUserMappingParam : actual.getUserMappings()) {
                if (expectedUserMappingParam.getDomain().equalsIgnoreCase(actualUserMappingParam.getDomain()) &&
                        expectedUserMappingParam.getGroups().containsAll(actualUserMappingParam.getGroups())) {
                    userMappingsFound = true;
                }
            }
            Assert.assertTrue(userMappingsFound);
        }

        // Add the created tenant to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getTestDeleteApi(actual.getId());
        CleanupResource tenantToCleanup = new CleanupResource("post", deleteObjectURL, rSys, null);

        registerResourceForCleanup(tenantToCleanup);
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

    private void validateProjectCreateSuccess(String expectedProjectName, ProjectElement actual) {
        Assert.assertNotNull(actual);
        Assert.assertTrue(actual.getName().equalsIgnoreCase(expectedProjectName));

        // Add the created project to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        String projectDeleteApi = getProjectDeleteApi(actual.getId());
        CleanupResource projectToCleanup = new CleanupResource("post", projectDeleteApi, rSys, null);

        registerResourceForCleanup(projectToCleanup);
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
    public void testTenantCreateWithGroupInLDAPAuthnProviderWithoutLDAPGroupProperties() {
        final String testName = "testTenantCreateWithGroupInLDAPAuthnProviderWithoutLDAPGroupProperties - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription()
                + " with empty group ObjectClasses.");

        // Remove the ldap group properties from the authnprovider createparam before creating the authnprovider.
        // This should make the tenant creation with groups fail.
        authnProviderCreateParam.getGroupObjectClasses().clear();
        authnProviderCreateParam.getGroupMemberAttributes().clear();

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Creation of tenant with groups should fail");
        ClientResponse clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam contains invalid groupAttribute,
        // the post request should fail with the below errors.
        String partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientCreateResp);
    }

    @Test
    public void testTenantCreateWithGroupInLDAPAuthnProviderWithoutGroupProperties() {
        final String testName = "testTenantCreateWithGroupInLDAPAuthnProviderWithoutGroupProperties - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription()
                + " with empty group objectClasses and memberAttributes.");

        // Remove the ldap group properties from the authnprovider createparam before creating the authnprovider.
        // This should make the tenant creation with groups fail.
        // Now remove both group objectClasses and memberAttributes.
        authnProviderCreateParam.getGroupMemberAttributes().clear();
        authnProviderCreateParam.getGroupObjectClasses().clear();

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Creation of tenant with groups should fail");
        ClientResponse clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam contains invalid groupAttribute,
        // the post request should fail with the below errors.
        String partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientCreateResp);
    }

    @Test
    public void testTenantCreateWithInvalidGroupObjectClassInLDAPAuthnProvider() {
        final String testName = "testTenantCreateWithInvalidGroupObjectClassInLDAPAuthnProvider - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription()
                + " with only one objectClasses and memberAttributes.");

        // Remove the ldap group properties from the authnprovider createparam before creating the authnprovider.
        // And add only one group objectClassName and member attributeTypeName.
        // Now remove both group objectClasses and memberAttributes.
        authnProviderCreateParam.getGroupMemberAttributes().clear();
        authnProviderCreateParam.getGroupObjectClasses().clear();

        // Just add only one group objectClassName and member attributeTypeName.
        // Createed authnprovider with just "groupOfNames" and "member"
        authnProviderCreateParam.getGroupObjectClasses().add(getGroupObjectClass(0));
        authnProviderCreateParam.getGroupMemberAttributes().add(getGroupMemberAttribute(0));

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Creation of tenant with groups should fail");

        // Remove all the default groups from the createParam and add only one group of objectClass that
        // is not present in the authnprovider just created above.
        createParam.getUserMappings().get(0).getGroups().clear();

        // Just added group "ldapViPRUserGroupOrgRole" of objectClass "organizationalRole"
        // that is not supported by the autnprovider.
        // This should make the tenant creation fail.
        createParam.getUserMappings().get(0).getGroups().add(getGroup(2));

        ClientResponse clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam contains invalid groupAttribute,
        // the post request should fail with the below errors.
        String partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientCreateResp);
    }

    @Test
    public void testTenantCreateWithOnlyGroupInLDAPAuthnProvider() {
        final String testName = "testTenantCreateWithOnlyGroupInLDAPAuthnProvider - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());
        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant with group only");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);
    }

    @Test
    public void testTenantCreateWithOnlyAttributesInLDAPAuthnProvider() {
        final String testName = "testTenantCreateWithOnlyAttributesInLDAPAuthnProvider - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());
        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant with attributes only");
        createParam.getUserMappings().get(0).getGroups().clear();
        createParam.getUserMappings().get(0).getAttributes().addAll(getDefaultUserMappingAttributeParamList(2));

        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);
    }

    @Test
    public void testTenantCreateWithGroupAndAttributesInLDAPAuthnProvider() {
        final String testName = "testTenantCreateWithGroupAndAttributesInLDAPAuthnProvider - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());
        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName
                + "Successful creation of tenant with group and attributes");
        createParam.getUserMappings().get(0).getAttributes().addAll(getDefaultUserMappingAttributeParamList(2));

        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);
    }

    @Test
    public void testTenantEditWithGroupInLDAPAuthnProviderWithoutGroupProperties() {
        final String testName = "testTenantEditWithGroupInLDAPAuthnProviderWithoutGroupProperties - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription()
                + "Without group objectClasses and memberAttribute");

        // Remove the group objectClasses and memberAttributes from authnprovider create param.
        authnProviderCreateParam.getGroupObjectClasses().clear();
        authnProviderCreateParam.getGroupMemberAttributes().clear();

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName
                + "Successful creation of tenant with group and attributes");

        // Remove all the default groups, as authnprovider was created without any group properties.
        createParam.getUserMappings().get(0).getGroups().clear();

        // Add some attributes in the user mapping in order make the tenant creation success.
        createParam.getUserMappings().get(0).getAttributes().addAll(getDefaultUserMappingAttributeParamList(2));

        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        // Now edit the created tenant and add some valid groups in the user mapping.
        // This should make the tenant edit fail as authn provider does not have any
        // group properties.

        String testEditApi = this.getTestEditApi(createResp.getId());

        TenantUpdateParam editParam = getTenantUpdateParamFromTenantCreateRestResp(createResp);
        editParam.setDescription(testName
                + "Editing the tenant with some valid groups. This will fail as authnprovider has empty group properties");
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(0));
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(1));

        ClientResponse clientEditResp = rSys.path(testEditApi).put(ClientResponse.class, editParam);

        String partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientEditResp);

        // Remove the valid group and just some invalid group (a group that is not a configured one in the
        // authnprovider).
        editParam.setDescription(testName
                + "Editing the tenant with some invalid groups. This will fail as authnprovider has empty group properties");
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().clear();
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add("someInvalidGroup1");
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add("someInvalidGroup2");

        clientEditResp = rSys.path(testEditApi).put(ClientResponse.class, editParam);

        partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientEditResp);
    }

    @Test
    public void testTenantEditWithGroupInLDAPAuthnProviderWithGroupProperties() {
        final String testName = "testTenantEditWithGroupInLDAPAuthnProviderWithGroupProperties - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant with group only");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        // Now edit the created tenant and remove the added groups and add some attributes.

        String testEditApi = this.getTestEditApi(createResp.getId());

        TenantUpdateParam editParam = getTenantUpdateParamFromTenantCreateRestResp(createResp);
        editParam.setDescription(testName + "Editing the tenant by removing the valid groups and adding some attributes");

        // Remove the groups by removing the added userMapping.
        UserMappingParam userMappingParam = new UserMappingParam(createResp.getUserMappings().get(0).getDomain(), createResp
                .getUserMappings().get(0).getAttributes(), createResp.getUserMappings().get(0).getGroups());
        editParam.getUserMappingChanges().getRemove().add(userMappingParam);

        editParam.getUserMappingChanges().getAdd().get(0).getGroups().clear();
        editParam.getUserMappingChanges().getAdd().get(0).getAttributes().addAll(getDefaultUserMappingAttributeParamList(2));

        TenantOrgRestRep editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        validateTenantEditSuccess(editParam, editResp);

        // Remove the groups by removing the added userMapping.
        userMappingParam = new UserMappingParam(createResp.getUserMappings().get(0).getDomain(), createResp.getUserMappings().get(0)
                .getAttributes(), createResp.getUserMappings().get(0).getGroups());
        editParam.getUserMappingChanges().getRemove().add(userMappingParam);

        // Add some valid groups.
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(0));
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(1));
        editParam.setDescription(testName + "Editing the tenant by Adding the valid groups back");

        editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        validateTenantEditSuccess(editParam, editResp);

        // Add some invalid groups.
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add("someInvalidGroup1");
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add("someInvalidGroup2");
        editParam.setDescription(testName + "Editing the tenant by Adding the invalid valid groups");

        ClientResponse clientEditResp = rSys.path(testEditApi).put(ClientResponse.class, editParam);

        String partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientEditResp);
    }

    @Test
    public void testTenantEditWithInvalidGroupInLDAPAuthnProviderWithGroupProperties() {
        final String testName = "testTenantEditWithInvalidGroupInLDAPAuthnProviderWithGroupProperties - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription()
                + "With only one group objectClasses and memberAttributes");

        // Remove the ldap group properties from the authnprovider createparam before creating the authnprovider.
        // And add only one group objectClassName and member attributeTypeName.
        // Now remove both group objectClasses and memberAttributes.
        authnProviderCreateParam.getGroupMemberAttributes().clear();
        authnProviderCreateParam.getGroupObjectClasses().clear();

        // Just add only one group objectClassName and member attributeTypeName.
        // Createed authnprovider with just "groupOfNames" and "member"
        authnProviderCreateParam.getGroupObjectClasses().add(getGroupObjectClass(0));
        authnProviderCreateParam.getGroupMemberAttributes().add(getGroupMemberAttribute(0));

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant");

        // Clear the default groups from createParam and add only the group its objectClass matches with the
        // authnprovider.
        createParam.getUserMappings().get(0).getGroups().clear();
        createParam.getUserMappings().get(0).getGroups().add(getGroup(0));

        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        // Now edit the created tenant and add a new group thats objectClass is not present
        // in the authnprovider. So, the update request should fail.
        String testEditApi = this.getTestEditApi(createResp.getId());

        TenantUpdateParam editParam = getTenantUpdateParamFromTenantCreateRestResp(createResp);
        editParam.setDescription(testName
                + "Editing the tenant by adding a new groups thats objectclass does not match the authn provider supported");
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(3));

        ClientResponse clientEditResp = rSys.path(testEditApi).put(ClientResponse.class, editParam);

        String partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientEditResp);
    }

    @Test
    public void testTenantCreateWithMultipleUserMappingsInLDAPAuthnProviderWithGroupProperties() {
        final String testName = "testTenantCreateWithMultipleUserMappingsInLDAPAuthnProviderWithGroupProperties - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription()
                + "With only two group objectClasses and memberAttributes");

        // Remove the ldap group properties from the authnprovider createparam before creating the authnprovider.
        // And add only one group objectClassName and member attributeTypeName.
        // Now remove both group objectClasses and memberAttributes.
        authnProviderCreateParam.getGroupMemberAttributes().clear();
        authnProviderCreateParam.getGroupObjectClasses().clear();

        // Just add only one group objectClassName and member attributeTypeName.
        // Created authnprovider with just "groupOfNames, groupOfUniqueNames" and "member, uniqueMember"
        authnProviderCreateParam.getGroupObjectClasses().add(getGroupObjectClass(0));
        authnProviderCreateParam.getGroupObjectClasses().add(getGroupObjectClass(1));
        authnProviderCreateParam.getGroupMemberAttributes().add(getGroupMemberAttribute(0));
        authnProviderCreateParam.getGroupMemberAttributes().add(getGroupMemberAttribute(1));

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "With invalid groups in both userMappingParam");

        // Clear the default groups from createParam and add some invalid group.
        createParam.getUserMappings().get(0).getGroups().clear();
        createParam.getUserMappings().get(0).getGroups().add("someInvalidGroup1");

        // Create second userMappingParam with invalid group.
        UserMappingParam userMappingParam = getDefaultUserMappingParam();
        userMappingParam.getGroups().clear();
        userMappingParam.getGroups().add("someInvalidGroup2");
        createParam.getUserMappings().add(userMappingParam);

        ClientResponse clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientCreateResp);

        // Now make both userMappingParams with valid group but thats objectClassName is not supported
        // but the create authnprovider.
        createParam.getUserMappings().get(0).getGroups().clear();
        createParam.getUserMappings().get(0).getGroups().add(getGroup(2));
        createParam.getUserMappings().get(1).getGroups().clear();
        createParam.getUserMappings().get(1).getGroups().add(getGroup(4));
        createParam.setDescription(testName + "With valid groups in both userMappingParam but the objectClassName of the group is"
                + "not supported by the authnprovider");

        clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientCreateResp);

        // Now make one userMappingParam with valid group and second with
        // invalid group only.
        createParam.getUserMappings().get(0).getGroups().clear();
        createParam.getUserMappings().get(0).getGroups().add(getGroup(0));
        createParam.setDescription(testName + "With valid groups in both userMappingParam but the objectClassName of the one group is"
                + "not supported by the authnprovider");

        clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        partialExpectedErrorMsg = "Invalid value";
        validateTenantCreateAndEditBadRequest(400, partialExpectedErrorMsg, clientCreateResp);

        // Now make both userMappingParam with valid group
        createParam.getUserMappings().get(1).getGroups().clear();
        createParam.getUserMappings().get(1).getGroups().add(getGroup(1));
        createParam.setDescription(testName + "With valid groups in both userMappingParam");

        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);
        validateTenantCreateSuccess(createParam, createResp);
    }

    @Test
    public void testTenantEditToCreateRoleAssignment() throws NoSuchAlgorithmException {
        final String testName = "testTenantEditToCreateRoleAssignment - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());
        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant for role assignment");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        // Creating a roleAssignmentEntry with group (group without domain).
        // This should fail as the group does not have the domain suffix.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(true, false);

        String roleAssignmentsApi = getTestRoleAssignmentsApi(createResp.getId());

        ClientResponse roleAssignmentClientCreateResp = rSys.path(roleAssignmentsApi).put(ClientResponse.class, roleAssignmentEntryParam);

        String partialExpectedErrorMsg = "Invalid role assignments: Search for the following failed for this system, or could not be found for this system:";
        validateRoleAssignmentBadRequest(400, partialExpectedErrorMsg, roleAssignmentClientCreateResp);

        // Modify the roleAssignmentChanges to some invalid group.
        roleAssignmentEntryParam.getAdd().get(0).setGroup("SomeInvalidGroup1@" + getTestDomainName());

        roleAssignmentClientCreateResp = rSys.path(roleAssignmentsApi).put(ClientResponse.class, roleAssignmentEntryParam);

        partialExpectedErrorMsg = "Invalid role assignments: Search for the following failed for this system, or could not be found for this system:";
        validateRoleAssignmentBadRequest(400, partialExpectedErrorMsg, roleAssignmentClientCreateResp);

        // Modify the roleAssignmentChanges valid group.
        roleAssignmentEntryParam.getAdd().get(0).setGroup(getGroupWithDomain(2));

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);

        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Creating a roleAssignmentEntry with user (user with domain).
        roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);

        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a roleAssignmentEntry with user whose group is not part of the tenant.
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(2));

        roleAssignmentClientCreateResp = rSys.path(roleAssignmentsApi).put(ClientResponse.class, roleAssignmentEntryParam);

        partialExpectedErrorMsg = "Invalid role assignments: Search for the following failed for this system, or could not be found for this system:";
        validateRoleAssignmentBadRequest(400, partialExpectedErrorMsg, roleAssignmentClientCreateResp);

        // Create a user who is part of the tenant's group.
        BalancedWebResource ldapUser = getHttpsClient(getUserWithDomain(1), getUserPassword());

        // Get the tenant of the ldap user. This should return the above created tenant.
        TenantResponse tenantGetResp = ldapUser.path(getGetTenantApi()).get(TenantResponse.class);

        validateGetTenantSuccess(createResp, tenantGetResp);

        // Create a user who is not part of the tenant's group.
        ldapUser = getHttpsClient(getUserWithDomain(2), getUserPassword());

        // Get the tenant of the ldap user. This should not return any tenant.
        ClientResponse clientTenantGetResp = ldapUser.path(getGetTenantApi()).get(ClientResponse.class);
        Assert.assertEquals(403, clientTenantGetResp.getStatus());
    }

    @Test
    public void testTenantEditToCreateProject() throws NoSuchAlgorithmException {
        final String testName = "testTenantEditToCreateProject - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());
        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant for project creation");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        // Creating a roleAssignmentEntry with group.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(true, true);

        String roleAssignmentsApi = getTestRoleAssignmentsApi(createResp.getId());
        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);

        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        String projectCreateApi = getProjectCreateApi(createResp.getId());

        ProjectParam projectCreateParam = getDefaultProjectParam("TestProject");

        ProjectElement projectCreateResp = rSys.path(projectCreateApi).post(ProjectElement.class, projectCreateParam);

        validateProjectCreateSuccess(projectCreateParam.getName(), projectCreateResp);

        // Create a user who is part of the tenant's group.
        BalancedWebResource ldapUser = getHttpsClient(getUserWithDomain(1), getUserPassword());

        // Get the tenant of the ldap user. This should return the above created tenant.
        TenantResponse tenantGetResp = ldapUser.path(getGetTenantApi()).get(TenantResponse.class);

        validateGetTenantSuccess(createResp, tenantGetResp);

        String getProjectApi = this.getProjectGetApi(createResp.getId());

        // Get the project of the ldap user. This should return the above created project.
        com.emc.storageos.model.project.ProjectList getProjectResp = ldapUser.path(getProjectApi).get(
                com.emc.storageos.model.project.ProjectList.class);

        validateGetProjectSuccess(projectCreateResp.getName().toString(), getProjectResp);

        // Create a user who is not part of the tenant's group.
        ldapUser = getHttpsClient(getUserWithDomain(2), getUserPassword());

        // Get the tenant of the ldap user. This should not return any tenant.
        ClientResponse clientTenantGetResp = ldapUser.path(getGetTenantApi()).get(ClientResponse.class);
        Assert.assertEquals(403, clientTenantGetResp.getStatus());

        ClientResponse clientProjectIdsResp = ldapUser.path(getProjectApi).get(ClientResponse.class);

        Assert.assertEquals(403, clientProjectIdsResp.getStatus());
    }

    @Test
    public void testTenantEditWithDomainComponentInGroup() {
        final String testName = "testTenantEditWithDomainComponentInGroup - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant with group only");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        // Now edit the created tenant and remove the added groups and add some attributes.

        String testEditApi = this.getTestEditApi(createResp.getId());

        TenantUpdateParam editParam = getTenantUpdateParamFromTenantCreateRestResp(createResp);
        editParam.setDescription(testName + "Editing the tenant by removing the valid groups and adding some attributes");

        // Remove the groups by removing the added userMapping.
        UserMappingParam userMappingParam = new UserMappingParam(createResp.getUserMappings().get(0).getDomain(), createResp
                .getUserMappings().get(0).getAttributes(), createResp.getUserMappings().get(0).getGroups());
        editParam.getUserMappingChanges().getRemove().add(userMappingParam);

        editParam.getUserMappingChanges().getAdd().get(0).getGroups().clear();
        editParam.getUserMappingChanges().getAdd().get(0).getAttributes().addAll(getDefaultUserMappingAttributeParamList(2));

        TenantOrgRestRep editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        validateTenantEditSuccess(editParam, editResp);

        // Remove the groups by removing the added userMapping.
        userMappingParam = new UserMappingParam(createResp.getUserMappings().get(0).getDomain(), createResp.getUserMappings().get(0)
                .getAttributes(), createResp.getUserMappings().get(0).getGroups());
        editParam.getUserMappingChanges().getRemove().add(userMappingParam);

        // Add some valid groups.
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(0));
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(1));
        editParam.setDescription(testName + "Editing the tenant by Adding the valid groups back");

        editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        validateTenantEditSuccess(editParam, editResp);

        // Make sure all the added user mappings available in the tenant.
        Assert.assertEquals(1, editResp.getUserMappings().size());

        // Remove all the existing groups.
        editParam.getUserMappingChanges().getRemove().get(0).getGroups().add(getGroup(0));
        editParam.getUserMappingChanges().getRemove().get(0).getGroups().add(getGroup(1));

        // Add the same old groups with domain component.
        editParam.getUserMappingChanges().getAdd().get(0).getGroups()
                .set(0, getGroup(0) + "@" + editParam.getUserMappingChanges().getAdd().get(0).getDomain());
        editParam.getUserMappingChanges().getAdd().get(0).getGroups()
                .set(1, getGroup(1) + "@" + editParam.getUserMappingChanges().getAdd().get(0).getDomain());
        editParam.setDescription(testName + "Editing the tenant by Adding the domain component in the groups");

        editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        // Make sure all the added user mappings available in the tenant.
        Assert.assertEquals(1, editResp.getUserMappings().size());
        Assert.assertEquals(2, editResp.getUserMappings().get(0).getGroups().size());
    }

    @Test
    public void testTenantEditByAddingGroupsWithDomainComponent() {
        final String testName = "testTenantEditByAddingGroupsWithDomainComponent - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant with group only");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        // Now edit the created tenant and remove the added groups and add some attributes.

        String testEditApi = this.getTestEditApi(createResp.getId());

        TenantUpdateParam editParam = getTenantUpdateParamFromTenantCreateRestResp(createResp);
        editParam.setDescription(testName + "Editing the tenant by removing the valid groups and adding some attributes");

        // Remove the groups by removing the added userMapping.
        UserMappingParam userMappingParam = new UserMappingParam(createResp.getUserMappings().get(0).getDomain(), createResp
                .getUserMappings().get(0).getAttributes(), createResp.getUserMappings().get(0).getGroups());
        editParam.getUserMappingChanges().getRemove().add(userMappingParam);

        editParam.getUserMappingChanges().getAdd().get(0).getGroups().clear();
        editParam.getUserMappingChanges().getAdd().get(0).getAttributes().addAll(getDefaultUserMappingAttributeParamList(2));

        TenantOrgRestRep editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        validateTenantEditSuccess(editParam, editResp);

        // Remove the groups by removing the added userMapping.
        userMappingParam = new UserMappingParam(createResp.getUserMappings().get(0).getDomain(), createResp.getUserMappings().get(0)
                .getAttributes(), createResp.getUserMappings().get(0).getGroups());
        editParam.getUserMappingChanges().getRemove().add(userMappingParam);

        // Add some valid groups.
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(0));
        editParam.getUserMappingChanges().getAdd().get(0).getGroups().add(getGroup(1));
        editParam.setDescription(testName + "Editing the tenant by Adding the valid groups back");

        editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        validateTenantEditSuccess(editParam, editResp);

        // Make sure all the added user mappings available in the tenant.
        Assert.assertEquals(1, editResp.getUserMappings().size());

        // Add some groups with domain component.
        editParam.getUserMappingChanges().getAdd().get(0).getGroups()
                .set(0, getGroup(0) + "@" + editParam.getUserMappingChanges().getAdd().get(0).getDomain());
        editParam.getUserMappingChanges().getAdd().get(0).getGroups()
                .set(1, getGroup(1) + "@" + editParam.getUserMappingChanges().getAdd().get(0).getDomain());
        editParam.setDescription(testName + "Editing the tenant by Adding the domain component in the groups");

        editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        // Make sure all the added user mappings available in the tenant.
        Assert.assertEquals(1, editResp.getUserMappings().size());
        Assert.assertEquals(2, editResp.getUserMappings().get(0).getGroups().size());

        // Add some groups with domain component.
        editParam.getUserMappingChanges().getAdd().get(0).getGroups()
                .add(getGroup(0) + "@" + editParam.getUserMappingChanges().getAdd().get(0).getDomain());
        editParam.getUserMappingChanges().getAdd().get(0).getGroups()
                .add(getGroup(1) + "@" + editParam.getUserMappingChanges().getAdd().get(0).getDomain());
        editParam.setDescription(testName + "Editing the tenant by Adding the domain component in the groups");

        editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        // Make sure all the added user mappings available in the tenant.
        Assert.assertEquals(1, editResp.getUserMappings().size());
        Assert.assertEquals(2, editResp.getUserMappings().get(0).getGroups().size());

        // Add some groups with domain component.
        editParam.getUserMappingChanges().getAdd().get(0).getGroups()
                .add(getGroup(2) + "@" + editParam.getUserMappingChanges().getAdd().get(0).getDomain());
        editParam.getUserMappingChanges().getAdd().get(0).getGroups()
                .add(getGroup(3) + "@" + editParam.getUserMappingChanges().getAdd().get(0).getDomain());
        editParam.setDescription(testName + "Editing the tenant by Adding the domain component in the groups");

        editResp = rSys.path(testEditApi).put(TenantOrgRestRep.class, editParam);

        // Make sure all the added user mappings available in the tenant.
        Assert.assertEquals(2, editResp.getUserMappings().size());
        Assert.assertEquals(2, editResp.getUserMappings().get(0).getGroups().size());
        Assert.assertEquals(4, editResp.getUserMappings().get(1).getGroups().size());
    }

    @Test
    public void testTenantCreateByProviderTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testTenantCreateByProviderTenantAdmin - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        String groupToAddInUserMapping = getGroup(0);
        addUserMapping(rootTenantId, groupToAddInUserMapping);

        // Assign tenant admin role to the user ldapvipruser1@maxcrc.com
        // who is part of provider tenant.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(0));
        roleAssignmentEntryParam.getAdd().get(0).getRoles().clear();
        roleAssignmentEntryParam.getAdd().get(0).getRoles().add(getTenantRole(0));

        String roleAssignmentsApi = getTestRoleAssignmentsApi(rootTenantId);

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a ldapvipruser1@maxcrc.com who has provider tenant admin role.
        String ldapViPRUser1Name = getUserWithDomain(0);
        BalancedWebResource ldapViPRUser1 = getHttpsClient(ldapViPRUser1Name, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser1UserInfo = ldapViPRUser1.path(whoAmIApi).get(UserInfo.class);
        List<String> expectedRoles = new ArrayList<String>();
        expectedRoles.add(getTenantRole(0));
        validateUserTenantRoles(ldapViPRUser1UserInfo, expectedRoles);

        // Create a subtenant by provider tenant admin.
        TenantCreateParam createParam = this
                .getDefaultTenantCreateParam(testName + "Tenant creation by Provider tenant admin should fail.");
        ClientResponse clientCreateResp = ldapViPRUser1.path(getTestApi()).post(ClientResponse.class, createParam);

        // Only sec admin can create sub tenants, the operation will fail.
        String partialExpectedErrorMsg = "Insufficient permissions for user %s";
        partialExpectedErrorMsg = String.format(partialExpectedErrorMsg, ldapViPRUser1Name.toLowerCase());
        validateTenantCreateAndEditBadRequest(403, partialExpectedErrorMsg, clientCreateResp);

        // Logout the user.
        logoutUser(ldapViPRUser1);

        // Remove the role assignment for the user.
        roleAssignmentEntryParam.getRemove().add(roleAssignmentEntryParam.getAdd().get(0));
        roleAssignmentEntryParam.getAdd().clear();

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateVDCRoleAssignmentsRemove(roleAssignmentCreateResp, ldapViPRUser1Name, false);

        // Remove the user mappings.
        removeUserMapping(rootTenantId, groupToAddInUserMapping);
    }

    @Test
    public void testTenantCreateBySubTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testTenantCreateBySubTenantAdmin - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        // Create a subtenant by sec admin.
        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant by sec admin.");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        URI tenantId = createResp.getId();
        String groupToAddInUserMapping = getGroup(0);
        addUserMapping(tenantId, groupToAddInUserMapping);

        // Assign tenant admin role to the user ldapvipruser1@maxcrc.com
        // who is part of subtenant.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(0));
        roleAssignmentEntryParam.getAdd().get(0).getRoles().clear();
        roleAssignmentEntryParam.getAdd().get(0).getRoles().add(getTenantRole(0));

        String roleAssignmentsApi = getTestRoleAssignmentsApi(tenantId);

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a ldapvipruser1@maxcrc.com who has subtenant admin role.
        String ldapViPRUser1Name = getUserWithDomain(0);
        BalancedWebResource ldapViPRUser1 = getHttpsClient(ldapViPRUser1Name, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser1UserInfo = ldapViPRUser1.path(whoAmIApi).get(UserInfo.class);
        List<String> expectedRoles = new ArrayList<String>();
        expectedRoles.add(getTenantRole(0));
        validateUserTenantRoles(ldapViPRUser1UserInfo, expectedRoles);

        // Change the name the subtenant to something different.
        createParam = this.getDefaultTenantCreateParam(testName + "Tenant creation by subtenant admin should fail.");
        createParam.setLabel("Tenant Created by subtenant admin");

        // Create a subtenant by subtenant admin.
        ClientResponse clientCreateResp = ldapViPRUser1.path(getTestApi()).post(ClientResponse.class, createParam);

        // Only sec admin can create sub tenants, the operation will fail.
        String partialExpectedErrorMsg = "Insufficient permissions for user %s";
        partialExpectedErrorMsg = String.format(partialExpectedErrorMsg, ldapViPRUser1Name.toLowerCase());
        validateTenantCreateAndEditBadRequest(403, partialExpectedErrorMsg, clientCreateResp);

        // Logout the user.
        logoutUser(ldapViPRUser1);

        // Remove the role assignment for the user.
        roleAssignmentEntryParam.getRemove().add(roleAssignmentEntryParam.getAdd().get(0));
        roleAssignmentEntryParam.getAdd().clear();

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateVDCRoleAssignmentsRemove(roleAssignmentCreateResp, ldapViPRUser1Name, false);

        // Remove the user mappings.
        removeUserMapping(tenantId, groupToAddInUserMapping);
    }

    @Test
    public void testProviderTenantEditByProviderTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testProviderTenantEditByProviderTenantAdmin - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        String groupToAddInUserMapping = getGroup(0);
        addUserMapping(rootTenantId, groupToAddInUserMapping);

        // Assign tenant admin role to the user ldapvipruser1@maxcrc.com
        // who is part of provider tenant.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(0));
        roleAssignmentEntryParam.getAdd().get(0).getRoles().clear();
        roleAssignmentEntryParam.getAdd().get(0).getRoles().add(getTenantRole(0));

        String roleAssignmentsApi = getTestRoleAssignmentsApi(rootTenantId);

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a ldapvipruser1@maxcrc.com who has provider tenant admin role.
        String ldapViPRUser1Name = getUserWithDomain(0);
        BalancedWebResource ldapViPRUser1 = getHttpsClient(ldapViPRUser1Name, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser1UserInfo = ldapViPRUser1.path(whoAmIApi).get(UserInfo.class);
        List<String> expectedRoles = new ArrayList<String>();
        expectedRoles.add(getTenantRole(0));
        validateUserTenantRoles(ldapViPRUser1UserInfo, expectedRoles);

        String rootTenantEditApi = getTestEditApi(rootTenantId);

        TenantUpdateParam editParam = new TenantUpdateParam();
        editParam.setDescription(testName + "Provider tenant admin editing the provider tenant by changing the description.");

        // Provider tenant edits the provider tenant by changing its description.
        ClientResponse clientEditResp = ldapViPRUser1.path(rootTenantEditApi).put(ClientResponse.class, editParam);
        Assert.assertEquals(200, clientEditResp.getStatus());

        // Add the user mapping to the provider tenant.
        // Only sec admin can create sub tenants, the operation will fail.
        addUserMappingAndExpectFailure(rootTenantId, getGroup(0), ldapViPRUser1);

        // Logout the user.
        logoutUser(ldapViPRUser1);

        // Remove the role assignment for the user.
        roleAssignmentEntryParam.getRemove().add(roleAssignmentEntryParam.getAdd().get(0));
        roleAssignmentEntryParam.getAdd().clear();

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateVDCRoleAssignmentsRemove(roleAssignmentCreateResp, ldapViPRUser1Name, false);

        // Remove the user mappings.
        removeUserMapping(rootTenantId, groupToAddInUserMapping);
    }

    @Test
    public void testSubTenantEditByProviderTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testSubTenantEditByProviderTenantAdmin - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        String groupToAddInUserMapping = getGroup(0);
        addUserMapping(rootTenantId, groupToAddInUserMapping);

        // Assign tenant admin role to the user ldapvipruser1@maxcrc.com
        // who is part of provider tenant.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(0));
        roleAssignmentEntryParam.getAdd().get(0).getRoles().clear();
        roleAssignmentEntryParam.getAdd().get(0).getRoles().add(getTenantRole(0));

        String roleAssignmentsApi = getTestRoleAssignmentsApi(rootTenantId);

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a ldapvipruser1@maxcrc.com who has provider tenant admin role.
        String ldapViPRUser1Name = getUserWithDomain(0);
        BalancedWebResource ldapViPRUser1 = getHttpsClient(ldapViPRUser1Name, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser1UserInfo = ldapViPRUser1.path(whoAmIApi).get(UserInfo.class);
        List<String> expectedRoles = new ArrayList<String>();
        expectedRoles.add(getTenantRole(0));
        validateUserTenantRoles(ldapViPRUser1UserInfo, expectedRoles);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant by sec admin.");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        URI subTenantId = createResp.getId();
        String subTenantEditApi = getTestEditApi(subTenantId);

        TenantUpdateParam editParam = new TenantUpdateParam();
        editParam.setDescription(testName + "SubTenant - Set by provider tenant admin");

        // Edit the subtenant by changing its description. It should fail
        // as only the tenant admin or sec admin can edit the tenant.
        ClientResponse clientEditResp = ldapViPRUser1.path(subTenantEditApi).put(ClientResponse.class, editParam);

        String partialExpectedErrorMsg = "Insufficient permissions for user %s";
        partialExpectedErrorMsg = String.format(partialExpectedErrorMsg, ldapViPRUser1Name.toLowerCase());
        validateTenantCreateAndEditBadRequest(403, partialExpectedErrorMsg, clientEditResp);

        // Logout the user.
        logoutUser(ldapViPRUser1);

        // Remove the role assignment for the user.
        roleAssignmentEntryParam.getRemove().add(roleAssignmentEntryParam.getAdd().get(0));
        roleAssignmentEntryParam.getAdd().clear();

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateVDCRoleAssignmentsRemove(roleAssignmentCreateResp, ldapViPRUser1Name, false);

        // Remove the user mappings.
        removeUserMapping(rootTenantId, groupToAddInUserMapping);
    }

    // @Test
    public void testSubTenantEditBySubTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testSubTenantEditBySubTenantAdmin - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        // Create a subtenant by the sec admin.
        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of tenant by sec admin.");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        // Add the user mapping to the subtenant.
        URI subTenantId = createResp.getId();
        String groupToAddInUserMapping = getGroup(0);
        addUserMapping(subTenantId, groupToAddInUserMapping);

        // Assign tenant admin role to the user ldapvipruser1@maxcrc.com
        // who is part of subtenant.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(0));
        roleAssignmentEntryParam.getAdd().get(0).getRoles().clear();
        roleAssignmentEntryParam.getAdd().get(0).getRoles().add(getTenantRole(0));

        String roleAssignmentsApi = getTestRoleAssignmentsApi(subTenantId);

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a ldapvipruser1@maxcrc.com who has subtenant admin role.
        String ldapViPRUser1Name = getUserWithDomain(0);
        BalancedWebResource ldapViPRUser1 = getHttpsClient(ldapViPRUser1Name, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser1UserInfo = ldapViPRUser1.path(whoAmIApi).get(UserInfo.class);
        List<String> expectedRoles = new ArrayList<String>();
        expectedRoles.add(getTenantRole(0));
        validateUserTenantRoles(ldapViPRUser1UserInfo, expectedRoles);

        String subTenantEditApi = getTestEditApi(subTenantId);

        // Edit the provider tenant by changing its description.
        TenantUpdateParam editParam = new TenantUpdateParam();
        editParam.setDescription(testName + "SubTenant - Set by subtenant admin");

        ClientResponse clientEditResp = ldapViPRUser1.path(subTenantEditApi).put(ClientResponse.class, editParam);
        Assert.assertEquals(200, clientEditResp.getStatus());

        // Add the user mapping to it. It should fail as this is done by provider tenant admin.
        // Only sec admin can edit sub tenants, the operation will fail.
        addUserMappingAndExpectFailure(subTenantId, getGroup(0), ldapViPRUser1);

        // Logout the user.
        logoutUser(ldapViPRUser1);

        // Remove the role assignment for the user.
        roleAssignmentEntryParam.getRemove().add(roleAssignmentEntryParam.getAdd().get(0));
        roleAssignmentEntryParam.getAdd().clear();

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateVDCRoleAssignmentsRemove(roleAssignmentCreateResp, ldapViPRUser1Name, false);

        // Remove the user mappings.
        removeUserMapping(subTenantId, groupToAddInUserMapping);
    }

    @Test
    public void testProviderTenantDeleteByProviderTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testProviderTenantDeleteByProviderTenantAdmin - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        String groupToAddInUserMapping = getGroup(0);
        addUserMapping(rootTenantId, groupToAddInUserMapping);

        // Assign tenant admin role to the user ldapvipruser1@maxcrc.com
        // who is part of provider tenant.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(0));
        roleAssignmentEntryParam.getAdd().get(0).getRoles().clear();
        roleAssignmentEntryParam.getAdd().get(0).getRoles().add(getTenantRole(0));

        String roleAssignmentsApi = getTestRoleAssignmentsApi(rootTenantId);

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a ldapvipruser1@maxcrc.com who has tenant admin role.
        String ldapViPRUser1Name = getUserWithDomain(0);
        BalancedWebResource ldapViPRUser1 = getHttpsClient(ldapViPRUser1Name, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser1UserInfo = ldapViPRUser1.path(whoAmIApi).get(UserInfo.class);
        List<String> expectedRoles = new ArrayList<String>();
        expectedRoles.add(getTenantRole(0));
        validateUserTenantRoles(ldapViPRUser1UserInfo, expectedRoles);

        String rootTenantDeleteApi = getTestDeleteApi(rootTenantId);

        // Delete the provider tenant. It should fail. Deleting provider tenant can't be done.
        ClientResponse clientEditResp = ldapViPRUser1.path(rootTenantDeleteApi).post(ClientResponse.class);

        String partialExpectedErrorMsg = "Insufficient permissions for user %s";
        partialExpectedErrorMsg = String.format(partialExpectedErrorMsg, ldapViPRUser1Name.toLowerCase());
        validateTenantCreateAndEditBadRequest(403, partialExpectedErrorMsg, clientEditResp);

        // Logout the user.
        logoutUser(ldapViPRUser1);

        // Remove the role assignment for the user.
        roleAssignmentEntryParam.getRemove().add(roleAssignmentEntryParam.getAdd().get(0));
        roleAssignmentEntryParam.getAdd().clear();

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateVDCRoleAssignmentsRemove(roleAssignmentCreateResp, ldapViPRUser1Name, false);

        // Remove the user mappings.
        removeUserMapping(rootTenantId, groupToAddInUserMapping);
    }

    @Test
    public void testSubTenantDeleteByProviderTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testSubTenantDeleteByProviderTenantAdmin - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        String groupToAddInUserMapping = getGroup(0);
        addUserMapping(rootTenantId, groupToAddInUserMapping);

        // Assign tenant admin role to the user ldapvipruser1@maxcrc.com
        // who is part of provider tenant.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(0));
        roleAssignmentEntryParam.getAdd().get(0).getRoles().clear();
        roleAssignmentEntryParam.getAdd().get(0).getRoles().add(getTenantRole(0));

        String roleAssignmentsApi = getTestRoleAssignmentsApi(rootTenantId);

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a ldapvipruser1@maxcrc.com who has tenant admin role.
        String ldapViPRUser1Name = getUserWithDomain(0);
        BalancedWebResource ldapViPRUser1 = getHttpsClient(ldapViPRUser1Name, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser1UserInfo = ldapViPRUser1.path(whoAmIApi).get(UserInfo.class);
        List<String> expectedRoles = new ArrayList<String>();
        expectedRoles.add(getTenantRole(0));
        validateUserTenantRoles(ldapViPRUser1UserInfo, expectedRoles);

        // Create a subtenant by sec admin.
        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of subtenant by sec admin.");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        URI subTenantId = createResp.getId();
        String subTenantDeleteApi = getTestDeleteApi(subTenantId);

        // Delete the subtenant tenant.
        // Only sec admin can create sub tenants, the operation will fail.
        ClientResponse clientDeleteResp = ldapViPRUser1.path(subTenantDeleteApi).post(ClientResponse.class);

        String partialExpectedErrorMsg = "Insufficient permissions for user %s";
        partialExpectedErrorMsg = String.format(partialExpectedErrorMsg, ldapViPRUser1Name.toLowerCase());
        validateTenantCreateAndEditBadRequest(403, partialExpectedErrorMsg, clientDeleteResp);

        // Logout the user.
        logoutUser(ldapViPRUser1);

        // Remove the role assignment for the user.
        roleAssignmentEntryParam.getRemove().add(roleAssignmentEntryParam.getAdd().get(0));
        roleAssignmentEntryParam.getAdd().clear();

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateVDCRoleAssignmentsRemove(roleAssignmentCreateResp, ldapViPRUser1Name, false);

        // Remove the user mappings.
        removeUserMapping(rootTenantId, groupToAddInUserMapping);
    }

    @Test
    public void testSubTenantDeleteBySubTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testSubTenantDeleteBySubTenantAdmin - ";

        // Create an authnprovider before creating a tenant.
        AuthnCreateParam authnProviderCreateParam = getDefaultAuthnCreateParam(testName + getTestDefaultAuthnProviderDescription());

        ClientResponse clientAuthnProviderCreateResp = rSys.path(getTestAuthnProviderApi()).post(ClientResponse.class,
                authnProviderCreateParam);

        // Validate the authn provider creation success and add the
        // resource to the resource clean up list.
        validateAuthnProviderCreateSuccess(clientAuthnProviderCreateResp);

        TenantCreateParam createParam = this.getDefaultTenantCreateParam(testName + "Successful creation of sbutenant by sec admin.");
        TenantOrgRestRep createResp = rSys.path(getTestApi()).post(TenantOrgRestRep.class, createParam);

        validateTenantCreateSuccess(createParam, createResp);

        URI subTenantId = createResp.getId();

        String groupToAddInUserMapping = getGroup(0);
        addUserMapping(subTenantId, groupToAddInUserMapping);

        // Assign tenant admin role to the user ldapvipruser1@maxcrc.com
        // who is part of subtenant.
        RoleAssignmentChanges roleAssignmentEntryParam = getDefaultRoleAssignmentChanges(false, true);
        roleAssignmentEntryParam.getAdd().get(0).setSubjectId(getUserWithDomain(0));
        roleAssignmentEntryParam.getAdd().get(0).getRoles().clear();
        roleAssignmentEntryParam.getAdd().get(0).getRoles().add(getTenantRole(0));

        String roleAssignmentsApi = getTestRoleAssignmentsApi(subTenantId);

        RoleAssignments roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateRoleAssignmentCreateSuccess(roleAssignmentEntryParam, roleAssignmentCreateResp);

        // Create a ldapvipruser1@maxcrc.com who has subtenant admin role.
        String ldapViPRUser1Name = getUserWithDomain(0);
        BalancedWebResource ldapViPRUser1 = getHttpsClient(ldapViPRUser1Name, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser1UserInfo = ldapViPRUser1.path(whoAmIApi).get(UserInfo.class);
        List<String> expectedRoles = new ArrayList<String>();
        expectedRoles.add(getTenantRole(0));
        validateUserTenantRoles(ldapViPRUser1UserInfo, expectedRoles);

        String subTenantDeleteApi = getTestDeleteApi(subTenantId);

        // Delete the subtenant.
        // Only sec admin can create sub tenants, the operation will fail.
        ClientResponse clientDeleteResp = ldapViPRUser1.path(subTenantDeleteApi).post(ClientResponse.class);

        String partialExpectedErrorMsg = "Insufficient permissions for user %s";
        partialExpectedErrorMsg = String.format(partialExpectedErrorMsg, ldapViPRUser1Name.toLowerCase());
        validateTenantCreateAndEditBadRequest(403, partialExpectedErrorMsg, clientDeleteResp);

        // Logout the user.
        logoutUser(ldapViPRUser1);

        // Remove the role assignment for the user.
        roleAssignmentEntryParam.getRemove().add(roleAssignmentEntryParam.getAdd().get(0));
        roleAssignmentEntryParam.getAdd().clear();

        roleAssignmentCreateResp = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentEntryParam);
        validateVDCRoleAssignmentsRemove(roleAssignmentCreateResp, ldapViPRUser1Name, false);

        // Remove the user mappings.
        removeUserMapping(subTenantId, groupToAddInUserMapping);
    }
}
