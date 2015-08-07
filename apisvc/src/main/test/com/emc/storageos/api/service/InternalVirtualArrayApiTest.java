/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;

import com.emc.storageos.api.service.impl.resource.utils.InternalVirtualArrayServiceClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.model.varray.VirtualArrayCreateParam;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Tests internal tenant api
 * Note: requires some manual configuration for ad and license
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:coordinatorclient-var.xml" })
public class InternalVirtualArrayApiTest extends ApiTestBase {
    private String _server = "localhost";
    private String _apiServer = "https://" + _server + ":8443";
    private URI _rootTenantId;
    private String _rootToken;
    private InternalVirtualArrayServiceClient _internalVarrayClient;

    @Autowired
    private CoordinatorClientImpl _coordinatorClient;

    @XmlRootElement(name = "results")
    public static class Resources {
        public List<SearchResultResourceRep> resource;
    }

    @Before
    public void setup() throws Exception {

        _internalVarrayClient = new InternalVirtualArrayServiceClient();
        _internalVarrayClient.setCoordinatorClient(_coordinatorClient);
        _internalVarrayClient.setServer(_server);

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
    public void testInternalVirtualArray() throws Exception {

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

        // 2. create neighborhoods for test
        VirtualArrayCreateParam neighborhoodParam = new VirtualArrayCreateParam();
        neighborhoodParam.setLabel("nb1" + String.valueOf(timestamp));
        VirtualArrayRestRep n1 = rSys.path("/vdc/varrays").post(VirtualArrayRestRep.class, neighborhoodParam);
        Assert.assertNotNull(n1.getId());

        // 3. set protection type
        String sProtectionType = "protectionType1";
        VirtualArrayRestRep resp = _internalVarrayClient.setProtectionType(n1.getId(), sProtectionType);
        Assert.assertTrue(resp != null);
        Assert.assertTrue(resp.getId().equals(n1.getId()));
        Assert.assertTrue(resp.getObjectSettings().getProtectionType().equals(sProtectionType));

        // 4. get protection type
        String rProtectionType = _internalVarrayClient.getProtectionType(n1.getId());
        Assert.assertTrue(resp != null);
        Assert.assertTrue(rProtectionType.equals(sProtectionType));

        // 4.a get protectoin type from an not existed varray
        try {
            URI tmpvarryId = URI.create(String.format("urn:storageos:VirtualArray:%1$s:%2$s", UUID.randomUUID().toString(), "vdc1"));
            rProtectionType = _internalVarrayClient.getProtectionType(tmpvarryId);
        } catch (APIException e) {
            Assert.assertEquals(ServiceCode.API_URL_ENTITY_NOT_FOUND, e.getServiceCode());
        }

        // 5. unset protection type
        ClientResponse unsetResp = _internalVarrayClient.unsetProtectionType(n1.getId());
        Assert.assertTrue(unsetResp != null);
        Assert.assertTrue(unsetResp.getStatus() == 200);

        // 6. get protection type after unset
        rProtectionType = _internalVarrayClient.getProtectionType(n1.getId());
        Assert.assertTrue(resp != null);
        Assert.assertTrue(rProtectionType.isEmpty());

        // 7. set registered status to true
        Boolean bDeviceRegistered = true;
        VirtualArrayRestRep resp2 = _internalVarrayClient.setDeviceRegistered(n1.getId(), bDeviceRegistered);
        Assert.assertTrue(resp2 != null);
        Assert.assertTrue(resp2.getId().equals(n1.getId()));
        Assert.assertTrue(resp2.getObjectSettings().getDeviceRegistered().equals(bDeviceRegistered));

        // 8. get registered status
        Boolean rDeviceRegistered = _internalVarrayClient.getDeviceRegistered(n1.getId());
        Assert.assertTrue(resp != null);
        Assert.assertTrue(bDeviceRegistered == rDeviceRegistered);

        // 9. try to delete nh1
        ClientResponse deleteResp = rSys.path("/vdc/varrays/" + n1.getId().toString() + "/deactivate").post(ClientResponse.class);
        Assert.assertTrue(deleteResp != null);
        Assert.assertTrue(deleteResp.getStatus() == 400);

        // 9. set registered status to false
        bDeviceRegistered = false;
        VirtualArrayRestRep resp3 = _internalVarrayClient.setDeviceRegistered(n1.getId(), bDeviceRegistered);
        Assert.assertTrue(resp3 != null);
        Assert.assertTrue(resp3.getId().equals(n1.getId()));
        Assert.assertTrue(resp3.getObjectSettings().getDeviceRegistered().equals(bDeviceRegistered));

        // 10. delete nh1
        deleteResp = rSys.path("/vdc/varrays/" + n1.getId().toString() + "/deactivate").post(ClientResponse.class);
        Assert.assertTrue(deleteResp != null);
        Assert.assertTrue(deleteResp.getStatus() == 200);
    }

    @After
    public void teardown() throws Exception {

    }
}
