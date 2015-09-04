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

import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.auth.*;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.project.ProjectUpdateParam;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.model.user.UserInfo;
import com.emc.storageos.model.usergroup.*;
import com.sun.jersey.api.client.ClientResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 *
 * ApiTest_UserGroup class to exercise the core api functionality of User Group.
 */
public class ApiTest_UserGroup extends ApiTestBase {
    private final String TEST_API = "/vdc/admin/user-groups";
    private final String TEST_EDIT_API = TEST_API + "/%s";
    private final String TEST_BULK_API = TEST_API + "/bulk";
    private final String TEST_TAGS_API = TEST_API + "/tags";
    private final String TEST_VDC_ROLE_ASSIGNMENT_API = "/vdc/role-assignments";
    private final String TEST_USER_WHOAMI_API = "/user/whoami";
    private final String TEST_GET_PROJECT_API = "/projects/%s";
    private final String TEST_PROJECT_ACL_ASSIGNMENTS_API = "/projects/%s/acl";
    private final String TEST_PROJECT_DELETE_API = "/projects/%s/deactivate";

    private final String TEST_DEFAULT_USER_GROUP_NAME = "Depart_Dev";
    private final String[] TEST_DEFAULT_VDC_ROLES = { "SYSTEM_ADMIN", "SECURITY_ADMIN", "SYSTEM_MONITOR", "SYSTEM_AUDITOR" };
    private final String[] TEST_DEFAULT_TENANT_ROLES = { "TENANT_ADMIN", "PROJECT_ADMIN", "TENANT_APPROVER" };
    private final String[] TEST_DEFAULT_ACLS = { "ALL", "BACKUP", "USE", "OWN" };

    private String authnProviderDomain = null;
    private ApiTest_AuthnProviderUtils apiTestAuthnProviderUtils = new ApiTest_AuthnProviderUtils();;
    private ApiTest_Tenants apiTestTenants = new ApiTest_Tenants();
    private LinkedList<CleanupResource> _cleanupResourceList = null;

    @Before
    public void setUp() throws Exception {
        setupHttpsResources();
        _cleanupResourceList = new LinkedList<CleanupResource>();

        apiTestAuthnProviderUtils = new ApiTest_AuthnProviderUtils();
        apiTestTenants = new ApiTest_Tenants();
        apiTestTenants.rootTenantId = rootTenantId;
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

    UserGroup removeDuplicateAttributes(UserGroup from) {
        Assert.assertNotNull(from);
        Assert.assertFalse(CollectionUtils.isEmpty(from.getAttributes()));

        UserGroupCreateParam createParam = new UserGroupCreateParam();
        createParam.setLabel(from.getLabel());
        createParam.setDomain(from.getDomain());

        for (String userAttributeParamString : from.getAttributes()) {
            UserAttributeParam userAttributeParam = UserAttributeParam.fromString(userAttributeParamString);
            Assert.assertNotNull(userAttributeParam);
            boolean foundAttribute = false;
            for (UserAttributeParam existingUserAttributeParam : createParam.getAttributes()) {
                if (existingUserAttributeParam.getKey().equalsIgnoreCase(userAttributeParam.getKey())) {
                    existingUserAttributeParam.getValues().addAll(userAttributeParam.getValues());
                    foundAttribute = true;
                }
            }
            if (!foundAttribute) {
                createParam.getAttributes().add(userAttributeParam);
            }
        }
        return buildUserGroupFromCreateParam(createParam, true);
    }

    UserGroup buildUserGroupFromCreateParam(UserGroupCreateParam createParam, boolean removingDuplicate) {
        Assert.assertNotNull(createParam);
        Assert.assertFalse(CollectionUtils.isEmpty(createParam.getAttributes()));

        UserGroup userGroup = new UserGroup();
        userGroup.setLabel(createParam.getLabel());
        userGroup.setDomain(createParam.getDomain());

        for (UserAttributeParam attributeParam : createParam.getAttributes()) {
            userGroup.getAttributes().add(attributeParam.toString());
        }

        if (!removingDuplicate) {
            return removeDuplicateAttributes(userGroup);
        } else {
            return userGroup;
        }
    }

    UserGroup buildUserGroupFromUpdateParam(UserGroupUpdateParam updateParam, UserGroup userGroup) {
        Assert.assertNotNull(updateParam);
        Assert.assertNotNull(userGroup);

        UserGroup userGroupToReturn = new UserGroup();
        userGroupToReturn.setLabel(userGroup.getLabel());
        userGroupToReturn.setDomain(updateParam.getDomain());

        if (!CollectionUtils.isEmpty(updateParam.getAddAttributes())) {
            for (UserAttributeParam attributeParam : updateParam.getAddAttributes()) {
                userGroup.getAttributes().add(attributeParam.toString());
            }
        }

        if (!CollectionUtils.isEmpty(updateParam.getRemoveAttributes())) {
            for (String removeAttributeKey : updateParam.getRemoveAttributes()) {
                Iterator<String> it = userGroup.getAttributes().iterator();
                while (it.hasNext()) {
                    String userAttributeParamString = it.next();
                    UserAttributeParam userAttributeParam = UserAttributeParam.fromString(userAttributeParamString);
                    Assert.assertNotNull(userAttributeParam);
                    if (userAttributeParam.getKey().equalsIgnoreCase(removeAttributeKey)) {
                        userGroup.getAttributes().remove(userAttributeParam.toString());
                    }
                }
            }
        }
        return removeDuplicateAttributes(userGroup);
    }

    private void deleteUserGroupAndExpectFailure(URI id) {
        String userGroupDeleteApi = getTestEditApi(id);
        ClientResponse clientResponseUserGroupDelete = rSys.path(userGroupDeleteApi).delete(ClientResponse.class);

        Assert.assertEquals(400, clientResponseUserGroupDelete.getStatus());

        final String partialExpectedErrorString = "Deleting or editing the domain of an user group is not allowed because";
        final ServiceErrorRestRep actualErrorMsg = clientResponseUserGroupDelete.getEntity(ServiceErrorRestRep.class);
        Assert.assertTrue(actualErrorMsg.getDetailedMessage().contains(partialExpectedErrorString));
    }

    private void changeUserGroupDomainAndExpectFailure(UserGroupRestRep restRep) {
        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(restRep);
        Assert.assertNotNull(restRep);

        updateParam.setDomain(getSecondDomain());

        String editApi = getTestEditApi(restRep.getId());

        ClientResponse clientResponseUserGroupEdit = rSys.path(editApi).put(ClientResponse.class, updateParam);

        Assert.assertEquals(400, clientResponseUserGroupEdit.getStatus());

        final String partialExpectedErrorString = "Deleting or editing the domain of an user group is not allowed because";
        final ServiceErrorRestRep actualErrorMsg = clientResponseUserGroupEdit.getEntity(ServiceErrorRestRep.class);
        Assert.assertTrue(actualErrorMsg.getDetailedMessage().contains(partialExpectedErrorString));
    }

    private void editUserGroupWithoutAnyChangeAndExpectSuccess(UserGroupRestRep restRep) {
        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(restRep);
        Assert.assertNotNull(restRep);

        String editApi = getTestEditApi(restRep.getId());

        ClientResponse clientResponseUserGroupEdit = rSys.path(editApi).put(ClientResponse.class, updateParam);

        Assert.assertEquals(200, clientResponseUserGroupEdit.getStatus());
    }

    UserGroup buildUserGroupFromRestRep(UserGroupRestRep restRep) {
        Assert.assertNotNull(restRep);
        Assert.assertFalse(CollectionUtils.isEmpty(restRep.getAttributes()));

        UserGroup userGroup = new UserGroup();
        userGroup.setLabel(restRep.getName());
        userGroup.setDomain(restRep.getDomain());

        for (UserAttributeParam attributeParam : restRep.getAttributes()) {
            userGroup.getAttributes().add(attributeParam.toString());
        }
        return removeDuplicateAttributes(userGroup);
    }

    boolean isSame(UserGroup expected, UserGroup actual) {
        Assert.assertNotNull(expected);
        Assert.assertNotNull(actual);

        return expected.isEqual(actual);
    }

    private void validateUserGroupCommon(UserGroupBaseParam expected, UserGroupRestRep actual) {
        Assert.assertNotNull(actual);

        Assert.assertTrue(actual.getName().equalsIgnoreCase(expected.getLabel()));
        Assert.assertTrue(actual.getDomain().equalsIgnoreCase(expected.getDomain()));
    }

    // Function to validate the Authn provider creation and add resource to the cleanup list.
    private void validateAuthnProviderCreateSuccess(AuthnProviderRestRep resp, int status) {
        Assert.assertEquals(200, status);

        // Add the created authnprovider to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getAuthnProviderDeleteApi(resp.getId());
        CleanupResource authnProviderToCleanup = new CleanupResource("delete", deleteObjectURL, rSys, null);

        registerResourceForCleanup(authnProviderToCleanup);
    }

    private UserGroupRestRep validateUserGroupCreateSuccess(UserGroupCreateParam expected, ClientResponse actual) {
        Assert.assertEquals(200, actual.getStatus());

        UserGroupRestRep resp = actual.getEntity(UserGroupRestRep.class);
        Assert.assertNotNull(resp);

        validateUserGroupCommon(expected, resp);

        UserGroup expectedUserGroup = buildUserGroupFromCreateParam(expected, false);
        UserGroup actualUserGroup = buildUserGroupFromRestRep(resp);
        Assert.assertTrue(isSame(expectedUserGroup, actualUserGroup));

        // Add the created userGroup to cleanup list, so that at the end of this test
        // the object will be destroyed.
        final String deleteObjectURL = this.getTestEditApi(resp.getId());
        CleanupResource userGroupToCleanup = new CleanupResource("delete", deleteObjectURL, rSys, null);

        registerResourceForCleanup(userGroupToCleanup);

        return resp;
    }

    private void validateUserGroupEditSuccess(UserGroup userGroup,
            UserGroupUpdateParam expected,
            ClientResponse actual) {
        Assert.assertEquals(200, actual.getStatus());

        UserGroupRestRep resp = actual.getEntity(UserGroupRestRep.class);
        Assert.assertNotNull(resp);

        validateUserGroupCommon(expected, resp);

        UserGroup expectedUserGroup = buildUserGroupFromUpdateParam(expected, userGroup);
        UserGroup actualUserGroup = buildUserGroupFromRestRep(resp);
        Assert.assertTrue(isSame(expectedUserGroup, actualUserGroup));
    }

    private BulkIdParam validateUserGroupBulkGetSuccess(ClientResponse actual, long expectedIDCount) {
        Assert.assertEquals(200, actual.getStatus());

        BulkIdParam resp = actual.getEntity(BulkIdParam.class);
        Assert.assertNotNull(resp);

        Assert.assertTrue(expectedIDCount <= resp.getIds().size());

        return resp;
    }

    private void validateUserGroupBulkPostSuccess(ClientResponse actual, long expectedIDCount) {
        Assert.assertEquals(200, actual.getStatus());

        UserGroupBulkRestRep resp = actual.getEntity(UserGroupBulkRestRep.class);
        Assert.assertNotNull(resp);

        Assert.assertTrue(expectedIDCount <= resp.getUserGroups().size());
    }

    private void validateUserGroupBadRequest(int expectedStatus, String expectedErrorMsg, ClientResponse actual) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expectedStatus, actual.getStatus());

