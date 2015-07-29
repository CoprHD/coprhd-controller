/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service;

import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.api.service.impl.resource.utils.InternalTenantServiceClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.security.authentication.RequestProcessingUtils;
import com.sun.jersey.api.client.ClientResponse;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.bind.annotation.XmlRootElement;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Tests internal tenant api
 * Note: requires some manual configuration for ad and license
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:coordinatorclient-var.xml" })
public class InternalTenantApiTest extends ApiTestBase {
    private String _server = "localhost";
    private String _apiServer = "https://" + _server + ":8443";
    private URI _rootTenantId;
    private String _rootToken;
    private InternalTenantServiceClient _internalTenantClient;

    @Autowired
    private CoordinatorClientImpl _coordinatorClient;

    @XmlRootElement(name = "results")
    public static class Resources {
        public List<SearchResultResourceRep> resource;
    }

    @Before
    public void setup() throws Exception {

        _internalTenantClient = new InternalTenantServiceClient();
        _internalTenantClient.setCoordinatorClient(_coordinatorClient);
        _internalTenantClient.setServer(_server);

        List<String> urls = new ArrayList<String>();
        urls.add(_apiServer);
        rSys = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, urls);

        TenantResponse tenantResp = rSys.path("/tenant")
                .get(TenantResponse.class);
        _rootTenantId = tenantResp.getTenant();
        _rootToken = (String) _savedTokens.get("root");

        updateADConfig();
        updateRootTenantAttrs();

        rTAdminGr = createHttpsClient(TENANTADMIN, AD_PASS_WORD, urls);

    }

    @Test
    public void testInternalTenantNamespace() throws Exception {

        long timestamp = System.currentTimeMillis();

        // 1. CREATE subtenants
        String subtenant_url = "/tenants/" + _rootTenantId.toString() + "/subtenants";

        TenantCreateParam tenantParam = new TenantCreateParam();
        String subtenant_label = "subtenant" + String.valueOf(timestamp);
        tenantParam.setLabel(subtenant_label);
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());

        UserMappingParam tenantMapping = new UserMappingParam();
        // Add an domain to the mapping
        tenantMapping.setDomain("sanity.LOCAL");
        // Add an attribute scope to the mapping
        UserMappingAttributeParam tenantAttr = new UserMappingAttributeParam();
        tenantAttr.setKey("OU");
        tenantAttr.setValues(Collections.singletonList(subtenant_label));
        tenantMapping.setAttributes(Collections.singletonList(tenantAttr));

        tenantParam.getUserMappings().add(tenantMapping);

        TenantOrgRestRep subtenant = rTAdminGr.path(subtenant_url)
                .header(RequestProcessingUtils.AUTH_TOKEN_HEADER, _rootToken)
                .post(TenantOrgRestRep.class, tenantParam);
        Assert.assertTrue(subtenant.getName().equals(subtenant_label));
        Assert.assertEquals(1, subtenant.getUserMappings().size());

        // 2. set namespace
        String sNamespace1 = "namespace1";
        TenantOrgRestRep resp = _internalTenantClient.setTenantNamespace(subtenant.getId(), sNamespace1);
        Assert.assertTrue(resp != null);
        Assert.assertTrue(resp.getId().equals(subtenant.getId()));
        Assert.assertTrue(resp.getNamespace().equals(sNamespace1));

        // 3. get tenant
        TenantOrgRestRep getTenantResp = rTAdminGr.path("/tenants/" + subtenant.getId().toString())
                .header(RequestProcessingUtils.AUTH_TOKEN_HEADER, _rootToken)
                .get(TenantOrgRestRep.class);
        Assert.assertTrue(getTenantResp != null);
        Assert.assertTrue(resp.getId().equals(subtenant.getId()));
        Assert.assertTrue(getTenantResp.getNamespace().equals(sNamespace1));

        // 4. try to delete tenant with namespace attached
        ClientResponse delTenantResp = rTAdminGr.path("/tenants/" + subtenant.getId().toString() + "/deactivate")
                .header(RequestProcessingUtils.AUTH_TOKEN_HEADER, _rootToken)
                .post(ClientResponse.class);
        Assert.assertTrue(delTenantResp != null);
        Assert.assertTrue(delTenantResp.getStatus() == 400);

        // 5. get tenant namespace via internal API
        TenantNamespaceInfo internalGetTenantResp = _internalTenantClient.getTenantNamespace(subtenant.getId());
        Assert.assertTrue(internalGetTenantResp != null);
        Assert.assertTrue(internalGetTenantResp.getName().equals(sNamespace1));

        // 6. unset namespace
        ClientResponse delResp = _internalTenantClient.unsetTenantNamespace(subtenant.getId());
        Assert.assertTrue(delResp != null);
        Assert.assertTrue(delResp.getStatus() == 200);

        // 7. get tenant via internal API agin
        internalGetTenantResp = _internalTenantClient.getTenantNamespace(subtenant.getId());
        Assert.assertTrue(internalGetTenantResp != null);
        Assert.assertTrue(internalGetTenantResp.getName().isEmpty());

        // 8. delete tenant
        delTenantResp = rTAdminGr.path("/tenants/" + subtenant.getId().toString() + "/deactivate")
                .header(RequestProcessingUtils.AUTH_TOKEN_HEADER, _rootToken)
                .post(ClientResponse.class);
        Assert.assertTrue(delTenantResp != null);
        Assert.assertTrue(delTenantResp.getStatus() == 200);
    }

    @After
    public void teardown() throws Exception {

    }
}
