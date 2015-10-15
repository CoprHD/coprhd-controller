/*
 * Copyright (c) 2011-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import com.emc.storageos.api.ldap.exceptions.DirectoryOrFileNotFoundException;
import com.emc.storageos.api.ldap.exceptions.FileOperationFailedException;
import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderBaseParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.sun.jersey.api.client.ClientResponse;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFException;
import org.apache.commons.httpclient.HttpStatus;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.LinkedHashSet;

/**
 * 
 * ApiTestAuthnProviders class to exercise the core api functionality of Authentication Providers.
 */
public class ApiTestAuthnProviders extends ApiTestBase {
    private List<CleanupResource> _cleanupResourceList = null;
    private ApiTestAuthnProviderUtils apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();;
    private final String AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR = "The authentication provider could not be added or ";
    private final String TRACE_SUCCESSFUL = "Successful";
    private final String TRACE_AUTHN_PROVIDER_SUCCESSFUL = "Successful creation of authn provider";

    private static ApiTestAuthnProviders apiTestAuthnProviders = new ApiTestAuthnProviders();

    @BeforeClass
    public static void setupTestSuite() throws LDIFException,
            LDAPException, IOException, FileOperationFailedException,
            GeneralSecurityException, DirectoryOrFileNotFoundException, InterruptedException {
        apiTestAuthnProviders.apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();
        apiTestAuthnProviders.apiTestAuthnProviderUtils.startLdapServer(ApiTestAuthnProviders.class.getSimpleName());
    }

    @AfterClass
    public static void tearDownTestSuite() {
        apiTestAuthnProviders.apiTestAuthnProviderUtils.stopLdapServer();
    }

