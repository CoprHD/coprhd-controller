/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package impl;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.keystone.restapi.model.response.KeystoneTenant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.keystone.restapi.utils.KeystoneUtils;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.UserMappingAttributeParam;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@RunWith(MockitoJUnitRunner.class)
public class KeystoneUtilsTest {

    private static final String TENANT_NAME = "Openstack Test";
    private static final String TENANT_DESCRIPTION = "Test description for Tenant";
    private static final String TENANT_OS_ID = "731c295027a549beb97a51e74d45fdbf";
    private static final String TENANT_WRONG_OS_ID = "1234567890";
    private static final String TENANT_ENABLED_STRING = "true";
    private static final String TENANT_USER_MAPPING_STRING = "\"attributes\":[{\"values\":" +
            "[\"731c295027a549beb97a51e74d45fdbf\"],\"key\":\"tenant_id\"}],\"domain\":\"test.com\",\"groups\":[]";
    private static final String TENANT_USER_MAPPING_NO_VALUES_STRING = "\"attributes\":[],\"domain\":\"test.com\"," +
            "\"groups\":[]";
    private static final String EMPTY_STRING = "";
    private static final String PROVIDER_DOMAIN = "test.com";
    private static final boolean TENANT_ENABLED = true;
    private static final boolean TENANT_EXCLUDED = false;

    private KeystoneUtils _keystoneUtils;
    private OSTenant osTenant;
    private KeystoneTenant keystoneTenant;
    private Project project;
    private TenantOrg tenantOrg;
    private List<UserMappingParam> userMappings;
    private AuthnProvider keystoneProvider;

    @Mock
    private DbClient _dbClient;

    @Before
    public void setupTest() {

        _keystoneUtils = new KeystoneUtils();
        _keystoneUtils.setDbClient(_dbClient);

        prepareProject();
        prepareTenants();
        prepareUserMapping();
        prepareKeystoneProvider();
    }

    @Test
    public void testGetCoprhdTenantsWithOpenStackId() {

        List<TenantOrg> tenants = new ArrayList<>();
        List<URI> tenantsUri = new ArrayList<>();
        tenants.add(createTenantOrg());
        tenants.add(tenantOrg);
        tenants.add(createTenantOrg());

        KeystoneUtils spyKeystoneUtils = spy(_keystoneUtils);
        spyKeystoneUtils.setDbClient(_dbClient);

        when(_dbClient.queryByType(TenantOrg.class, true)).thenReturn(tenantsUri);
        when(_dbClient.queryIterativeObjects(TenantOrg.class, tenantsUri)).thenReturn(tenants.iterator());
        doReturn(null).doReturn(TENANT_OS_ID).doReturn(null).when(spyKeystoneUtils).getCoprhdTenantUserMapping(any(TenantOrg.class));

        List<TenantOrg> returnedTenants = spyKeystoneUtils.getCoprhdTenantsWithOpenStackId();
        assertNotNull(returnedTenants);
        assertEquals(1, returnedTenants.size());
    }

    @Test
    public void testGetCoprhdTenantWithOpenstackId() {

        List<TenantOrg> tenants = new ArrayList<>();
        tenants.add(createTenantOrg());
        tenants.add(tenantOrg);

        KeystoneUtils spyKeystoneUtils = spy(_keystoneUtils);
        doReturn(tenants).when(spyKeystoneUtils).getCoprhdTenantsWithOpenStackId();
        doReturn(TENANT_USER_MAPPING_STRING).when(spyKeystoneUtils).getCoprhdTenantUserMapping(tenantOrg);

        TenantOrg returnedTenant = spyKeystoneUtils.getCoprhdTenantWithOpenstackId(TENANT_OS_ID);
        assertNotNull(returnedTenant);
        assertEquals(tenantOrg.getId(), returnedTenant.getId());

        TenantOrg returnedNullTenant = spyKeystoneUtils.getCoprhdTenantWithOpenstackId(EMPTY_STRING);
        assertNull(returnedNullTenant);

        TenantOrg returnedWrongNullTenant = spyKeystoneUtils.getCoprhdTenantWithOpenstackId(TENANT_WRONG_OS_ID);
        assertNull(returnedWrongNullTenant);
    }

