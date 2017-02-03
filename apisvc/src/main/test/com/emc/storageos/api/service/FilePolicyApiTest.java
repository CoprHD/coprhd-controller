/*
 * Copyright (c) 2011-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.httpclient.HttpStatus;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.api.ldap.exceptions.DirectoryOrFileNotFoundException;
import com.emc.storageos.api.ldap.exceptions.FileOperationFailedException;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.file.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyCreateParam;
import com.emc.storageos.model.file.policy.FilePolicyCreateResp;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyScheduleParams;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyParam;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.sun.jersey.api.client.ClientResponse;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFException;

/**
 * 
 * FilePolicyTest class to exercise the core api functionality of File Policy Service.
 */
public class FilePolicyApiTest extends ApiTestBase {

    private static final String FILE_POLICIES = "/file/file-policies";
    private static final String FILE_POLICY = "/file/file-policies/%s";

    private static final String DEFAULT_TEST_TENANT_LABEL = "TestTenant";
    @SuppressWarnings("unused")
    private static final String DEFAULT_TEST_TENANT_DESCRIPTION = "Tenant Provided by LDAP Authenticate Provider";

    private static URI createdFileSnapshotPolicyURI = null;

    private static String filePolicyName = "test file snapshot policy " + UUID.randomUUID().toString();

    private List<CleanupResource> _cleanupResourceList = null;
    private ApiTestAuthnProviderUtils apiTestAuthnProviderUtils = new ApiTestAuthnProviderUtils();

    private static FilePolicyApiTest apiTestAuthnTenants = new FilePolicyApiTest();

    private String getFilePolicyURI(URI id) {
        return String.format(FILE_POLICY, id.toString());
    }

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

    private void registerResourceForCleanup(CleanupResource resource) {
        if (_cleanupResourceList == null) {
            _cleanupResourceList = new LinkedList<CleanupResource>();
        }

        _cleanupResourceList.add(resource);
    }

    private String getTestDomainName() {
        return apiTestAuthnProviderUtils.getAuthnProviderDomain();
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

    public ProjectParam getDefaultProjectParam(String projectName) {
        ProjectParam param = new ProjectParam();
        param.setName(projectName);

        return param;
    }

    @Test
    public void testFileSnapshotPolicyCreate() {

        FilePolicyCreateParam param = new FilePolicyCreateParam();
        param.setPolicyName(filePolicyName);
        param.setPolicyDescription(filePolicyName);
        param.setPolicyType(FilePolicyType.file_snapshot.name());
        param.setApplyAt(FilePolicy.FilePolicyApplyLevel.vpool.toString());
        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        policySchedule.setScheduleFrequency("DAYS");
        policySchedule.setScheduleRepeat(6L);
        policySchedule.setScheduleTime("12:00");
        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();
        snapshotPolicyParam.setSnapshotNamePattern("snapshot policy 1");
        snapshotPolicyParam.setPolicySchedule(policySchedule);
        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();

        snapshotExpireParams.setExpireType(SnapshotExpireType.NEVER.toString());
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);

        Assert.assertEquals(HttpStatus.SC_OK, createFilePolicyResp.getStatus());

        FilePolicyCreateResp createdFilePolicy = createFilePolicyResp.getEntity(FilePolicyCreateResp.class);

        Assert.assertTrue(filePolicyName.equals(createdFilePolicy.getName()));

        createdFileSnapshotPolicyURI = createdFilePolicy.getId();

        System.out.println("New snapshot policy ID: " + createdFileSnapshotPolicyURI);

    }