    @Before
    public void setUp() throws Exception {
        setupHttpsResources();
        _cleanupResourceList = new LinkedList<CleanupResource>();
        apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();
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

    private Set<String> getDefaultGroupObjectClasses() {
        return apiTestAuthnProviderUtils.getDefaultGroupObjectClasses();
    }

    private Set<String> getDefaultGroupMemberAttributes() {
        return apiTestAuthnProviderUtils.getDefaultGroupMemberAttributes();
    }

    private String getGroupObjectClass(int index) {
        return apiTestAuthnProviderUtils.getGroupObjectClass(index);
    }

    private String getGroupMemberAttribute(int index) {
        return apiTestAuthnProviderUtils.getGroupMemberAttribute(index);
    }

    private String getTestApi() {
        return apiTestAuthnProviderUtils.getAuthnProviderBaseURL();
    }

    private String getTestEditApi(URI uri) {
        return apiTestAuthnProviderUtils.getAuthnProviderEditURL(uri);
    }

    private String getDefaultGroupAttribute() {
        return apiTestAuthnProviderUtils.getDefaultGroupAttribute();
    }

    private String getNonManagerBindDN() {
        return apiTestAuthnProviderUtils.getNonManagerDN();
    }

    private String getNonManagerBindDNPwd() {
        return apiTestAuthnProviderUtils.getLDAPUserPassword();
    }

    private AuthnCreateParam getDefaultAuthnCreateParam(String description) {
        return apiTestAuthnProviderUtils.getDefaultAuthnCreateParam(description);
    }

    private AuthnUpdateParam getAuthnUpdateParamFromAuthnProviderRestResp(AuthnProviderRestRep createResponse) {
        return apiTestAuthnProviderUtils.getAuthnUpdateParamFromAuthnProviderRestResp(createResponse);
    }

    private void validateAuthProviderCommon(AuthnProviderBaseParam expected,
            AuthnProviderRestRep actual) {
        Assert.assertNotNull(actual);
        Assert.assertTrue(actual.getGroupAttribute().equalsIgnoreCase(expected.getGroupAttribute()));
    }

    private void validateAuthProviderCreateSuccess(AuthnCreateParam expected, AuthnProviderRestRep actual) {
        validateAuthProviderCommon(expected, actual);

        Assert.assertArrayEquals(expected.getGroupObjectClasses().toArray(), actual.getGroupObjectClasses().toArray());
        Assert.assertArrayEquals(expected.getGroupMemberAttributes().toArray(), actual.getGroupMemberAttributes().toArray());

        // Add the created authnprovider to cleanup list, so that at the end of this test
        // the object will be destroyed.
        final String deleteObjectURL = this.getTestEditApi(actual.getId());
        CleanupResource authnProviderToCleanup = new CleanupResource("delete", deleteObjectURL, rSys, null);

        registerResourceForCleanup(authnProviderToCleanup);

        // Query the APIService about the new created resource and make
        // sure the properties are right in the DB too.
        final String getObjectURL = this.getTestEditApi(actual.getId());
        AuthnProviderRestRep createResp = rSys.path(getObjectURL).get(AuthnProviderRestRep.class);

        Assert.assertNotNull(createResp);
        Assert.assertArrayEquals(expected.getGroupObjectClasses().toArray(),
                createResp.getGroupObjectClasses().toArray());
        Assert.assertArrayEquals(expected.getGroupMemberAttributes().toArray(),
                createResp.getGroupMemberAttributes().toArray());
    }

    private void validateAuthProviderEditSuccess(AuthnUpdateParam expected, AuthnProviderRestRep actual) {
        validateAuthProviderCommon(expected, actual);

        Assert.assertArrayEquals(expected.getGroupObjectClassChanges().getAdd().toArray(),
                actual.getGroupObjectClasses().toArray());
        Assert.assertArrayEquals(expected.getGroupMemberAttributeChanges().getAdd().toArray(),
                actual.getGroupMemberAttributes().toArray());

        // Query the APIService about the new edited resource and make
        // sure the properties are right in the DB too.
        final String getObjectURL = this.getTestEditApi(actual.getId());
        AuthnProviderRestRep createResp = rSys.path(getObjectURL).get(AuthnProviderRestRep.class);

        Assert.assertNotNull(createResp);
        Assert.assertArrayEquals(expected.getGroupObjectClassChanges().getAdd().toArray(),
                createResp.getGroupObjectClasses().toArray());
        Assert.assertArrayEquals(expected.getGroupMemberAttributeChanges().getAdd().toArray(),
                createResp.getGroupMemberAttributes().toArray());
    }

    private void validateAuthProviderEditSuccessForGroupObjectClassOnly(AuthnUpdateParam expected, AuthnProviderRestRep actual) {
        validateAuthProviderCommon(expected, actual);

        Assert.assertArrayEquals(expected.getGroupObjectClassChanges().getAdd().toArray(),
                actual.getGroupObjectClasses().toArray());

        // Query the APIService about the new edited resource and make
        // sure the properties are right in the DB too.
        final String getObjectURL = this.getTestEditApi(actual.getId());
        AuthnProviderRestRep createResp = rSys.path(getObjectURL).get(AuthnProviderRestRep.class);

        Assert.assertNotNull(createResp);
        Assert.assertArrayEquals(expected.getGroupObjectClassChanges().getAdd().toArray(),
                createResp.getGroupObjectClasses().toArray());
    }

    private void validateAuthProviderEditSuccessForGroupMemberAttributeOnly(AuthnUpdateParam expected, AuthnProviderRestRep actual) {
        validateAuthProviderCommon(expected, actual);

        Assert.assertArrayEquals(expected.getGroupMemberAttributeChanges().getAdd().toArray(),
                actual.getGroupMemberAttributes().toArray());

        // Query the APIService about the new edited resource and make
        // sure the properties are right in the DB too.
        final String getObjectURL = this.getTestEditApi(actual.getId());
        AuthnProviderRestRep createResp = rSys.path(getObjectURL).get(AuthnProviderRestRep.class);

        Assert.assertNotNull(createResp);
        Assert.assertArrayEquals(expected.getGroupMemberAttributeChanges().getAdd().toArray(),
                createResp.getGroupMemberAttributes().toArray());
    }

    private void validateAuthProviderBadRequest(int expectedStatus, String expectedErrorMsg, ClientResponse actual) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expectedStatus, actual.getStatus());