    @Test
    public void testGetCoprhdTenantUserMapping() {

        String returnedUserMapping = _keystoneUtils.getCoprhdTenantUserMapping(tenantOrg);

        assertNotNull(returnedUserMapping);
        assertEquals(TENANT_USER_MAPPING_STRING, returnedUserMapping);

        tenantOrg.getParentTenant().setURI(URI.create(TenantOrg.NO_PARENT));
        String returnedRootUserMapping = _keystoneUtils.getCoprhdTenantUserMapping(tenantOrg);
        assertNull(returnedRootUserMapping);

        tenantOrg.setUserMappings(null);
        String returnedNullUserMapping = _keystoneUtils.getCoprhdTenantUserMapping(tenantOrg);
        assertNull(returnedNullUserMapping);

    }

    @Test
    public void testGetTenantIdFromUserMapping() {

        String expectedTenantId = "731c295027a549beb97a51e74d45fdbf";

        String result = _keystoneUtils.getTenantIdFromUserMapping(TENANT_USER_MAPPING_STRING);
        assertEquals(expectedTenantId, result);

        String emptyResult = _keystoneUtils.getTenantIdFromUserMapping(EMPTY_STRING);
        assertNotEquals(expectedTenantId, emptyResult);
        assertEquals("", emptyResult);

        String noValuesResult = _keystoneUtils.getTenantIdFromUserMapping(TENANT_USER_MAPPING_NO_VALUES_STRING);
        assertNotEquals(expectedTenantId, noValuesResult);
        assertEquals("", noValuesResult);
    }

    @Test
    public void testFindOpenstackTenantInCoprhd() {

        List<OSTenant> osTenants = new ArrayList<>();
        osTenants.add(osTenant);
        List<URI> osTenantUriList = new ArrayList<>();
        osTenantUriList.add(osTenant.getId());

        when(_dbClient.queryByType(OSTenant.class, true)).thenReturn(osTenantUriList);
        when(_dbClient.queryIterativeObjects(OSTenant.class, osTenantUriList)).thenReturn(osTenants.iterator());

        OSTenant result = _keystoneUtils.findOpenstackTenantInCoprhd(TENANT_OS_ID);
        assertNotNull(result);
        assertEquals(osTenant.getId(), result.getId());
        assertEquals(osTenant.getOsId(), result.getOsId());
        assertEquals(osTenant.getName(), result.getName());

        OSTenant nullResult = _keystoneUtils.findOpenstackTenantInCoprhd(TENANT_WRONG_OS_ID);
        assertNull(nullResult);
    }

    @Test
    public void testPrepareUserMappings() {

        KeystoneUtils spyKeystoneUtils = spy(_keystoneUtils);
        doReturn(keystoneProvider).when(spyKeystoneUtils).getKeystoneProvider();

        List<UserMappingParam> createdUserMapping = spyKeystoneUtils.prepareUserMappings(TENANT_OS_ID);

        UserMappingParam expectedParams = userMappings.iterator().next();
        UserMappingParam createdParams = createdUserMapping.iterator().next();

        assertEquals(expectedParams.getDomain(), createdParams.getDomain());
        assertEquals(expectedParams.getAttributes(), createdParams.getAttributes());
        assertEquals(expectedParams.getGroups(), createdParams.getGroups());
    }

    @Test
    public void testPrepareTenantParam() {

        String expectedTenantName = CinderConstants.TENANT_NAME_PREFIX + " " + TENANT_NAME;
        TenantCreateParam param = new TenantCreateParam(expectedTenantName, userMappings);

        KeystoneUtils spyKeystoneUtils = spy(_keystoneUtils);
        doReturn(userMappings).when(spyKeystoneUtils).prepareUserMappings(TENANT_OS_ID);
        doReturn(TENANT_DESCRIPTION).when(spyKeystoneUtils).getProperTenantDescription(TENANT_DESCRIPTION);

        TenantCreateParam createdParams = spyKeystoneUtils.prepareTenantParam(keystoneTenant);

        assertEquals(param.getUserMappings(), createdParams.getUserMappings());
        assertEquals(TENANT_DESCRIPTION, createdParams.getDescription());
        assertEquals(param.getLabel(), createdParams.getLabel());
    }

    @Test(expected = InternalServerErrorException.class)
    public void testTagProjectWithOpenstackIdNullProject() {

        URI projectId = project.getId();
        URI tenantId = tenantOrg.getId();
        when(_dbClient.queryObject(Project.class, projectId)).thenReturn(null);

        try {
            _keystoneUtils.tagProjectWithOpenstackId(projectId, TENANT_OS_ID, tenantId.toString());
        } catch (InternalServerErrorException apiException) {
            assertEquals(ServiceCode.SYS_IS_NULL_OR_EMPTY, apiException.getServiceCode());
            throw apiException;
        }
    }

