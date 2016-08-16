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
package com.emc.storageos.api.service.impl.resource.utils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.keystone.restapi.model.response.KeystoneTenant;
import com.emc.storageos.keystone.restapi.utils.KeystoneUtils;
import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.security.authentication.InternalTenantServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.URI;

@RunWith(MockitoJUnitRunner.class)
public class OpenStackSynchronizationTaskTest {

    private static final String TENANT_NAME = "Test";
    private static final String TENANT_ORG_NAME = "OpenStack Test";
    private static final String TENANT_DESCRIPTION = "Test description for Tenant";
    private static final String TENANT_OS_ID = "731c295027a549beb97a51e74d45fdbf";
    private static final String TENANT_ENABLED_STRING = "true";
    private static final String TENANT_USER_MAPPING_STRING = "\"attributes\":[{\"values\":" +
            "[\"731c295027a549beb97a51e74d45fdbf\"],\"key\":\"tenant_id\"}],\"domain\":\"test.com\",\"groups\":[]";
    private static final String PROVIDER_DOMAIN = "test.com";
    private static final String INTERVAL = "60";
    private static final boolean TENANT_ENABLED = true;
    private static final boolean TENANT_EXCLUDED = false;

    private OpenStackSynchronizationTask _synchronizationTask;
    private OSTenant osTenant;
    private KeystoneTenant keystoneTenant;
    private TenantOrg tenantOrg;
    private AuthnProvider keystoneProvider;

    @Mock
    private DbClient _dbClient;

    @Mock
    private KeystoneUtils _keystoneUtils;

    @Mock
    private InternalTenantServiceClient _internalTenantsService;

    @Before
    public void setupTest() {

        _synchronizationTask = spy(new OpenStackSynchronizationTask());
        _synchronizationTask.setDbClient(_dbClient);
        _synchronizationTask.setKeystoneUtilsService(_keystoneUtils);
        _synchronizationTask.setInternalTenantServiceClient(_internalTenantsService);

        prepareTenants();
        prepareKeystoneProvider();
    }

    @Test
    public void testCreateTenant() {

        URI tenantId = URIUtil.createId(TenantOrg.class);
        TenantOrgRestRep tenant = new TenantOrgRestRep();
        tenant.setId(tenantId);

        when(_keystoneUtils.prepareTenantParam(any())).thenReturn(new TenantCreateParam());
        when(_internalTenantsService.createTenant(any())).thenReturn(tenant);

        URI result = _synchronizationTask.createTenant(keystoneTenant);
        verify(_internalTenantsService).createTenant(any());
        assertNotNull(result);
        assertEquals(tenantId, result);
    }

    @Test
    public void testCreateProject() {

        URI projectId = URIUtil.createId(Project.class);
        ProjectElement projectParam = new ProjectElement();
        projectParam.setId(projectId);

        when(_internalTenantsService.createProject(any(), any())).thenReturn(projectParam);

        URI result = _synchronizationTask.createProject(tenantOrg.getId(), keystoneTenant);
        verify(_internalTenantsService).createProject(any(), any());
        assertNotNull(result);
        assertEquals(projectId, result);
    }

    @Test
    public void testGetTaskInterval() {

        doReturn(INTERVAL).when(_synchronizationTask).getIntervalFromTenantSyncSet(any());
        int returnedInterval = _synchronizationTask.getTaskInterval(keystoneProvider);
        assertNotNull(returnedInterval);
        assertEquals(Integer.parseInt(INTERVAL), returnedInterval);

    }

    @Test(expected = InternalServerErrorException.class)
    public void testGetTaskIntervalWhenNullParameter() {

        doReturn(INTERVAL).when(_synchronizationTask).getIntervalFromTenantSyncSet(any());

        try {
            _synchronizationTask.getTaskInterval(null);
        } catch (InternalServerErrorException e) {
            assertEquals(ServiceCode.SYS_IS_NULL_OR_EMPTY, e.getServiceCode());
            throw e;
        }
    }

    @Test(expected = InternalServerErrorException.class)
    public void testGetTaskIntervalWhenMissingInterval() {

        doReturn(INTERVAL).when(_synchronizationTask).getIntervalFromTenantSyncSet(any());
        doReturn(null).when(_synchronizationTask).getIntervalFromTenantSyncSet(any());

        try {
            _synchronizationTask.getTaskInterval(keystoneProvider);
        } catch (InternalServerErrorException e) {
            assertEquals(ServiceCode.SYS_IS_NULL_OR_EMPTY, e.getServiceCode());
            throw e;
        }
    }

    @Test
    public void testGetIntervalFromTenantSyncSet() {

        StringSet synchronizationOptions = new StringSet();
        String returnedInterval = _synchronizationTask.getIntervalFromTenantSyncSet(synchronizationOptions);
        assertNull(returnedInterval);

        synchronizationOptions.add(INTERVAL);
        checkInterval(synchronizationOptions);

        synchronizationOptions.add(AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString());
        checkInterval(synchronizationOptions);

        synchronizationOptions.add(AuthnProvider.TenantsSynchronizationOptions.DELETION.toString());
        checkInterval(synchronizationOptions);
    }

    public void checkInterval(StringSet synchronizationOptions) {

        String interval = _synchronizationTask.getIntervalFromTenantSyncSet(synchronizationOptions);
        assertNotNull(interval);
        assertEquals(INTERVAL, interval);
    }

    @Test
    public void testStartSynchronizationTask() {

        try {
            doNothing().when(_synchronizationTask).start(anyInt());

            _synchronizationTask.startSynchronizationTask(5);
            verify(_synchronizationTask, never()).start(5);

            _synchronizationTask.startSynchronizationTask(15);
            verify(_synchronizationTask).start(15);
        } catch (Exception e) {
            fail();
        }

    }

    @Test
    public void testStopSynchronizationTask() {

        doNothing().when(_synchronizationTask).stop();
        _synchronizationTask.stopSynchronizationTask();

        verify(_synchronizationTask).stop();
    }

    private void prepareTenants() {

        tenantOrg = new TenantOrg();
        tenantOrg.setId(URIUtil.createId(TenantOrg.class));
        tenantOrg.setParentTenant(new NamedURI(URIUtil.createId(TenantOrg.class), ""));
        tenantOrg.addUserMapping(PROVIDER_DOMAIN, TENANT_USER_MAPPING_STRING);
        tenantOrg.setDescription(TENANT_DESCRIPTION);
        tenantOrg.setLabel(TENANT_ORG_NAME);

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

    private void prepareKeystoneProvider() {

        keystoneProvider = new AuthnProvider();
        keystoneProvider.setDomains(new StringSet());
        keystoneProvider.getDomains().add(PROVIDER_DOMAIN);
    }
}