        final ServiceErrorRestRep actualErrorMsg = actual.getEntity(ServiceErrorRestRep.class);
        Assert.assertTrue(actualErrorMsg.getDetailedMessage().startsWith(expectedErrorMsg));
    }

    @Test
    public void testAuthnProviderCreateWithoutLDAPGroupProperties() {
        final String testName = "testAuthnProviderCreateWithoutLDAPGroupProperties - ";
        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + "GroupAttribute (\"\")");

        // Set the groupAttribute to "", so that the create request will fail, as
        // GroupAttribute is an mandatory parameter in the API.
        createParam.setGroupAttribute("");

        ClientResponse clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam contains invalid groupAttribute,
        // the post request should fail with the below errors.
        String partialExpectedErrorMsg = "Required parameter group_attribute was missing or empty";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientCreateResp);

        // Set the groupAttribute to null (to validate if there is no null pointer exception),
        // so that the create request will fail, as groupAttribute is an mandatory parameter in the API.
        createParam.setGroupAttribute(null);
        createParam.setDescription(testName + "GroupAttribute (null)");

        clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam contains invalid groupAttribute,
        // the post request should fail with the below errors.
        partialExpectedErrorMsg = AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR
                + "modified because of the following error: Could not find group attribute";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientCreateResp);

        // Set the groupAttribute to "some" (invalid group attribute. The imported ldap schema does not have an attribute called some),
        // so that the create request will fail, as groupAttribute is an mandatory parameter in the API.
        createParam.setGroupAttribute("some");
        createParam.setDescription(testName + "GroupAttribute (some)");

        clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam contains invalid groupAttribute,
        // the post request should fail with the below errors.
        partialExpectedErrorMsg = AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR
                + "modified because of the following error: Could not find group attribute";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientCreateResp);

        // Remove the LDAP Group search properties (Group ObjectClasses and MemberAttributes).
        createParam.setGroupObjectClasses(new HashSet<String>());
        createParam.setGroupMemberAttributes(new HashSet<String>());

        // Set the groupAttribute to valid groupAttribute to the post to be success.
        createParam.setGroupAttribute(getDefaultGroupAttribute());
        createParam.setDescription(testName + TRACE_SUCCESSFUL);

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);
    }

    @Test
    public void testAuthnProviderCreateWithNullLDAPGroupProperties() {
        final String testName = "testAuthnProviderCreateWithNullLDAPGroupProperties - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_SUCCESSFUL
                + "(null group objectClasses and memberAttributes)");

        // Remove the LDAP Group search properties (Group ObjectClasses and MemberAttributes).
        createParam.getGroupObjectClasses().clear();
        createParam.setGroupObjectClasses(null);

        createParam.getGroupMemberAttributes().clear();
        createParam.setGroupMemberAttributes(null);

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);
    }

    @Test
    public void testAuthnProviderCreateWithLDAPGroupProperties() {
        final String testName = "testAuthnProviderCreateWithLDAPGroupProperties - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + "Invalid group objectClasses and memberAttributes");

        // Add some invalid group objectclasses and memberAttributes.
        createParam.getGroupObjectClasses().add("some0");
        createParam.getGroupObjectClasses().add("some0");
        createParam.getGroupObjectClasses().add("some2");
        createParam.getGroupObjectClasses().add("some3");

        createParam.getGroupMemberAttributes().add("someAttribute0");
        createParam.getGroupMemberAttributes().add("someAttribute0");
        createParam.getGroupMemberAttributes().add("someAttribute2");
        createParam.getGroupMemberAttributes().add("someAttribute3");
        createParam.getGroupMemberAttributes().add("someAttribute4");
        createParam.getGroupMemberAttributes().add("someAttribute5");

        ClientResponse clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam contains invalid group ObjectClasses and memberAttributes
        // the post request should fail with the below errors. Here the failure will be only for the
        // objectClasses. So validate the error message against only the objectClasses error.
        String partialExpectedErrorMsg = AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR
                + "modified because of the following error: Could not find objectClasses";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientCreateResp);

        // Remove the invalid values from group objectClasses and set with default values.
        createParam.getGroupObjectClasses().clear();
        createParam.setGroupObjectClasses(getDefaultGroupObjectClasses());

        createParam.setDescription(testName + "Invalid memberAttributes");

        clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // After we removal of invalid objectClasses from createParam, it contains only
        // invalid group member attributes. So, the post request should fail with the below errors.
        // Here the failure will be only for the member attributes.
        // So validate the error message against only the member attributes error.
        partialExpectedErrorMsg = "The authentication provider could not be added or modified because of the following error: Could not find attributes";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientCreateResp);

        // Remove the invalid values from group member attributes and set with default values.
        createParam.getGroupMemberAttributes().clear();
        createParam.setGroupMemberAttributes(getDefaultGroupMemberAttributes());

        createParam.setDescription(testName + TRACE_SUCCESSFUL);

        // Now, all the paramaters in the post payload is valid. So the request should be successful.
        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);
    }

    @Test
    public void testAuthnProviderCreateDuplicateLDAPGroupProperties() {
        final String testName = "testAuthnProviderCreateDuplicateLDAPGroupProperties - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_SUCCESSFUL
                + "(Duplicate group objectClasses and memberAttributes)");

        // Add the same group objectClasses and memberAttributes to the createParam as duplicates.
        createParam.getGroupObjectClasses().addAll(getDefaultGroupObjectClasses());
        createParam.getGroupMemberAttributes().addAll(getDefaultGroupMemberAttributes());

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);

        // Validate the counts separately to make sure that the counts are removed.
        final int expected = 4;
        Assert.assertEquals(expected, createResp.getGroupObjectClasses().size());
        Assert.assertEquals(expected, createResp.getGroupMemberAttributes().size());
    }

    @Test
    public void testAuthnProviderCreateWithLDAPGroupObjectClassesOnly() {
        final String testName = "testAuthnProviderCreateWithLDAPGroupObjectClassesOnly - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_SUCCESSFUL);

        // Remove the memberAttributes from the createParam.
        createParam.getGroupMemberAttributes().clear();

        ClientResponse clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam does not contain group member attributes, the request
        // should fail with the below error.
        String partialExpectedErrorMsg = AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR
                + "modified because of the following error: Group member attributes are not provided.";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientCreateResp);
    }

    @Test
    public void testAuthnProviderCreateWithLDAPGroupMemberAttributesOnly() {
        final String testName = "testAuthnProviderCreateWithLDAPGroupMemberAttributesOnly - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_SUCCESSFUL);

        // Remove the group objectClasses from the createParam.
        createParam.getGroupObjectClasses().clear();

        ClientResponse clientCreateResp = rSys.path(getTestApi()).post(ClientResponse.class, createParam);

        // Since the createParam does not contain group objectClasses, the request
        // should fail with the below error.
        String partialExpectedErrorMsg = AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR
                + "modified because of the following error: Group object classes are not provided.";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientCreateResp);
    }

    @Test
    public void testAuthnProviderEditWithLDAPGroupProperties() {
        final String testName = "testAuthnProviderEditWithLDAPGroupProperties - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + "Creating default authn provider for edit");

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        // Succesful authn provider creation with default values.
        validateAuthProviderCreateSuccess(createParam, createResp);

        // Now edit the created authn provider.
        final String editAPI = getTestEditApi(createResp.getId());

        AuthnUpdateParam editParam = getAuthnUpdateParamFromAuthnProviderRestResp(createResp);

        // Adding some invalid group objectClasses and memberAttributes at the time of edit.
        editParam.getGroupObjectClassChanges().getAdd().add("some1");
        editParam.getGroupObjectClassChanges().getAdd().add("some1");
        editParam.getGroupObjectClassChanges().getAdd().add("some2");
        editParam.getGroupObjectClassChanges().getAdd().add("some3");

        editParam.getGroupMemberAttributeChanges().getAdd().add("someAttribute1");
        editParam.getGroupMemberAttributeChanges().getAdd().add("someAttribute1");
        editParam.getGroupMemberAttributeChanges().getAdd().add("someAttribute2");
        editParam.getGroupMemberAttributeChanges().getAdd().add("someAttribute3");
        editParam.getGroupMemberAttributeChanges().getAdd().add("someAttribute4");
        editParam.getGroupMemberAttributeChanges().getAdd().add("someAttribute5");

        editParam.setDescription(testName + "Edit with invalid group objectClasses and memberAttributes");

        ClientResponse clientEditResp = rSys.path(editAPI).put(ClientResponse.class, editParam);

        // Since the createParam contains invalid group ObjectClasses and memberAttributes
        // the post request should fail with the below errors. Here the failure will be only for the
        // objectClasses. So validate the error message against only the objectClasses error.
        String partialExpectedErrorMsg = AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR
                + "modified because of the following error: Could not find objectClasses";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientEditResp);

        // Remove the invalid values from group objectClasses and set with default values.
        editParam.getGroupObjectClassChanges().getAdd().clear();
        editParam.getGroupObjectClassChanges().getAdd().addAll(getDefaultGroupObjectClasses());

        editParam.setDescription(testName + "Edit with invalid memberAttributes");

        clientEditResp = rSys.path(editAPI).put(ClientResponse.class, editParam);

        // After we removed of invalid objectClasses from createParam, it contains only
        // invalid group memberAttributes. So, the post request should fail with the below errors.
        // Here the failure will be only for the memberAttributes.
        // So validate the error message against only the memberAttributes error.
        partialExpectedErrorMsg = "The authentication provider could not be added or modified because of the following error: Could not find attributes";
        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientEditResp);

        // Remove the invalid values from group memberAttributes and set with default values.
        editParam.getGroupMemberAttributeChanges().getAdd().clear();
        editParam.getGroupMemberAttributeChanges().getAdd().addAll(getDefaultGroupMemberAttributes());

        editParam.setDescription(testName + "Successful Edit");

        // Now, all the parameters in the post payload is valid. So the request should be successful.
        AuthnProviderRestRep editResp = rSys.path(editAPI).put(AuthnProviderRestRep.class, editParam);

        validateAuthProviderEditSuccess(editParam, editResp);
    }

    @Test
    public void testAuthnProviderEditDuplicateLDAPGroupProperties() {
        final String testName = "testAuthnProviderEditDuplicateLDAPGroupProperties - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_AUTHN_PROVIDER_SUCCESSFUL);

        // Add the same group objectClasses and memberAttributes to the createParam as duplicates.
        createParam.getGroupObjectClasses().addAll(getDefaultGroupObjectClasses());
        createParam.getGroupMemberAttributes().addAll(getDefaultGroupMemberAttributes());

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);

        // Now edit the created authn provider.
        final String editAPI = getTestEditApi(createResp.getId());

        AuthnUpdateParam editParam = getAuthnUpdateParamFromAuthnProviderRestResp(createResp);

        // Add the same group objectClasses and memberAttributes to the editParam as duplicates.
        editParam.getGroupObjectClassChanges().getAdd().addAll(getDefaultGroupObjectClasses());
        editParam.getGroupObjectClassChanges().getAdd().addAll(getDefaultGroupObjectClasses());

        editParam.setDescription(testName + "Edit with Duplicate ldap group properties");

        // Now, Send the put request to edit the auth provider with duplicate ldap group properties.
        // The request should be be successful and ldap group properties should not have any duplicates.
        AuthnProviderRestRep editResp = rSys.path(editAPI).put(AuthnProviderRestRep.class, editParam);

        validateAuthProviderEditSuccess(editParam, editResp);

        // Validate the counts separately to make sure that the counts are removed.
        final int expected = 4;
        Assert.assertEquals(expected, createResp.getGroupObjectClasses().size());
        Assert.assertEquals(expected, createResp.getGroupMemberAttributes().size());
    }

    @Test
    public void testAuthnProviderEditWithLDAPGroupObjectClassesOnly() {
        final String testName = "testAuthnProviderEditWithLDAPGroupObjectClassesOnly - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_AUTHN_PROVIDER_SUCCESSFUL);

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);

        // Now edit the created authn provider.
        final String editAPI = getTestEditApi(createResp.getId());

        AuthnUpdateParam editParam = getAuthnUpdateParamFromAuthnProviderRestResp(createResp);

        // Remove the memberAttributes from the editParam.
        editParam.getGroupMemberAttributeChanges().getAdd().clear();

        editParam.setDescription(testName + "Edit with only group objectClasses");

        // Now, Send the put request to edit the auth provider with duplicate ldap group properties.
        // The request should be be successful and ldap group properties should not have any duplicates.
        AuthnProviderRestRep editResp = rSys.path(editAPI).put(AuthnProviderRestRep.class, editParam);

        validateAuthProviderEditSuccessForGroupObjectClassOnly(editParam, editResp);

        // Validate the counts separately to make sure that the counts are removed.
        // GroupMemberAttributes wont change here as the edit did not change
        // the GroupMemberAttributes
        final int expected = 4;
        Assert.assertEquals(expected, editResp.getGroupObjectClasses().size());
        Assert.assertEquals(expected, editResp.getGroupMemberAttributes().size());
    }

    @Test
    public void testAuthnProviderEditWithLDAPGroupMemberAttributesOnly() {
        final String testName = "testAuthnProviderEditWithLDAPGroupMemberAttributesOnly - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_AUTHN_PROVIDER_SUCCESSFUL);

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);

        // Now edit the created authn provider.
        final String editAPI = getTestEditApi(createResp.getId());

        AuthnUpdateParam editParam = getAuthnUpdateParamFromAuthnProviderRestResp(createResp);

        // Remove the objectClasses from the editParam.
        editParam.getGroupObjectClassChanges().getAdd().clear();

        editParam.setDescription(testName + "Edit with only group memberAttributes");

        // Now, Send the put request to edit the auth provider with duplicate ldap group properties.
        // The reqeust should be be successful and ldap group properties should not have any duplicates.
        AuthnProviderRestRep editResp = rSys.path(editAPI).put(AuthnProviderRestRep.class, editParam);

        validateAuthProviderEditSuccessForGroupMemberAttributeOnly(editParam, editResp);

        // Validate the counts separately to make sure that the counts are removed.
        // GroupObjectClasses wont change here as the edit did not change
        // the GroupObjectClasses
        final int expected = 4;
        Assert.assertEquals(expected, editResp.getGroupObjectClasses().size());
        Assert.assertEquals(expected, editResp.getGroupMemberAttributes().size());
    }

    @Test
    public void testAuthnProviderCreateWithLDAPGroupPropertiesAndNonManagerDN() {
        final String testName = "testAuthnProviderCreateWithLDAPGroupPropertiesAndNonManagerDN - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + "Successful creation of authn provider with non managerDN");

        // overwrite the managerdn with some user information. Just to make sure that ldap schema schema search
        // does not need only the managerdn's.
        createParam.setManagerDn(getNonManagerBindDN());
        createParam.setManagerPassword(getNonManagerBindDNPwd());

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);
    }

    @Test
    public void testAuthnProviderEditWithLDAPGroupPropertiesAndNonManagerDN() {
        final String testName = "testAuthnProviderEditWithLDAPGroupPropertiesAndNonManagerDN - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_AUTHN_PROVIDER_SUCCESSFUL);

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);

        // Now edit the created authn provider.
        final String editAPI = getTestEditApi(createResp.getId());

        AuthnUpdateParam editParam = getAuthnUpdateParamFromAuthnProviderRestResp(createResp);

        // overwrite the managerdn with some user information. Just to make sure that ldap schema schema search
        // does not need only the managerdn's.
        editParam.setManagerDn(getNonManagerBindDN());
        editParam.setManagerPassword(getNonManagerBindDNPwd());

        editParam.setDescription(testName + "Edit with Non Mananger DN user");

        // Now, Send the put request to edit the auth provider with duplicate ldap group properties.
        // The reqeust should be be successful and ldap group properties should not have any duplicates.
        AuthnProviderRestRep editResp = rSys.path(editAPI).put(AuthnProviderRestRep.class, editParam);

        validateAuthProviderEditSuccess(editParam, editResp);
    }

    @Test
    public void testAuthnProviderEditByRemovingLDAPGroupProperties() {
        final String testName = "testAuthnProviderEditByRemovingLDAPGroupProperties - ";

        AuthnCreateParam createParam = getDefaultAuthnCreateParam(testName + TRACE_AUTHN_PROVIDER_SUCCESSFUL);

        AuthnProviderRestRep createResp = rSys.path(getTestApi()).post(AuthnProviderRestRep.class, createParam);

        validateAuthProviderCreateSuccess(createParam, createResp);

        // Now edit the created authn provider.
        final String editAPI = getTestEditApi(createResp.getId());

        AuthnUpdateParam editParam = getAuthnUpdateParamFromAuthnProviderRestResp(createResp);

        Set<String> addedGroupObjectClasses = new LinkedHashSet<String>();
        addedGroupObjectClasses.addAll(editParam.getGroupObjectClassChanges().getAdd());

        Set<String> addedGroupMemberAttributes = new LinkedHashSet<String>();
        addedGroupMemberAttributes.addAll(editParam.getGroupMemberAttributeChanges().getAdd());

        // Remove everything from the add list
        editParam.getGroupObjectClassChanges().getAdd().clear();
        editParam.getGroupMemberAttributeChanges().getAdd().clear();

        // Add everything to the remove list.
        editParam.getGroupObjectClassChanges().getRemove().addAll(addedGroupObjectClasses);
        editParam.getGroupMemberAttributeChanges().getRemove().addAll(addedGroupMemberAttributes);

        editParam.setDescription(testName + "Edit by removing the ldap group properties");

        // Now, Send the put request to edit the auth provider with duplicate ldap group properties.
        // The request should be be successful and ldap group properties should not have any duplicates.
        AuthnProviderRestRep editResp = rSys.path(editAPI).put(AuthnProviderRestRep.class, editParam);

        validateAuthProviderEditSuccess(editParam, editResp);

        editParam = getAuthnUpdateParamFromAuthnProviderRestResp(createResp);
        editParam.setDescription(testName + "Edit after removing the ldap group properties to reset with default values");

        // Now, Send the put request to edit the auth provider with duplicate ldap group properties.
        // The request should be be successful and ldap group properties should not have any duplicates.
        editResp = rSys.path(editAPI).put(AuthnProviderRestRep.class, editParam);

        validateAuthProviderEditSuccess(editParam, editResp);

        editParam.getGroupObjectClassChanges().getAdd().clear();
        editParam.getGroupMemberAttributeChanges().getAdd().clear();

        // Add only first two group object classes to the add list.
        editParam.getGroupObjectClassChanges().getAdd().add(this.getGroupObjectClass(0));
        editParam.getGroupObjectClassChanges().getAdd().add(this.getGroupObjectClass(1));

        // Add only last two group object classes to the remove list.
        editParam.getGroupObjectClassChanges().getRemove().add(this.getGroupObjectClass(2));
        editParam.getGroupObjectClassChanges().getRemove().add(this.getGroupObjectClass(3));

        // Add only first two group member attributes to the add list.
        editParam.getGroupMemberAttributeChanges().getAdd().add(this.getGroupMemberAttribute(0));
        editParam.getGroupMemberAttributeChanges().getAdd().add(this.getGroupMemberAttribute(1));

        // Add only last two group member attributes to the remove list.
        editParam.getGroupMemberAttributeChanges().getRemove().add(this.getGroupMemberAttribute(2));
        editParam.getGroupMemberAttributeChanges().getRemove().add(this.getGroupMemberAttribute(3));

        editParam.setDescription(testName + "Edit by removing and adding the ldap group properties in one update");

        // Now, Send the put request to edit the auth provider with duplicate ldap group properties.
        // The request should be be successful and ldap group properties should not have any duplicates.
        editResp = rSys.path(editAPI).put(AuthnProviderRestRep.class, editParam);

        validateAuthProviderEditSuccess(editParam, editResp);

        editParam.getGroupObjectClassChanges().getAdd().clear();
        editParam.getGroupMemberAttributeChanges().getAdd().clear();
        editParam.getGroupObjectClassChanges().getRemove().clear();
        editParam.getGroupMemberAttributeChanges().getRemove().clear();

        editParam.getGroupObjectClassChanges().getRemove().add(this.getGroupObjectClass(0));
        editParam.getGroupObjectClassChanges().getRemove().add(this.getGroupObjectClass(1));

        editParam.setDescription(testName + "Edit by just removing all the group object classes only.");

        // Now, Send the put request to edit the auth provider to remove all the object classes and keep
        // member attributes.
        // The request should fail as both group object classes and member attributes
        // can be empty or both can have values. Just only one containing values is
        // not allowed.
        ClientResponse clientEditResp = rSys.path(editAPI).put(ClientResponse.class, editParam);

        // Since the createParam does not contain group object classes, the request
        // should fail with the below error.
        String partialExpectedErrorMsg = AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR
                + "modified because of the following error: Group object classes are not provided.";

        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientEditResp);

        editParam.getGroupObjectClassChanges().getAdd().clear();
        editParam.getGroupMemberAttributeChanges().getAdd().clear();
        editParam.getGroupObjectClassChanges().getRemove().clear();
        editParam.getGroupMemberAttributeChanges().getRemove().clear();

        editParam.getGroupMemberAttributeChanges().getRemove().add(this.getGroupMemberAttribute(0));
        editParam.getGroupMemberAttributeChanges().getRemove().add(this.getGroupMemberAttribute(1));

        editParam.setDescription(testName + "Edit by just removing all the group member attributes only.");

        // Now, Send the put request to edit the auth provider to remove all the member attributes and keep
        // object classes.
        // The request should fail as both group object classes and member attributes
        // can be empty or both can have values. Just only one containing values is
        // not allowed.
        clientEditResp = rSys.path(editAPI).put(ClientResponse.class, editParam);

        // Since the createParam does not contain group member attributes, the request
        // should fail with the below error.
        partialExpectedErrorMsg = AUTHN_PROVIDER_ADD_UPDATE_PARTIAL_ERROR
                + "modified because of the following error: Group member attributes are not provided.";

        validateAuthProviderBadRequest(HttpStatus.SC_BAD_REQUEST, partialExpectedErrorMsg, clientEditResp);
    }
}