    @Test
    public void testTagProjectWithOpenstackId() {

        URI projectId = project.getId();
        URI tenantId = tenantOrg.getId();
        ScopedLabel tag = new ScopedLabel(tenantId.toString(), TENANT_OS_ID);

        when(_dbClient.queryObject(Project.class, projectId)).thenReturn(project);

        Project updatedProject = _keystoneUtils.tagProjectWithOpenstackId(projectId, TENANT_OS_ID, tenantId.toString());

        assertFalse(updatedProject.getTag().isEmpty());
        assertEquals(1, updatedProject.getTag().size());

        ScopedLabel createdTag = updatedProject.getTag().iterator().next();
        assertEquals(tag.toString(), createdTag.toString());
    }

    @Test
    public void testMapToOsTenant() {

        OSTenant mappedTenant = _keystoneUtils.mapToOsTenant(keystoneTenant);
        assertEquals(osTenant.getName(), mappedTenant.getName());
        assertEquals(osTenant.getDescription(), mappedTenant.getDescription());
        assertEquals(osTenant.getOsId(), mappedTenant.getOsId());
        assertEquals(osTenant.getEnabled(), mappedTenant.getEnabled());
        assertEquals(osTenant.getExcluded(), mappedTenant.getExcluded());
    }

    @Test(expected = InternalServerErrorException.class)
    public void testMapToOsTenantNullParameter() {

        try {
            _keystoneUtils.mapToOsTenant(null);
        } catch (InternalServerErrorException apiException) {
            assertEquals(ServiceCode.SYS_IS_NULL_OR_EMPTY, apiException.getServiceCode());
            throw apiException;
        }
    }

    @Test
    public void testGetProperTenantDescription() {

        String normalDescription = _keystoneUtils.getProperTenantDescription(TENANT_DESCRIPTION);
        assertEquals(TENANT_DESCRIPTION, normalDescription);

        String nullDescription = _keystoneUtils.getProperTenantDescription(null);
        assertEquals(CinderConstants.TENANT_NAME_PREFIX, nullDescription);

        String emptyDescription = _keystoneUtils.getProperTenantDescription("");
        assertEquals(CinderConstants.TENANT_NAME_PREFIX, emptyDescription);
    }

    private void prepareTenants() {

        tenantOrg = new TenantOrg();
        tenantOrg.setId(URIUtil.createId(TenantOrg.class));
        tenantOrg.setParentTenant(new NamedURI(URIUtil.createId(TenantOrg.class), ""));
        tenantOrg.addUserMapping(PROVIDER_DOMAIN, TENANT_USER_MAPPING_STRING);

        keystoneTenant = new KeystoneTenant();
        keystoneTenant.setName(TENANT_NAME);
        keystoneTenant.setDescription(TENANT_DESCRIPTION);
        keystoneTenant.setId(TENANT_OS_ID);
        keystoneTenant.setEnabled(TENANT_ENABLED_STRING);

        osTenant = new OSTenant();
        osTenant.setName(TENANT_NAME);
        osTenant.setDescription(TENANT_DESCRIPTION);
        osTenant.setOsId(TENANT_OS_ID);
        osTenant.setEnabled(TENANT_ENABLED);
        osTenant.setExcluded(TENANT_EXCLUDED);
    }

    private void prepareProject() {

        project = new Project();
        project.setId(URIUtil.createId(Project.class));
    }

    private void prepareUserMapping() {

        userMappings = new ArrayList<>();
        List<String> values = new ArrayList<>();
        values.add(TENANT_OS_ID);

        List<UserMappingAttributeParam> attributes = new ArrayList<>();
        attributes.add(new UserMappingAttributeParam(KeystoneUtils.OPENSTACK_TENANT_ID, values));

        userMappings.add(new UserMappingParam(PROVIDER_DOMAIN, attributes, new ArrayList<>()));
    }

    private void prepareKeystoneProvider() {

        keystoneProvider = new AuthnProvider();
        keystoneProvider.setDomains(new StringSet());
        keystoneProvider.getDomains().add(PROVIDER_DOMAIN);
    }

    private TenantOrg createTenantOrg() {

        TenantOrg tenant = new TenantOrg();
        tenant.setId(URIUtil.createId(TenantOrg.class));
        tenant.setParentTenant(new NamedURI(URIUtil.createId(TenantOrg.class), ""));

        return tenant;
    }
}