        final ServiceErrorRestRep actualErrorMsg = actual.getEntity(ServiceErrorRestRep.class);
        Assert.assertTrue(actualErrorMsg.getDetailedMessage().contains(expectedErrorMsg));
    }

    private void validateVDCRoleAssignmentsSuccess(RoleAssignments actual, String expectedEntity,
            List<String> expectedRoles, boolean isGroup) {
        Assert.assertNotNull(actual);
        Assert.assertFalse(CollectionUtils.isEmpty(actual.getAssignments()));

        boolean found = false;
        for (RoleAssignmentEntry roleAssignmentEntry : actual.getAssignments()) {
            Assert.assertNotNull(roleAssignmentEntry);
            if (isGroup) {
                if (expectedEntity.equalsIgnoreCase(roleAssignmentEntry.getGroup()) &&
                        expectedRoles.containsAll(expectedRoles)) {
                    found = true;
                }
            } else {
                if (expectedEntity.equalsIgnoreCase(roleAssignmentEntry.getSubjectId()) &&
                        expectedRoles.containsAll(expectedRoles)) {
                    found = true;
                }
            }
        }
        Assert.assertTrue(found);
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

    private void validateACLAssignmentsSuccess(ACLAssignments actual, String expectedEntity,
            List<String> expectedRoles, boolean isGroup) {
        Assert.assertNotNull(actual);
        Assert.assertFalse(CollectionUtils.isEmpty(actual.getAssignments()));

        boolean found = false;
        for (ACLEntry aclAssignmentEntry : actual.getAssignments()) {
            Assert.assertNotNull(aclAssignmentEntry);
            if (isGroup) {
                if (expectedEntity.equalsIgnoreCase(aclAssignmentEntry.getGroup()) &&
                        expectedRoles.containsAll(expectedRoles)) {
                    found = true;
                }
            } else {
                if (expectedEntity.equalsIgnoreCase(aclAssignmentEntry.getSubjectId()) &&
                        expectedRoles.containsAll(expectedRoles)) {
                    found = true;
                }
            }
        }
        Assert.assertTrue(found);
    }

    private void validateACLAssignmentsRemove(ACLAssignments actual, String expectedEntity,
            boolean isGroup) {
        Assert.assertNotNull(actual);

        boolean found = false;
        for (ACLEntry aclAssignmentEntry : actual.getAssignments()) {
            Assert.assertNotNull(aclAssignmentEntry);
            if (isGroup) {
                if (expectedEntity.equalsIgnoreCase(aclAssignmentEntry.getGroup())) {
                    found = true;
                }
            } else {
                if (expectedEntity.equalsIgnoreCase(aclAssignmentEntry.getSubjectId())) {
                    found = true;
                }
            }
        }
        Assert.assertFalse(found);
    }

    private void validateUserVDCRoles(UserInfo actual, List<String> expectedRoles) {
        Assert.assertNotNull(actual);
        Assert.assertFalse(CollectionUtils.isEmpty(actual.getVdcRoles()));
        Assert.assertTrue(actual.getVdcRoles().containsAll(expectedRoles));
    }

    private void validateNoneUserVDCRoles(UserInfo actual) {
        Assert.assertNotNull(actual);
        Assert.assertTrue(CollectionUtils.isEmpty(actual.getVdcRoles()));
    }

    private void validateUserTenantRoles(UserInfo actual, List<String> expectedRoles) {
        Assert.assertNotNull(actual);
        Assert.assertFalse(CollectionUtils.isEmpty(actual.getHomeTenantRoles()));
        Assert.assertTrue(actual.getHomeTenantRoles().containsAll(expectedRoles));
    }

    private void validateNoneUserTenantRoles(UserInfo actual) {
        Assert.assertNotNull(actual);
        Assert.assertTrue(CollectionUtils.isEmpty(actual.getHomeTenantRoles()));
    }

    private String getTestApi() {
        return TEST_API;
    }

    private String getTestEditApi(URI uri) {
        return String.format(TEST_EDIT_API, uri.toString());
    }

    private String getTestBulkApi() {
        return TEST_BULK_API;
    }

    private String getTestTagsApi() {
        return TEST_TAGS_API;
    }

    private String getAuthnProviderCreateApi() {
        return apiTestAuthnProviderUtils.getAuthnProviderBaseURL();
    }

    private String getAuthnProviderDeleteApi(URI uri) {
        return apiTestAuthnProviderUtils.getAuthnProviderEditURL(uri);
    }

    private String getVDCRoleAssignmentsApi() {
        return TEST_VDC_ROLE_ASSIGNMENT_API;
    }

    private String getUserWhoAmIApi() {
        return TEST_USER_WHOAMI_API;
    }

    private String getTenantRoleAssignmentApi(URI id) {
        return apiTestTenants.getTestRoleAssignmentsApi(id);
    }

    private String getTenantEditApi(URI id) {
        return apiTestTenants.getTestEditApi(id);
    }

    private String getTenantDeleteApi(URI id) {
        return apiTestTenants.getTestDeleteApi(id);
    }

    private String getSubTenantCreateApi() {
        return apiTestTenants.getTestApi();
    }

    private String getProjectCreateApi(URI tenantId) {
        return apiTestTenants.getProjectCreateApi(tenantId);
    }

    private String getProjectApi(URI id) {
        return String.format(TEST_GET_PROJECT_API, id);
    }

    private String getProjectACLAssignmentApi(URI id) {
        return String.format(TEST_PROJECT_ACL_ASSIGNMENTS_API, id);
    }

    private String getDeleteProjectApi(URI id) {
        return String.format(TEST_PROJECT_DELETE_API, id);
    }

    private String getDefaultUserGroupName() {
        return TEST_DEFAULT_USER_GROUP_NAME;
    }

    private Set<String> getDefaultAttributeKeys() {
        return apiTestAuthnProviderUtils.getDefaultAttributeKeys();
    }

    private Set<String> getDefaultAttributeDepartmentValues() {
        return apiTestAuthnProviderUtils.getDefaultAttributeDepartmentValues();
    }

    private Set<String> getDefaultAttributeLocalityValues() {
        return apiTestAuthnProviderUtils.getDefaultAttributeLocalityValues();
    }

    private List<String> getDefaultVDCRoles() {
        return new ArrayList<>(Arrays.asList(TEST_DEFAULT_VDC_ROLES));
    }

    private List<String> getDefaultTenantRoles() {
        return new ArrayList<>(Arrays.asList(TEST_DEFAULT_TENANT_ROLES));
    }

    private List<String> getDefaultACLs() {
        return new ArrayList<>(Arrays.asList(TEST_DEFAULT_ACLS));
    }

    private String getAttributeKey(int index) {
        return apiTestAuthnProviderUtils.getAttributeKey(index);
    }

    private String getAttributeDepartmentValue(int index) {
        return apiTestAuthnProviderUtils.getAttributeDepartmentValue(index);
    }

    private String getAttributeLocalityValue(int index) {
        return apiTestAuthnProviderUtils.getAttributeLocalityValue(index);
    }

    private String getVDCRole(int index) {
        return TEST_DEFAULT_VDC_ROLES[index];
    }

    private String getTenantRole(int index) {
        return TEST_DEFAULT_TENANT_ROLES[index];
    }

    private String getACL(int index) {
        return TEST_DEFAULT_ACLS[index];
    }

    private String getAuthnProviderDomain() {
        return authnProviderDomain;
    }

    private void setAuthnProviderDomain(String domain) {
        authnProviderDomain = domain;
    }

    private String getUserWithDomain(int index) {
        return apiTestAuthnProviderUtils.getUserWithDomain(index);
    }

    private String getLDAPUserPassword() {
        return apiTestAuthnProviderUtils.getLDAPUserPassword();
    }

    private String getLDAPGroup(int index) {
        return apiTestAuthnProviderUtils.getLDAPGroup(index);
    }

    private String getSecondDomain() {
        return apiTestAuthnProviderUtils.getSecondDomain();
    }

    private String getOneLetterDomain() {
        return apiTestAuthnProviderUtils.getOneLetterDomain();
    }

    private URI createDefaultAuthnProvider(String description) {
        // Create a default authnprovider.
        AuthnCreateParam authnProviderCreateParam = apiTestAuthnProviderUtils.getDefaultAuthnCreateParam(description);

        // Add the one letter domain to make sure that works fine with User Group.
        authnProviderCreateParam.getDomains().add(getOneLetterDomain());

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

    private void updateTenantGroups(URI tenantId, String group) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(getAuthnProviderDomain());
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getAdd().add(rootMapping);

        TenantOrgRestRep getTenantResp = rSys.path(getTenantEditApi(tenantId)).get(TenantOrgRestRep.class);
        Assert.assertNotNull(getTenantResp.getName());
        tenantUpdate.setLabel(getTenantResp.getName());

        ClientResponse resp = rSys.path(getTenantEditApi(tenantId)).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
    }

    private void removeTenantUserMapping(URI tenantId, String group) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setRemove(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(getAuthnProviderDomain());
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getRemove().add(rootMapping);

        TenantOrgRestRep getTenantResp = rSys.path(getTenantEditApi(tenantId)).get(TenantOrgRestRep.class);
        Assert.assertNotNull(getTenantResp.getName());
        tenantUpdate.setLabel(getTenantResp.getName());

        ClientResponse resp = rSys.path(getTenantEditApi(tenantId)).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
    }

    private void removeUserMappingGroups(URI tenantId, String group) {
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setRemove(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain(getAuthnProviderDomain());
        rootMapping.getGroups().add(group);
        tenantUpdate.getUserMappingChanges().getRemove().add(rootMapping);

        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam addMapping = new UserMappingParam();
        addMapping.setDomain(getAuthnProviderDomain());
        tenantUpdate.getUserMappingChanges().getAdd().add(addMapping);

        TenantOrgRestRep getTenantResp = rSys.path(getTenantEditApi(tenantId)).get(TenantOrgRestRep.class);
        Assert.assertNotNull(getTenantResp.getName());
        tenantUpdate.setLabel(getTenantResp.getName());

        ClientResponse resp = rSys.path(getTenantEditApi(tenantId)).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
    }

    private URI createTestTenant() {
        TenantCreateParam createParam = apiTestTenants.getDefaultTenantCreateParam("Default Tenant creation " +
                "for User group test.");
        TenantOrgRestRep resp = rSys.path(getSubTenantCreateApi()).post(TenantOrgRestRep.class, createParam);
        Assert.assertNotNull(resp.getId());

        // Add the created tenant to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getTenantDeleteApi(resp.getId());
        CleanupResource tenantToCleanup = new CleanupResource("post", deleteObjectURL, rSys, null);

        registerResourceForCleanup(tenantToCleanup);

        return resp.getId();
    }

    private URI createTestProject(URI tenantId) {
        ProjectParam createParam = apiTestTenants.getDefaultProjectParam("UserGroupProject");

        ProjectElement resp = rSys.path(getProjectCreateApi(tenantId)).post(ProjectElement.class, createParam);
        Assert.assertNotNull(resp.getId());

        // Add the created project to cleanup list, so that at the end of this test
        // the resource will be destroyed.
        final String deleteObjectURL = getDeleteProjectApi(resp.getId());
        CleanupResource projectToCleanup = new CleanupResource("post", deleteObjectURL, rSys, null);

        registerResourceForCleanup(projectToCleanup);

        return resp.getId();
    }

    private UserGroupCreateParam getDefaultUserGroupCreateParam() {
        UserGroupCreateParam createParam = new UserGroupCreateParam();
        createParam.setLabel(getDefaultUserGroupName());
        createParam.setDomain(getAuthnProviderDomain());

        UserAttributeParam userAttributeParam = new UserAttributeParam();
        userAttributeParam.setKey(getAttributeKey(0));
        userAttributeParam.getValues().add(getAttributeDepartmentValue(0));
        userAttributeParam.getValues().add(getAttributeDepartmentValue(2));

        UserAttributeParam userAttributeParam1 = new UserAttributeParam();
        userAttributeParam1.setKey(getAttributeKey(0));
        userAttributeParam1.getValues().add(getAttributeDepartmentValue(0));
        userAttributeParam1.getValues().add(getAttributeDepartmentValue(2));

        UserAttributeParam userAttributeParam2 = new UserAttributeParam();
        userAttributeParam2.setKey(getAttributeKey(0));
        userAttributeParam2.getValues().add(getAttributeDepartmentValue(2));

        UserAttributeParam userAttributeParam3 = new UserAttributeParam();
        userAttributeParam3.setKey(getAttributeKey(1));
        userAttributeParam3.getValues().add(getAttributeLocalityValue(0));

        createParam.getAttributes().add(userAttributeParam);
        createParam.getAttributes().add(userAttributeParam1);
        createParam.getAttributes().add(userAttributeParam2);
        createParam.getAttributes().add(userAttributeParam3);

        return createParam;
    }

    private UserGroupUpdateParam getUserGroupUpdateParamFromRestRep(UserGroupRestRep restRep) {
        Assert.assertNotNull(restRep);
        UserGroupUpdateParam updateParam = new UserGroupUpdateParam();
        updateParam.setLabel(restRep.getName());
        updateParam.setDomain(restRep.getDomain());
        updateParam.getAddAttributes().addAll(restRep.getAttributes());

        return updateParam;
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
        RoleAssignmentChanges roleAssignmentChanges = new RoleAssignmentChanges();
        return roleAssignmentChanges;
    }

    private ACLEntry getACLAssignmentEntry(String entity, List<String> acls, boolean isGroup) {
        ACLEntry aclAssignmentEntry = new ACLEntry();
        if (isGroup) {
            aclAssignmentEntry.setGroup(entity);
        } else {
            aclAssignmentEntry.setSubjectId(entity);
        }
        aclAssignmentEntry.getAces().addAll(acls);

        return aclAssignmentEntry;
    }

    private ACLAssignmentChanges getDefaultACLAssignmentChanges() {
        ACLAssignmentChanges aclAssignmentChanges = new ACLAssignmentChanges();
        return aclAssignmentChanges;
    }

    @Test
    public void testUserGroupCreationWithOutName() {
        final String testName = "testUserGroupCreationWithOutName - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Set the label to null
        createParam.setLabel(null);

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        String partialErrorString = "Required parameter label was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithInvalidName() {
        final String testName = "testUserGroupCreationWithInvalidName - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Set the label to null
        String nameWithAt = createParam.getLabel() + "@some";
        createParam.setLabel(nameWithAt);

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        String partialErrorString = "Invalid value %s for parameter label";
        partialErrorString = String.format(partialErrorString, nameWithAt);

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithOutDomain() {
        final String testName = "testUserGroupCreationWithOutDomain - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Set the domain to null
        createParam.setDomain(null);

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        String partialErrorString = "Required parameter domain was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithInvalidDomain() {
        final String testName = "testUserGroupCreationWithInvalidDomain - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Set the domain to invalid domain that is not
        // available with any of the pre-configured
        // authnProvider.
        createParam.setDomain("invalidDomain.com");

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        String partialErrorString = "Invalid value %s for parameter domain";
        partialErrorString = String.format(partialErrorString, createParam.getDomain());

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithOutAttributes() {
        final String testName = "testUserGroupCreationWithOutAttributes - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Remove all the attributes.
        createParam.getAttributes().clear();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        String partialErrorString = "Required parameter attributes was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithOutAttributeKey() {
        final String testName = "testUserGroupCreationWithOutAttributeKey - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Remove the key from one of the default attribute key.
        Iterator<UserAttributeParam> it = createParam.getAttributes().iterator();
        while (it.hasNext()) {
            UserAttributeParam userAttributeParam = it.next();
            if (userAttributeParam != null) {
                userAttributeParam.setKey(null);
                break;
            }
        }

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        String partialErrorString = "Required parameter key was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithOutAttributeValues() {
        final String testName = "testUserGroupCreationWithOutAttributeValues - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Remove the values from one of the default attribute key.
        Iterator<UserAttributeParam> it = createParam.getAttributes().iterator();
        while (it.hasNext()) {
            UserAttributeParam userAttributeParam = it.next();
            if (userAttributeParam != null) {
                userAttributeParam.getValues().clear();
                break;
            }
        }

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        String partialErrorString = "Required parameter values was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationSuccess() {
        final String testName = "testUserGroupCreationSuccess - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithOneLetterDomainNameSuccess() {
        final String testName = "testUserGroupCreationWithOneLetterDomainNameSuccess - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();
        createParam.setDomain(getOneLetterDomain());

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);
    }

    @Test
    public void testDeleteAuthnProviderWithUserGroup() {
        final String testName = "testDeleteAuthnProviderWithUserGroup - ";
        URI authProviderId = createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        String authProviderDeleteApi = getAuthnProviderDeleteApi(authProviderId);
        ClientResponse authProviderDeleteResp = rSys.path(authProviderDeleteApi).delete(ClientResponse.class);
        String partialErrorMessage = "user groups are using the domains of the authentication provider";
        validateUserGroupBadRequest(400, partialErrorMessage, authProviderDeleteResp);
    }

    @Test
    public void testUserGroupCreationWithSameName() {
        final String testName = "testUserGroupCreationWithSameName - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();

        // Try to create a user group with same name. It should fail.
        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorString = "A component/resource with the label %s already exists";
        partialErrorString = String.format(partialErrorString, createParam.getLabel());
        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithMatchingPropertiesAndDifferentName() {
        final String testName = "testUserGroupCreationWithMatchingPropertiesAndDifferentName - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();
        String oldName = createParam.getLabel();

        // Change the name something different,
        // so that other properties (domain and attributes) will be same.
        // And this should give error back, saying existing user group
        // with same domain and attributes.
        createParam.setLabel("NewName");

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorString = "Operation not allowed. Overlapping attributes found between %s and [%s]";
        partialErrorString = String.format(partialErrorString, createParam.getLabel(), oldName);

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationToTestNewSubGroup() {
        final String testName = "testUserGroupCreationToTestNewSubGroup - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();
        String oldName = createParam.getLabel();

        // Change the name something different,
        // so that other properties (domain and attributes) will be same.
        // And this should give error back, saying existing user group
        // with same domain and attributes.
        createParam.setLabel("NewName");

        createParam.getAttributes().clear();

        // Attribute key 0 and value 0 is already part of the
        // another group.
        UserAttributeParam attributeParam = new UserAttributeParam();
        attributeParam.setKey(getAttributeKey(0));
        attributeParam.getValues().add(getAttributeDepartmentValue(0));

        createParam.getAttributes().add(attributeParam);

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorString = "Operation not allowed. Overlapping attributes found between %s and [%s]";
        partialErrorString = String.format(partialErrorString, createParam.getLabel(), oldName);

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationToTestExistingSubGroup() {
        final String testName = "testUserGroupCreationToTestExistingSubGroup - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();
        String oldName = createParam.getLabel();

        // Change the name something different,
        // so that other properties (domain and attributes) will be same.
        // And this should give error back, saying existing user group
        // with same domain and attributes.
        createParam.setLabel("NewName");

        createParam.getAttributes().clear();

        // Attribute key 0 and value 0 is already part of the
        // another group.
        UserAttributeParam attributeParam = new UserAttributeParam();
        attributeParam.setKey(getAttributeKey(0));
        attributeParam.getValues().add(getAttributeDepartmentValue(0));
        attributeParam.getValues().add(getAttributeDepartmentValue(1));
        attributeParam.getValues().add(getAttributeDepartmentValue(2));

        createParam.getAttributes().add(attributeParam);

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorString = "Operation not allowed. Overlapping attributes found between %s and [%s]";
        partialErrorString = String.format(partialErrorString, createParam.getLabel(), oldName);

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupOverlapCombinationTests1() {
        final String testName = "testUserGroupOverlapCombinationTests1 - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();
        String oldName = createParam.getLabel();

        // Change the name something different,
        // so that other properties (domain and attributes) will be same.
        // And this should give error back, saying existing user group
        // with same domain and attributes.
        createParam.setLabel("NewName");

        createParam.getAttributes().clear();

        // Attribute key 0 and value 0 is already part of the
        // another group.
        UserAttributeParam attributeParam = new UserAttributeParam();

        attributeParam.setKey(getAttributeKey(0));
        attributeParam.getValues().add(getAttributeDepartmentValue(0));
        attributeParam.getValues().add(getAttributeDepartmentValue(1));
        attributeParam.getValues().add(getAttributeDepartmentValue(2));

        createParam.getAttributes().add(attributeParam);

        // Now add some random attributes that are not part of existing
        // group. This should make the edit successful.
        UserAttributeParam attributeParam1 = new UserAttributeParam();
        attributeParam1.setKey("RandomKey");
        attributeParam1.getValues().clear();
        attributeParam1.getValues().add("RandomValue1");
        attributeParam1.getValues().add("RandomValue2");

        createParam.getAttributes().add(attributeParam1);

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupOverlapCombinationTests2() {
        final String testName = "testUserGroupOverlapCombinationTests2 - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();
        String oldName = createParam.getLabel();

        // Change the name something different,
        // so that other properties (domain and attributes) will be same.
        // And this should give error back, saying existing user group
        // with same domain and attributes.
        createParam.setLabel("NewName");

        // Now add some random attributes that are not part of existing
        // group. Since, we kept all the existing attributes as it is
        // and adding this new RandomKey attribute, the existing
        // group will be overlapping with this NewName group.
        UserAttributeParam attributeParam1 = new UserAttributeParam();
        attributeParam1.setKey("RandomKey");
        attributeParam1.getValues().add("RandomValue1");
        attributeParam1.getValues().add("RandomValue2");

        createParam.getAttributes().add(attributeParam1);

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorString = "Operation not allowed. Overlapping attributes found between %s and [%s]";
        partialErrorString = String.format(partialErrorString, createParam.getLabel(), oldName);

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupOverlapCombinationTests3() {
        final String testName = "testUserGroupOverlapCombinationTests3 - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();
        String oldName = createParam.getLabel();

        // Change the name something different,
        // so that other properties (domain and attributes) will be same.
        // And this should give error back, saying existing user group
        // with same domain and attributes.
        createParam.setLabel("NewName");

        createParam.getAttributes().clear();

        // Attribute key 0 and value 0 is already part of the
        // another group.
        UserAttributeParam attributeParam = new UserAttributeParam();

        attributeParam.setKey(getAttributeKey(0));
        attributeParam.getValues().add(getAttributeDepartmentValue(0));
        attributeParam.getValues().add(getAttributeDepartmentValue(1));
        attributeParam.getValues().add(getAttributeDepartmentValue(2));

        createParam.getAttributes().add(attributeParam);

        // Now add some random attributes that are not part of existing
        // group. This should make the edit successful.
        UserAttributeParam attributeParam1 = new UserAttributeParam();
        attributeParam1.setKey("RandomKey");
        attributeParam1.getValues().clear();
        attributeParam1.getValues().add("RandomValue1");
        attributeParam1.getValues().add("RandomValue2");

        createParam.getAttributes().add(attributeParam1);

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Now create a user group that will overlap with both the existing groups.
        UserGroupCreateParam newCreateParam = getDefaultUserGroupCreateParam();
        newCreateParam.setLabel("RandomGroup");

        // Remove all the attributes.
        newCreateParam.getAttributes().clear();

        // Now add the attribute that matches with the first group.
        UserAttributeParam firstAttribute = new UserAttributeParam();
        firstAttribute.setKey(getAttributeKey(0));
        firstAttribute.getValues().add(getAttributeDepartmentValue(0));
        newCreateParam.getAttributes().add(firstAttribute);

        // Now add the second attribute that matches with the second group.
        UserAttributeParam secondAttribute = new UserAttributeParam();
        secondAttribute.setKey("RandomKey");
        secondAttribute.getValues().add("RandomValue1");
        newCreateParam.getAttributes().add(secondAttribute);

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, newCreateParam);

        String partialErrorString = "Operation not allowed. Overlapping attributes found between %s";
        partialErrorString = String.format(partialErrorString, newCreateParam.getLabel());

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithOneLetterDomainNameAndMatchingPropertiesAndDifferentName() {
        final String testName = "testUserGroupCreationWithOneLetterDomainNameAndMatchingPropertiesAndDifferentName - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Change to one letter domain name.
        createParam.setDomain(getOneLetterDomain());

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();
        String oldName = createParam.getLabel();

        // Change the name something different,
        // so that other properties (domain and attributes) will be same.
        // And this should give error back, saying existing user group
        // with same domain and attributes.
        createParam.setLabel("NewName");

        // Change to one letter domain name.
        createParam.setDomain(getOneLetterDomain());

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorString = "Operation not allowed. Overlapping attributes found between %s and [%s]";
        partialErrorString = String.format(partialErrorString, createParam.getLabel(), oldName);

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithMatchingKeyAndDifferentValues() {
        final String testName = "testUserGroupCreationWithMatchingKeyAndDifferentValues - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();

        // Change the name something different and keep the same
        // attributes key but with different values for each key.
        // This should be successful.
        createParam.setLabel("NewName");
        Iterator<UserAttributeParam> it = createParam.getAttributes().iterator();
        while (it.hasNext()) {
            UserAttributeParam userAttributeParam = it.next();
            if (userAttributeParam != null) {
                userAttributeParam.getValues().clear();
                userAttributeParam.getValues().add("NewValue");
            }
        }

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupCreationWithMatchingValuesAndDifferentKeys() {
        final String testName = "testUserGroupCreationWithMatchingValuesAndDifferentKeys - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        createParam = getDefaultUserGroupCreateParam();

        // Change the name something different and keep the same
        // attributes key but with different values for each key.
        // This should be successful.
        createParam.setLabel("NewName");
        Iterator<UserAttributeParam> it = createParam.getAttributes().iterator();
        while (it.hasNext()) {
            UserAttributeParam userAttributeParam = it.next();
            if (userAttributeParam != null) {
                userAttributeParam.setKey(userAttributeParam.getKey() + "NewKey");
            }
        }

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);
    }

    @Test
    public void testUserGroupEditWithoutName() {
        final String testName = "testUserGroupEditWithoutName - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Set remove the name from the update param, so that the request will fail.
        updateParam.setLabel(null);

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        String partialErrorString = "Required parameter label was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditWithDifferentName() {
        final String testName = "testUserGroupEditWithDifferentName - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Change the name in the update request. This is also not supported.
        updateParam.setLabel("NewName");

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        String partialErrorString = "Cannot rename the User group %s";
        partialErrorString = String.format(partialErrorString, userGroupCreateResp.getName());

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditWithoutDomain() {
        final String testName = "testUserGroupEditWithoutDomain - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Remove the domain from the update param. This will return error.
        updateParam.setDomain(null);

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        String partialErrorString = "Required parameter domain was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditWithInvalidDomain() {
        final String testName = "testUserGroupEditWithInvalidDomain - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Set the domain to invalid domain that is not
        // available with any of the pre-configured authnProvider.
        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);
        updateParam.setDomain("InvalidDomain.com");

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        String partialErrorString = "Invalid value %s for parameter domain";
        partialErrorString = String.format(partialErrorString, updateParam.getDomain());

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditWithoutAttributeKey() {
        final String testName = "testUserGroupEditWithoutAttributeKey - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Remove the key from one of the default attribute key.
        Iterator<UserAttributeParam> it = updateParam.getAddAttributes().iterator();
        while (it.hasNext()) {
            UserAttributeParam userAttributeParam = it.next();
            if (userAttributeParam != null) {
                userAttributeParam.setKey(null);
                break;
            }
        }

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        String partialErrorString = "Required parameter key was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditWithoutAttributeValues() {
        final String testName = "testUserGroupEditWithoutAttributeValues - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Remove the key from one of the default attribute key.
        Iterator<UserAttributeParam> it = updateParam.getAddAttributes().iterator();
        while (it.hasNext()) {
            UserAttributeParam userAttributeParam = it.next();
            if (userAttributeParam != null) {
                userAttributeParam.getValues().clear();
                break;
            }
        }

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        String partialErrorString = "Required parameter values was missing or empty";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditWithoutAttributes() {
        final String testName = "testUserGroupEditWithoutAttributes - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroup createdUserGroup = buildUserGroupFromRestRep(userGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Clear both add and remove attributes. This should be successful.
        updateParam.getAddAttributes().clear();
        updateParam.getRemoveAttributes().clear();

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        validateUserGroupEditSuccess(createdUserGroup, updateParam, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditByChangingDomain() {
        final String testName = "testUserGroupEditByChangingDomain - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroup createdUserGroup = buildUserGroupFromRestRep(userGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Clear both add and remove attributes. This should be successful.
        updateParam.getAddAttributes().clear();
        updateParam.getRemoveAttributes().clear();

        // Change the domain.
        updateParam.setDomain(getSecondDomain());

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        validateUserGroupEditSuccess(createdUserGroup, updateParam, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditByAddingAttributes() {
        final String testName = "testUserGroupEditByAddingAttributes - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroup createdUserGroup = buildUserGroupFromRestRep(userGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Add one additional attribute to the user group.
        UserAttributeParam attributeParam = new UserAttributeParam();
        attributeParam.setKey("newKey");
        attributeParam.getValues().add("newValue1");
        attributeParam.getValues().add("newValue1");
        attributeParam.getValues().add("newValue2");
        updateParam.getAddAttributes().add(attributeParam);

        // Clear both add and remove attributes.
        updateParam.getRemoveAttributes().clear();

        // Change the domain.
        updateParam.setDomain(getSecondDomain());

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        validateUserGroupEditSuccess(createdUserGroup, updateParam, clientUserGroupEditResp);
    }

    @Test
    public void testUserGroupEditByRemovingAllAttributes() {
        final String testName = "testUserGroupEditByRemovingAllAttributes - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        UserGroupUpdateParam updateParam = getUserGroupUpdateParamFromRestRep(userGroupCreateResp);

        // Add all the attributes to the remove list.
        for (UserAttributeParam userAttributeParam : userGroupCreateResp.getAttributes()) {
            Assert.assertNotNull(userAttributeParam);
            updateParam.getRemoveAttributes().add(userAttributeParam.getKey());
        }

        // Clear the add list. So that, nothing will be added new.
        updateParam.getAddAttributes().clear();

        String testEditAPI = getTestEditApi(userGroupCreateResp.getId());
        ClientResponse clientUserGroupEditResp = rSys.path(testEditAPI).put(ClientResponse.class, updateParam);

        String partialErrorString = "Attempt to remove the last attribute is not allowed.  At least one attribute must be in the user group.";

        validateUserGroupBadRequest(400, partialErrorString, clientUserGroupEditResp);
    }

    @Test
    public void testInvalidUserGroupWithVDCRoleAssignment() throws NoSuchAlgorithmException {
        final String testName = "testInvalidUserGroupWithVDCRoleAssignment - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Update the provider tenant user mapping with the
        // just created user group "Depart_Dev".
        updateTenantGroups(rootTenantId, userGroupCreateResp.getName());

        String roleAssignmentsApi = getVDCRoleAssignmentsApi();
        boolean isGroup = true;

        // Assigning all the VDC roles to InvalidName group.
        // This InvalidName group is neither in LDAP/AD or in the local
        // user group.
        RoleAssignmentEntry roleAssignmentEntry1 = getRoleAssignmentEntry("InvalidName",
                getDefaultVDCRoles(), isGroup);
        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntry1);

        ClientResponse clientResponseRoleAssignments = rSys.path(roleAssignmentsApi).put(ClientResponse.class, roleAssignmentChanges);
        String partialErrorMsg = "Search for the following failed for this system, or could not be found for this system: %s";
        partialErrorMsg = String.format(partialErrorMsg, roleAssignmentEntry1.getGroup().toUpperCase());
        validateUserGroupBadRequest(400, partialErrorMsg, clientResponseRoleAssignments);

        // Now remove the user group from the
        // provider tenant user mappings.
        removeTenantUserMapping(rootTenantId, userGroupCreateResp.getName());
    }

    @Test
    public void testUserGroupWithVDCRoleAssignment() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupWithVDCRoleAssignment - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Update the provider tenant user mapping with the
        // just created user group "Depart_Dev".
        updateTenantGroups(rootTenantId, userGroupCreateResp.getName());

        String roleAssignmentsApi = getVDCRoleAssignmentsApi();

        boolean isGroup = true;

        // Assigning all the VDC roles to Depart_Dev user group
        // (with attributes department = [ENG, DEV] and l = [Boston]
        RoleAssignmentEntry roleAssignmentEntry1 = getRoleAssignmentEntry(userGroupCreateResp.getName(),
                getDefaultVDCRoles(), isGroup);
        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntry1);

        RoleAssignments roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsSuccess(roleAssignments, userGroupCreateResp.getName(), getDefaultVDCRoles(), isGroup);

        // Create a user whose attributes matches with the above created
        // user group "Depart_Dev". Matching LDAP user is ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(getUserWithDomain(4), getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateUserVDCRoles(ldapViPRUser5UserInfo, getDefaultVDCRoles());

        // Now try to delete the user group "Depart_Dev".
        // It should fail, as it is associated with the VDC role assignments and
        // provider tenants user mapping group.
        deleteUserGroupAndExpectFailure(userGroupCreateResp.getId());

        // Now try to change the domain the of the user group "Depart_Dev".
        // It should fail, as it is associated with the VDC role assginments and
        // provider tenants user mapping group.
        changeUserGroupDomainAndExpectFailure(userGroupCreateResp);

        // Edit the user group but dont change any properties in the group.
        // This should be successful irrespective of whether it is used in
        // any role or acl or user mapping assignments.
        editUserGroupWithoutAnyChangeAndExpectSuccess(userGroupCreateResp);

        // Now remove the user group from the role assignments.
        roleAssignmentChanges.getAdd().clear();
        roleAssignmentChanges.getRemove().add(roleAssignmentEntry1);

        roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsRemove(roleAssignments, userGroupCreateResp.getName(), isGroup);

        // Now the user should not have any roles associated with the
        // user group "Depart_Dev".
        ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateNoneUserVDCRoles(ldapViPRUser5UserInfo);

        // Now remove the user group from the
        // provider tenant user mappings.
        removeTenantUserMapping(rootTenantId, userGroupCreateResp.getName());
    }

    @Test
    public void testInvalidUserGroupWithTenantRoleAssignment() throws NoSuchAlgorithmException {
        final String testName = "testInvalidUserGroupWithTenantRoleAssignment - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Update the tenant user mapping with the
        // just created user group "Depart_Dev".
        updateTenantGroups(testTenantId, userGroupCreateResp.getName());

        String roleAssignmentsApi = getTenantRoleAssignmentApi(testTenantId);
        boolean isGroup = true;

        // Assigning all the tenant roles to InvalidName group.
        // This group is neither available in LDAP/AD or local user group.
        RoleAssignmentEntry roleAssignmentEntry1 = getRoleAssignmentEntry("InvalidName",
                getDefaultTenantRoles(), isGroup);
        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntry1);

        ClientResponse clientResponseRoleAssignments = rSys.path(roleAssignmentsApi).put(ClientResponse.class, roleAssignmentChanges);
        String partialErrorMsg = "Search for the following failed for this system, or could not be found for this system: %s";
        partialErrorMsg = String.format(partialErrorMsg, roleAssignmentEntry1.getGroup().toUpperCase());
        validateUserGroupBadRequest(400, partialErrorMsg, clientResponseRoleAssignments);

        // Now remove the user group from the provider tenant user mappings.
        removeTenantUserMapping(testTenantId, userGroupCreateResp.getName());
    }

    @Test
    public void testUserGroupWithTenantUserMappings() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupWithTenantUserMappings - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Update the tenant user mapping with the
        // just created user group "Depart_Dev".
        updateTenantGroups(testTenantId, userGroupCreateResp.getName());

        // Create a user whose attributes matches with the above created
        // user group "Depart_Dev". Matching LDAP user is ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(getUserWithDomain(4), getLDAPUserPassword());

        // Get the tenant of the user ldapViPRUser5.
        String getTenantApi = "/tenant";
        TenantResponse ldapViPRUser5Tenant = ldapViPRUser5.path(getTenantApi).get(TenantResponse.class);
        Assert.assertNotNull(ldapViPRUser5Tenant);
        Assert.assertEquals(testTenantId, ldapViPRUser5Tenant.getTenant());

        // Now try to delete the user group "Depart_Dev".
        // It should fail, as it is associated with
        // tenants user mapping group.
        deleteUserGroupAndExpectFailure(userGroupCreateResp.getId());

        // Now try to change the domain the of the user group "Depart_Dev".
        // It should fail, as it is associated with tenants user mapping group.
        changeUserGroupDomainAndExpectFailure(userGroupCreateResp);

        // Edit the user group but dont change any properties in the group.
        // This should be successful irrespective of whether it is used in
        // any role or acl or user mapping assignments.
        editUserGroupWithoutAnyChangeAndExpectSuccess(userGroupCreateResp);

        // Now remove the user group from the tenant user mappings.
        removeTenantUserMapping(testTenantId, userGroupCreateResp.getName());
    }

    @Test
    public void testUserGroupWithTenantRoleAssignment() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupWithTenantRoleAssignment - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Update the tenant user mapping with the
        // just created user group "Depart_Dev".
        updateTenantGroups(testTenantId, userGroupCreateResp.getName());

        String roleAssignmentsApi = getTenantRoleAssignmentApi(testTenantId);
        boolean isGroup = true;

        // Assigning all the Tenant roles to Depart_Dev user group
        // (with attributes department = [ENG, DEV] and l = [Boston]
        RoleAssignmentEntry roleAssignmentEntry1 = getRoleAssignmentEntry(userGroupCreateResp.getName(),
                getDefaultTenantRoles(), isGroup);
        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntry1);

        RoleAssignments roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsSuccess(roleAssignments, userGroupCreateResp.getName(), getDefaultTenantRoles(), isGroup);

        // Create a user whose attributes matches with the above created
        // user group "Depart_Dev". Matching LDAP user is ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(getUserWithDomain(4), getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateUserTenantRoles(ldapViPRUser5UserInfo, getDefaultTenantRoles());

        // Now try to delete the user group "Depart_Dev".
        // It should fail, as it is associated with the tenant role assignments and
        // tenants user mapping group.
        deleteUserGroupAndExpectFailure(userGroupCreateResp.getId());

        // Now try to change the domain the of the user group "Depart_Dev".
        // It should fail, as it is associated with the tenant role assignments and
        // tenants user mapping group.
        changeUserGroupDomainAndExpectFailure(userGroupCreateResp);

        // Edit the user group but dont change any properties in the group.
        // This should be successful irrespective of whether it is used in
        // any role or acl or user mapping assignments.
        editUserGroupWithoutAnyChangeAndExpectSuccess(userGroupCreateResp);

        // Now remove the user group from the role assignments.
        roleAssignmentChanges.getAdd().clear();
        roleAssignmentChanges.getRemove().add(roleAssignmentEntry1);

        roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsRemove(roleAssignments, userGroupCreateResp.getName(), isGroup);

        // Now the user should not have any roles associated with the
        // user group "Depart_Dev".
        ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateNoneUserTenantRoles(ldapViPRUser5UserInfo);

        // Now remove the user group from the tenant user mappings.
        removeTenantUserMapping(testTenantId, userGroupCreateResp.getName());
    }

    @Test
    public void testUserGroupWithProjectACLAssignment() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupWithProjectACLAssignment - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Update the provider tenant user mapping with the
        // just created user group "Depart_Dev".
        updateTenantGroups(testTenantId, userGroupCreateResp.getName());

        // Create a test project for the just created tenant.
        URI projectId = createTestProject(testTenantId);
        boolean isGroup = true;

        String aclAssignmentsApi = getProjectACLAssignmentApi(projectId);

        // Assigning all the project acls to Depart_Dev user group
        // (with attributes department = [ENG, DEV] and l = [Boston]
        List<String> acls = new ArrayList<String>();
        acls.add(getACL(1));
        ACLEntry aclAssignmentEntry1 = getACLAssignmentEntry(userGroupCreateResp.getName(), acls, isGroup);
        ACLAssignmentChanges aclAssignmentChanges = getDefaultACLAssignmentChanges();
        aclAssignmentChanges.getAdd().add(aclAssignmentEntry1);

        ACLAssignments aclAssignments = rSys.path(aclAssignmentsApi).put(ACLAssignments.class, aclAssignmentChanges);
        validateACLAssignmentsSuccess(aclAssignments, userGroupCreateResp.getName(), acls, isGroup);

        // Create a user whose attributes matches with the above created
        // user group "Depart_Dev". Matching LDAP user is ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(getUserWithDomain(4), getLDAPUserPassword());

        ProjectRestRep ldapViPRUser5ProjectInfo = ldapViPRUser5.path(getProjectApi(projectId)).get(ProjectRestRep.class);
        Assert.assertEquals(projectId, ldapViPRUser5ProjectInfo.getId());

        // Now try to delete the user group "Depart_Dev".
        // It should fail, as it is associated with the project acls assignments and
        // tenant user mappings.
        deleteUserGroupAndExpectFailure(userGroupCreateResp.getId());

        // Now try to change the domain the of the user group "Depart_Dev".
        // It should fail, as it is associated with the project acls assignments and
        // tenant user mappings.
        changeUserGroupDomainAndExpectFailure(userGroupCreateResp);

        // Edit the user group but dont change any properties in the group.
        // This should be successful irrespective of whether it is used in
        // any role or acl or user mapping assignments.
        editUserGroupWithoutAnyChangeAndExpectSuccess(userGroupCreateResp);

        // Now remove the user group from the acl assignments.
        aclAssignmentChanges.getAdd().clear();
        aclAssignmentChanges.getRemove().add(aclAssignmentEntry1);

        aclAssignments = rSys.path(aclAssignmentsApi).put(ACLAssignments.class, aclAssignmentChanges);
        validateACLAssignmentsRemove(aclAssignments, userGroupCreateResp.getName(), isGroup);

        // Now the user should not have any acls associated with the
        // user group "Depart_Dev". This is done by just
        // querying about the just created project. Since user
        // does not have any project role or tenant roles, the request
        // will fail.
        ClientResponse clientResponseProjectInfo = ldapViPRUser5.path(getProjectApi(projectId)).get(ClientResponse.class);
        Assert.assertEquals(403, clientResponseProjectInfo.getStatus());

        // Now remove the user group from the tenant user mappings.
        removeTenantUserMapping(testTenantId, userGroupCreateResp.getName());
    }

    @Test
    public void testUserGroupCreateByNonSecurityAdmin() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupCreateByNonSecurityAdmin - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        // Update one of the provider tenant user mapping with the
        // with null group.
        updateTenantGroups(rootTenantId, null);

        // Assigning the VDC role System Admin to ldapViPRUser5.
        List<String> roles = new ArrayList<String>();
        roles.add(getVDCRole(0));
        String userNameWithDomain = getUserWithDomain(4);
        boolean isGroup = false;

        RoleAssignmentEntry roleAssignmentEntry1 = getRoleAssignmentEntry(userNameWithDomain, roles, isGroup);
        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntry1);

        String roleAssignmentsApi = getVDCRoleAssignmentsApi();

        RoleAssignments roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsSuccess(roleAssignments, userNameWithDomain, roles, isGroup);

        // Create a user ldpaViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(userNameWithDomain, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateUserVDCRoles(ldapViPRUser5UserInfo, roles);

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Try to create a user group by non security admin user (ldapViPRUser5).
        ClientResponse clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorMessage = "Insufficient permissions for user %s";
        partialErrorMessage = String.format(partialErrorMessage, userNameWithDomain.toLowerCase());
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Try to get a list of user groups by non security/tenant admin or project owner (ldapViPRUser5).
        clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).get(ClientResponse.class);

        partialErrorMessage = "Insufficient permissions for user %s";
        partialErrorMessage = String.format(partialErrorMessage, userNameWithDomain.toLowerCase());
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Test the bulk api. Here expecting true as ldapViPRUser5 is a sysadmin
        testUserGroupBulkApi(ldapViPRUser5, true, true);

        // Now remove the role assignments for the user..
        roleAssignmentChanges.getAdd().clear();
        roleAssignmentChanges.getRemove().add(roleAssignmentEntry1);

        roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsRemove(roleAssignments, userNameWithDomain, isGroup);

        // Now remove the user group from the tenant user mappings.
        removeTenantUserMapping(rootTenantId, null);
    }

    @Test
    public void testUserGroupCreateByTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupCreateByTenantAdmin - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Remove the group from just created tenant user mapping.
        // So that, all the users in the domain can be assigned with
        // tenant roles. Here getting the ldapGroup(2) as that is the
        // one used as default one for creating the tenant.
        removeUserMappingGroups(testTenantId, getLDAPGroup(2));

        // Assigning the VDC role Tenant Admin to ldapViPRUser5.
        List<String> roles = new ArrayList<String>();
        roles.add(getTenantRole(0));
        String userNameWithDomain = getUserWithDomain(4);

        String roleAssignmentsApi = getTenantRoleAssignmentApi(testTenantId);
        boolean isGroup = false;

        RoleAssignmentEntry roleAssignmentEntry1 = getRoleAssignmentEntry(userNameWithDomain, roles, isGroup);
        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntry1);

        RoleAssignments roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsSuccess(roleAssignments, userNameWithDomain, roles, isGroup);

        // Create a user ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(userNameWithDomain, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateUserTenantRoles(ldapViPRUser5UserInfo, roles);

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Try to create a user group by non security admin user (ldapViPRUser5).
        ClientResponse clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorMessage = "Insufficient permissions for user %s";
        partialErrorMessage = String.format(partialErrorMessage, userNameWithDomain.toLowerCase());
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Tenant Admin and Project owner has a readonly access.
        clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).get(ClientResponse.class);
        Assert.assertEquals(200, clientResponseUserGroupCreate.getStatus());

        // Test the bulk api. Here expecting false for get, as ldapViPRUser5
        // is not a sysadmin or sysmonitor and expecting true for post, as
        // ldapViPRUser5 is tenant admin.
        testUserGroupBulkApi(ldapViPRUser5, false, true);

        // Now remove the user group from the role assignments.
        roleAssignmentChanges.getAdd().clear();
        roleAssignmentChanges.getRemove().add(roleAssignmentEntry1);

        roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsRemove(roleAssignments, userNameWithDomain, isGroup);

        // Now the user should not have any roles.
        ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateNoneUserTenantRoles(ldapViPRUser5UserInfo);
    }

    @Test
    public void testUserGroupCreateByNonTenantAdmin() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupCreateByNonTenantAdmin - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Remove the group just created tenant user mapping.
        // So that, all the users in the domain can be assigned with
        // tenant roles. Here getting the ldapGroup(2) as that is the
        // one used as default one for creating the tenant.
        removeUserMappingGroups(testTenantId, getLDAPGroup(2));

        // Assigning the tenant role Project admin to ldapViPRUser5.
        List<String> roles = new ArrayList<String>();
        roles.add(getTenantRole(1));
        String userNameWithDomain = getUserWithDomain(4);

        String roleAssignmentsApi = getTenantRoleAssignmentApi(testTenantId);
        boolean isGroup = false;

        RoleAssignmentEntry roleAssignmentEntry1 = getRoleAssignmentEntry(userNameWithDomain, roles, isGroup);
        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntry1);

        RoleAssignments roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsSuccess(roleAssignments, userNameWithDomain, roles, isGroup);

        // Create a user ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(userNameWithDomain, getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateUserTenantRoles(ldapViPRUser5UserInfo, roles);

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Try to create a user group by non security admin user (ldapViPRUser5).
        ClientResponse clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorMessage = "Insufficient permissions for user %s";
        partialErrorMessage = String.format(partialErrorMessage, userNameWithDomain.toLowerCase());
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Only tenant Admin and Project owner has a readonly access.
        clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).get(ClientResponse.class);
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Test the bulk api. Here expecting false as ldapViPRUser5
        // is not a sysadmin, project owner, tenant admin.
        testUserGroupBulkApi(ldapViPRUser5, false, false);

        // Now remove the user group from the role assignments.
        roleAssignmentChanges.getAdd().clear();
        roleAssignmentChanges.getRemove().add(roleAssignmentEntry1);

        roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsRemove(roleAssignments, userNameWithDomain, isGroup);

        // Now the user should not have any roles
        ldapViPRUser5UserInfo = ldapViPRUser5.path(whoAmIApi).get(UserInfo.class);
        validateNoneUserTenantRoles(ldapViPRUser5UserInfo);
    }

    @Test
    public void testUserGroupCreateWithProjectOwner() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupCreateWithProjectOwner - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Remove the group just created tenant user mapping.
        // So that, all the users in the domain can be assigned with
        // tenant roles. Here getting the ldapGroup(2) as that is the
        // one used as default one for creating the tenant.
        removeUserMappingGroups(testTenantId, getLDAPGroup(2));

        // Create a test project for the just created tenant.
        URI projectId = createTestProject(testTenantId);

        String userNameWithDomain = getUserWithDomain(4);

        // Change the owner of the project to ldapViPRUser5 from rSys.
        String projectEditApi = getProjectApi(projectId);
        ProjectUpdateParam updateParam = new ProjectUpdateParam();
        updateParam.setOwner(userNameWithDomain);
        ClientResponse clientResponseProjectEdit = rSys.path(projectEditApi).put(ClientResponse.class, updateParam);
        Assert.assertEquals(200, clientResponseProjectEdit.getStatus());

        // Create a user ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(userNameWithDomain, getLDAPUserPassword());

        ProjectRestRep ldapViPRUser5ProjectInfo = ldapViPRUser5.path(getProjectApi(projectId)).get(ProjectRestRep.class);
        Assert.assertEquals(projectId, ldapViPRUser5ProjectInfo.getId());

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Try to create a user group by non security admin user (ldapViPRUser5).
        ClientResponse clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorMessage = "Insufficient permissions for user %s";
        partialErrorMessage = String.format(partialErrorMessage, userNameWithDomain.toLowerCase());
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Tenant Admin and Project owner has a readonly access.
        clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).get(ClientResponse.class);
        Assert.assertEquals(200, clientResponseUserGroupCreate.getStatus());

        // Test the bulk api. Here expecting false for get, as ldapViPRUser5
        // is not a sysadmin or sysmonitor and expecting true for post, as
        // ldapViPRUser5 is project owner.
        testUserGroupBulkApi(ldapViPRUser5, false, true);

        ProjectRestRep ProjectInfo = ldapViPRUser5.path(getProjectApi(projectId)).get(ProjectRestRep.class);
        Assert.assertEquals(projectId, ProjectInfo.getId());
    }

    @Test
    public void testUserGroupCreateWithProjectAclALL() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupCreateWithProjectAclALL - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Remove the group just created tenant user mapping.
        // So that, all the users in the domain can be assigned with
        // tenant roles. Here getting the ldapGroup(2) as that is the
        // one used as default one for creating the tenant.
        removeUserMappingGroups(testTenantId, getLDAPGroup(2));

        // Create a test project for the just created tenant.
        URI projectId = createTestProject(testTenantId);

        String aclAssignmentsApi = getProjectACLAssignmentApi(projectId);
        boolean isGroup = false;

        // Assigning all the project acls ALL to the user ldapViPRUser5
        List<String> acls = new ArrayList<String>();
        acls.add(getACL(0));
        String userNameWithDomain = getUserWithDomain(4);

        ACLEntry aclAssignmentEntry1 = getACLAssignmentEntry(userNameWithDomain, acls, isGroup);
        ACLAssignmentChanges aclAssignmentChanges = getDefaultACLAssignmentChanges();
        aclAssignmentChanges.getAdd().add(aclAssignmentEntry1);

        ACLAssignments aclAssignments = rSys.path(aclAssignmentsApi).put(ACLAssignments.class, aclAssignmentChanges);
        validateACLAssignmentsSuccess(aclAssignments, userNameWithDomain, acls, isGroup);

        // Create a user ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(userNameWithDomain, getLDAPUserPassword());

        ProjectRestRep ldapViPRUser5ProjectInfo = ldapViPRUser5.path(getProjectApi(projectId)).get(ProjectRestRep.class);
        Assert.assertEquals(projectId, ldapViPRUser5ProjectInfo.getId());

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Try to create a user group by non security admin user (ldapViPRUser5).
        ClientResponse clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorMessage = "Insufficient permissions for user %s";
        partialErrorMessage = String.format(partialErrorMessage, userNameWithDomain.toLowerCase());
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Only Tenant Admin and Project owner has a readonly access.
        clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).get(ClientResponse.class);
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Test the bulk api. Here expecting false for get, as ldapViPRUser5
        // is not a sysadmin or sysmonitor and expecting true for post, as
        // ldapViPRUser5 has all project acl.
        testUserGroupBulkApi(ldapViPRUser5, false, false);

        // Now remove the user group from the acl assignments.
        aclAssignmentChanges.getAdd().clear();
        aclAssignmentChanges.getRemove().add(aclAssignmentEntry1);

        aclAssignments = rSys.path(aclAssignmentsApi).put(ACLAssignments.class, aclAssignmentChanges);
        validateACLAssignmentsRemove(aclAssignments, userNameWithDomain, isGroup);

        // Now the user should not have any acls.
        ClientResponse clientResponseProjectInfo = ldapViPRUser5.path(getProjectApi(projectId)).get(ClientResponse.class);
        Assert.assertEquals(403, clientResponseProjectInfo.getStatus());
    }

    @Test
    public void testUserGroupCreateWithProjectAclBACKUP() throws NoSuchAlgorithmException {
        final String testName = "testUserGroupCreateWithProjectAclBACKUP - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Remove the group just created tenant user mapping.
        // So that, all the users in the domain can be assigned with
        // tenant roles. Here getting the ldapGroup(2) as that is the
        // one used as default one for creating the tenant.
        removeUserMappingGroups(testTenantId, getLDAPGroup(2));

        // Create a test project for the just created tenant.
        URI projectId = createTestProject(testTenantId);

        String aclAssignmentsApi = getProjectACLAssignmentApi(projectId);
        boolean isGroup = false;

        // Assigning all the project acls BACKUP ldapViPRUser5.
        List<String> acls = new ArrayList<String>();
        acls.add(getACL(1));
        String userNameWithDomain = getUserWithDomain(4);

        ACLEntry aclAssignmentEntry1 = getACLAssignmentEntry(userNameWithDomain, acls, isGroup);
        ACLAssignmentChanges aclAssignmentChanges = getDefaultACLAssignmentChanges();
        aclAssignmentChanges.getAdd().add(aclAssignmentEntry1);

        ACLAssignments aclAssignments = rSys.path(aclAssignmentsApi).put(ACLAssignments.class, aclAssignmentChanges);
        validateACLAssignmentsSuccess(aclAssignments, userNameWithDomain, acls, isGroup);

        // Create a user ldapViPRUser5.
        BalancedWebResource ldapViPRUser5 = getHttpsClient(userNameWithDomain, getLDAPUserPassword());

        ProjectRestRep ldapViPRUser5ProjectInfo = ldapViPRUser5.path(getProjectApi(projectId)).get(ProjectRestRep.class);
        Assert.assertEquals(projectId, ldapViPRUser5ProjectInfo.getId());

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Try to create a user group by non security admin user (ldapViPRUser5).
        ClientResponse clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).post(ClientResponse.class, createParam);

        String partialErrorMessage = "Insufficient permissions for user %s";
        partialErrorMessage = String.format(partialErrorMessage, userNameWithDomain.toLowerCase());
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Only Tenant Admin and Project owner has a readonly access.
        clientResponseUserGroupCreate = ldapViPRUser5.path(getTestApi()).get(ClientResponse.class);
        validateUserGroupBadRequest(403, partialErrorMessage, clientResponseUserGroupCreate);

        // Test the bulk api. Here expecting false for get, as ldapViPRUser5
        // is not a sysadmin or sysmonitor and expecting false for post, as
        // ldapViPRUser5 is tenant admin, project owner, security admin.
        testUserGroupBulkApi(ldapViPRUser5, false, false);

        // Now remove the user group from the acl assignments.
        aclAssignmentChanges.getAdd().clear();
        aclAssignmentChanges.getRemove().add(aclAssignmentEntry1);

        aclAssignments = rSys.path(aclAssignmentsApi).put(ACLAssignments.class, aclAssignmentChanges);
        validateACLAssignmentsRemove(aclAssignments, userNameWithDomain, true);

        // Now the user should not have any acls.
        ClientResponse clientResponseProjectInfo = ldapViPRUser5.path(getProjectApi(projectId)).get(ClientResponse.class);
        Assert.assertEquals(403, clientResponseProjectInfo.getStatus());
    }

    @Test
    public void testSingleValueUserGroupWithTenantRoleAssignment() throws NoSuchAlgorithmException {
        final String testName = "testSingleValueUserGroupWithTenantRoleAssignment - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        // Set name to Depart_QE.
        createParam.setLabel("Depart_QE");

        // Remove all the attributes.
        createParam.getAttributes().clear();

        // Just set only one attribute and its only one value.
        UserAttributeParam userAttributeParam = new UserAttributeParam();
        userAttributeParam.setKey(getAttributeKey(0));
        userAttributeParam.getValues().add(getAttributeDepartmentValue(1));

        createParam.getAttributes().add(userAttributeParam);

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupCreateResp = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        // Create a test tenant.
        URI testTenantId = createTestTenant();

        // Update the tenant user mapping with the
        // just created user group "Depart_QE".
        updateTenantGroups(testTenantId, userGroupCreateResp.getName());

        String roleAssignmentsApi = getTenantRoleAssignmentApi(testTenantId);
        boolean isGroup = true;

        // Assigning all the Tenant roles to Depart_QE user group(with attributes department = [QE]
        RoleAssignmentEntry roleAssignmentEntry1 = getRoleAssignmentEntry(userGroupCreateResp.getName(),
                getDefaultTenantRoles(), isGroup);
        RoleAssignmentChanges roleAssignmentChanges = getDefaultVDCRoleAssignmentChanges();
        roleAssignmentChanges.getAdd().add(roleAssignmentEntry1);

        RoleAssignments roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsSuccess(roleAssignments, userGroupCreateResp.getName(), getDefaultTenantRoles(), isGroup);

        // Create a user whose attributes matches with the above created
        // user group "Depart_QE". Matching LDAP user is ldapViPRUser5.
        BalancedWebResource ldapViPRUser7 = getHttpsClient(getUserWithDomain(6), getLDAPUserPassword());

        String whoAmIApi = getUserWhoAmIApi();
        UserInfo ldapViPRUser7UserInfo = ldapViPRUser7.path(whoAmIApi).get(UserInfo.class);
        validateUserTenantRoles(ldapViPRUser7UserInfo, getDefaultTenantRoles());

        // Now try to delete the user group "Depart_QE".
        // It should fail, as it is associated with the tenant role assignments and
        // tenants user mapping group.
        deleteUserGroupAndExpectFailure(userGroupCreateResp.getId());

        // Now try to change the domain the of the user group "Depart_Dev".
        // It should fail, as it is associated with the tenant role assignments and
        // tenants user mapping group.
        changeUserGroupDomainAndExpectFailure(userGroupCreateResp);

        // Edit the user group but dont change any properties in the group.
        // This should be successful irrespective of whether it is used in
        // any role or acl or user mapping assignments.
        editUserGroupWithoutAnyChangeAndExpectSuccess(userGroupCreateResp);

        // Now remove the user group from the role assignments.
        roleAssignmentChanges.getAdd().clear();
        roleAssignmentChanges.getRemove().add(roleAssignmentEntry1);

        roleAssignments = rSys.path(roleAssignmentsApi).put(RoleAssignments.class, roleAssignmentChanges);
        validateVDCRoleAssignmentsRemove(roleAssignments, userGroupCreateResp.getName(), isGroup);

        // Now the user should not have any roles associated with the
        // user group "Depart_QE".
        ldapViPRUser7UserInfo = ldapViPRUser7.path(whoAmIApi).get(UserInfo.class);
        validateNoneUserTenantRoles(ldapViPRUser7UserInfo);

        // Now remove the user group from the tenant user mappings.
        removeTenantUserMapping(testTenantId, userGroupCreateResp.getName());
    }

    @Test
    public void testUserGroupBulkAPI() {
        final String testName = "testUserGroupBulkAPI - ";
        createDefaultAuthnProvider(testName + "Default Authn Provider creation");

        // Test the bulk api.
        testUserGroupBulkApi(rSys, true, true);
    }

    private void testUserGroupBulkApi(BalancedWebResource user, boolean expectGetSuccess, boolean expectPostSuccess) {
        String testBulkApi = getTestBulkApi();

        UserGroupCreateParam createParam = getDefaultUserGroupCreateParam();

        ClientResponse clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        UserGroupRestRep userGroupRestRep = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        List<URI> bulkIds = new ArrayList<URI>();
        bulkIds.add(userGroupRestRep.getId());

        createParam = getDefaultUserGroupCreateParam();

        // Change the name something different and keep the same
        // attributes key but with different values for each key.
        // This should be successful.
        createParam.setLabel("NewName");
        Iterator<UserAttributeParam> it = createParam.getAttributes().iterator();
        while (it.hasNext()) {
            UserAttributeParam userAttributeParam = it.next();
            if (userAttributeParam != null) {
                userAttributeParam.getValues().clear();
                userAttributeParam.getValues().add("NewValue");
            }
        }

        clientUserGroupCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);
        userGroupRestRep = validateUserGroupCreateSuccess(createParam, clientUserGroupCreateResp);

        bulkIds.add(userGroupRestRep.getId());

        // Get all the ids of UserGroup configured in the system.
        ClientResponse clientUserGroupBulkResp = user.path(testBulkApi).get(ClientResponse.class);
        if (!expectGetSuccess) {
            Assert.assertEquals(403, clientUserGroupBulkResp.getStatus());
            return;
        }

        BulkIdParam bulkIdParam = null;
        if (expectGetSuccess) {
            bulkIdParam = validateUserGroupBulkGetSuccess(clientUserGroupBulkResp, bulkIds.size());
        } else {
            bulkIdParam = new BulkIdParam();
            bulkIdParam.setIds(bulkIds);
        }

        int expectedPostReqCount = bulkIdParam.getIds().size();
        if (!expectPostSuccess) {
            expectedPostReqCount = 0;
        }

        // Get the details of all the UserGroups configured in the system.
        // By passing the same set of ids received in the response of get request.
        clientUserGroupBulkResp = user.path(testBulkApi).post(ClientResponse.class, bulkIdParam);
        validateUserGroupBulkPostSuccess(clientUserGroupBulkResp, expectedPostReqCount);
    }
}