    @Test
    public void testCreatePolicyWithoutName() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreatePolicyWithInValidApplyAt() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        param.setPolicyName(filePolicyName);
        param.setPolicyDescription(filePolicyName);
        param.setPolicyType(FilePolicyType.file_snapshot.name());
        param.setApplyAt("Some_invalid_string");
        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreatePolicyWithInValidPolicyType() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        param.setPolicyName(filePolicyName);
        param.setPolicyDescription(filePolicyName);
        param.setApplyAt(FilePolicy.FilePolicyApplyLevel.vpool.toString());
        param.setPolicyType("Some_invalid_string");
        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreatePolicyWithInValidPolicyScheduleFrequency() {

        FilePolicyCreateParam param = new FilePolicyCreateParam();
        setFilePolicyMandatoryParams(param);

        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        policySchedule.setScheduleRepeat(6L);
        policySchedule.setScheduleTime("12:00");
        policySchedule.setScheduleFrequency("some_invalid_value");
        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();
        snapshotPolicyParam.setPolicySchedule(policySchedule);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();
        snapshotExpireParams.setExpireType(SnapshotExpireType.NEVER.toString());
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreatePolicyWithInValidPolicyScheduleRepeat() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        setFilePolicyMandatoryParams(param);

        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        // Value less than 1
        policySchedule.setScheduleRepeat(0L);
        policySchedule.setScheduleTime("12:00");
        policySchedule.setScheduleFrequency("DAYS");
        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();
        snapshotPolicyParam.setPolicySchedule(policySchedule);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();
        snapshotExpireParams.setExpireType(SnapshotExpireType.NEVER.toString());
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreatePolicyWithInValidPolicyScheduleTime() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        setFilePolicyMandatoryParams(param);

        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();
        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();
        snapshotExpireParams.setExpireType(SnapshotExpireType.NEVER.toString());
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);

        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        policySchedule.setScheduleRepeat(6L);
        // Invalid TIME
        policySchedule.setScheduleTime("67:00");
        policySchedule.setScheduleFrequency("DAYS");
        snapshotPolicyParam.setPolicySchedule(policySchedule);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(createFilePolicyResp.toString(), HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreatePolicyWithInValidPolicyScheduleDayOfWeek() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        setFilePolicyMandatoryParams(param);

        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();
        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();
        snapshotExpireParams.setExpireType(SnapshotExpireType.NEVER.toString());
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);

        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        policySchedule.setScheduleRepeat(6L);
        policySchedule.setScheduleTime("13:00");
        policySchedule.setScheduleFrequency("WEEKS");
        // Invalid day of month
        policySchedule.setScheduleDayOfWeek("invalid day");
        snapshotPolicyParam.setPolicySchedule(policySchedule);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(createFilePolicyResp.toString(), HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreatePolicyWithInValidPolicyScheduleDayOfMonth() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        setFilePolicyMandatoryParams(param);

        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();
        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();
        snapshotExpireParams.setExpireType(SnapshotExpireType.NEVER.toString());
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);

        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        policySchedule.setScheduleRepeat(6L);
        policySchedule.setScheduleTime("13:00");
        policySchedule.setScheduleFrequency("MONTHS");
        // Invalid day of month(not in range of 1-31)
        policySchedule.setScheduleDayOfWeek("34");
        snapshotPolicyParam.setPolicySchedule(policySchedule);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(createFilePolicyResp.toString(), HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreateSnapshotPolicyInvalidExpireType() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        setFilePolicyMandatoryParams(param);
        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();

        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        setFilePolicyScheduleParams(policySchedule);
        snapshotPolicyParam.setPolicySchedule(policySchedule);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();
        // Invalid Expire Type
        snapshotExpireParams.setExpireType("some_junk_value");
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(createFilePolicyResp.toString(), HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreateSnapshotPolicyInvalidExpireValue() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        setFilePolicyMandatoryParams(param);
        FileSnapshotPolicyParam snapshotPolicyParam = new FileSnapshotPolicyParam();

        FilePolicyScheduleParams policySchedule = new FilePolicyScheduleParams();
        setFilePolicyScheduleParams(policySchedule);
        snapshotPolicyParam.setPolicySchedule(policySchedule);
        param.setSnapshotPolicyPrams(snapshotPolicyParam);

        FileSnapshotPolicyExpireParam snapshotExpireParams = new FileSnapshotPolicyExpireParam();
        snapshotExpireParams.setExpireType("HOURS");
        // Some invalid value less than 2 hours or greater than 2 years
        snapshotExpireParams.setExpireValue(1);
        snapshotPolicyParam.setSnapshotExpireParams(snapshotExpireParams);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(createFilePolicyResp.toString(), HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreateSnapshotPolicyWithoutParams() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        setFilePolicyMandatoryParams(param);

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(createFilePolicyResp.toString(), HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testCreateReplicationPolicyWithoutParams() {
        FilePolicyCreateParam param = new FilePolicyCreateParam();
        param.setPolicyName(filePolicyName);
        param.setPolicyDescription(filePolicyName);
        param.setPolicyType(FilePolicyType.file_replication.name());
        param.setApplyAt(FilePolicy.FilePolicyApplyLevel.vpool.toString());

        ClientResponse createFilePolicyResp = rSys.path(FILE_POLICIES).post(ClientResponse.class,
                param);
        Assert.assertEquals(createFilePolicyResp.toString(), HttpStatus.SC_BAD_REQUEST, createFilePolicyResp.getStatus());
    }

    @Test
    public void testGetFileSnapshotPolicy() {
        FilePolicyRestRep fileSnapshotPolicyResp = rSys.path(getFilePolicyURI(createdFileSnapshotPolicyURI)).get(FilePolicyRestRep.class);
        Assert.assertNotNull(fileSnapshotPolicyResp);
        Assert.assertTrue(createdFileSnapshotPolicyURI.equals(fileSnapshotPolicyResp.getId()));
    }

    @Test
    public void testListFileSnapshotPolicies() {
        FilePolicyListRestRep filePolicyListResp = rSys.path(FILE_POLICIES).get(FilePolicyListRestRep.class);
        Assert.assertNotNull(filePolicyListResp);
        List<NamedRelatedResourceRep> filePolicies = filePolicyListResp.getFilePolicies();
        for (Iterator<NamedRelatedResourceRep> iterator = filePolicies.iterator(); iterator.hasNext();) {
            NamedRelatedResourceRep namedRelatedResourceRep = iterator.next();
            if (namedRelatedResourceRep.getId().equals(createdFileSnapshotPolicyURI)) {
                Assert.assertTrue(true);
                return;

            }
        }
        Assert.assertTrue(false);
    }

    public void setFilePolicyMandatoryParams(FilePolicyCreateParam param) {
        param.setPolicyName(filePolicyName);
        param.setPolicyDescription(filePolicyName);
        param.setPolicyType(FilePolicyType.file_snapshot.name());
        param.setApplyAt(FilePolicy.FilePolicyApplyLevel.vpool.toString());

    }

    public void setFilePolicyScheduleParams(FilePolicyScheduleParams policySchedule) {
        policySchedule.setScheduleRepeat(6L);
        policySchedule.setScheduleTime("13:00");
        policySchedule.setScheduleFrequency("DAYS");
    }

    @After
    public void tearDown() {
        CleanupResource.cleanUpTestResources(_cleanupResourceList);
    }
}
