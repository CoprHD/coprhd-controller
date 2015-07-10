/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.model.varray.VirtualArrayCreateParam;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolCreateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolList;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolUpdateParam;
import com.sun.jersey.api.client.ClientResponse;

//public class ComputeVirtualPoolTest {
//
//	@Test
//	public void test() {
//		fail("Not yet implemented");
//	}
//}

/**
 * Tests Compute Virtual Pool API Note: requires some manual configuration for
 * ad and license
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:api-var.xml" })
// @ContextConfiguration(locations = {"classpath:coordinatorclient-var.xml"})
public class ComputeVirtualPoolTest extends ApiTestBase {
    private String _server = "localhost";
    private String _apiServer = "https://" + _server + ":8443";

    // private String _rootToken;
    // private URI _rootTenantId;

    @Before
    public void setup() throws Exception {

        System.out.println("-Running setup");

        List<String> urls = new ArrayList<String>();
        urls.add(_apiServer);
        rSys = createHttpsClient(SYSADMIN, SYSADMIN_PASSWORD, urls);

        TenantResponse tenantResp = rSys.path("/tenant").get(TenantResponse.class);
        // _rootTenantId = tenantResp.getTenant();
        // _rootToken = (String)_savedTokens.get(SYSADMIN);

        updateADConfig();
        updateRootTenantAttrs();

    }

    // @Test
    // public void testComputeVirtualPoolGet() throws Exception {
    //
    // // long timestamp = System.currentTimeMillis();
    //
    // System.out.println("-Running testComputeVirtualPoolGet");
    //
    // ComputeVirtualPoolList cvpResp = rSys.path("/compute/vpools")
    // .get(ComputeVirtualPoolList.class);
    // List<NamedRelatedResourceRep> cvpList = cvpResp.getComputeVirtualPool();
    // Assert.assertTrue(cvpList.size() > 0);
    //
    // }

    @Test
    public void testComputeVirtualPoolCrud() throws Exception {

        long timestamp = System.currentTimeMillis();

        System.out.println("-Running testComputeVirtualPool");

        // Create vArray (neighborhood) for test
        VirtualArrayCreateParam neighborhoodParam = new VirtualArrayCreateParam();
        neighborhoodParam.setLabel("nb1-temp" + String.valueOf(timestamp));
        // VirtualArrayRestRep n1 =
        // rSys.path("/vdc/varrays").header(RequestProcessingUtils.AUTH_TOKEN_HEADER,
        // _rootToken).post(VirtualArrayRestRep.class, neighborhoodParam);
        VirtualArrayRestRep n1 = rSys.path("/vdc/varrays").post(VirtualArrayRestRep.class, neighborhoodParam);
        Assert.assertNotNull(n1.getId());
        System.out.println("-Newly created vArray id - " + n1.getId().toString());

        // Create Compute Virtual Pool
        ComputeVirtualPoolCreateParam createParams = new ComputeVirtualPoolCreateParam();
        createParams.setDescription("VCP created by Unit Test");
        createParams.setName("VCP-Unit-Test-1");
        createParams.setSystemType("Cisco_UCSM");
        createParams.setMinCpuSpeed(2500);
        createParams.setMaxCpuSpeed(8000);
        createParams.setMinTotalCores(1);
        Integer newMaxCores = 8;
        createParams.setMaxTotalCores(newMaxCores);
        Set<String> vArrays = new HashSet<String>();
        vArrays.add(n1.getId().toString());
        createParams.setVarrays(vArrays);
        ComputeVirtualPoolRestRep cvpCreateResp = rSys.path("/compute/vpools").post(ComputeVirtualPoolRestRep.class,
                createParams);
        URI newId = cvpCreateResp.getId();
        Assert.assertNotNull(newId);
        System.out.println("-Newly created Compute Virtual Pool id - " + newId.toString());
        System.out.println("---max cores - " + cvpCreateResp.getMaxTotalCores());
        Assert.assertTrue(cvpCreateResp.getMaxTotalCores() == newMaxCores);

        // Get list of virtual pools
        ComputeVirtualPoolList cvpResp = rSys.path("/compute/vpools").get(ComputeVirtualPoolList.class);
        List<NamedRelatedResourceRep> cvpList = cvpResp.getComputeVirtualPool();
        Assert.assertTrue(!cvpList.isEmpty());

        // Get details of newly created Compute Virtual Pool
        ComputeVirtualPoolRestRep cvpGetResp = rSys.path("/compute/vpools/" + newId.toString()).get(
                ComputeVirtualPoolRestRep.class);
        Assert.assertNotNull(cvpGetResp.getId());

        System.out.println("id - " + cvpGetResp.getId().toString());
        System.out.println("name - " + cvpGetResp.getName());
        System.out.println("description - " + cvpGetResp.getDescription());
        List<RelatedResourceRep> vArrayResp = cvpGetResp.getVirtualArrays();
        Assert.assertTrue(!vArrayResp.isEmpty());

        // Get Matching Compute Elements
        ClientResponse ceResp = rSys.path("/compute/vpools/" + newId.toString() + "/matched-compute-elements").get(
                ClientResponse.class);
        // List<ComputeElementRestRep> ceList = ceResp.getList();
        Assert.assertTrue(ceResp.getStatus() == 200);

        // Update CVP
        System.out.println("- Updating - " + newId.toString());
        ComputeVirtualPoolUpdateParam updParams = new ComputeVirtualPoolUpdateParam();
        Integer updMaxCores = 4;
        updParams.setMaxTotalCores(updMaxCores);
        ComputeVirtualPoolRestRep updateResp = rSys.path("/compute/vpools/" + newId.toString()).put(
                ComputeVirtualPoolRestRep.class, updParams);
        System.out.println("---max cores - " + updateResp.getMaxTotalCores());
        Assert.assertTrue(updateResp.getMaxTotalCores() == updMaxCores);

        // Delete CVP
        ClientResponse delCvpResp = rSys.path("/compute/vpools/" + newId.toString() + "/deactivate").post(
                ClientResponse.class);
        Assert.assertTrue(delCvpResp != null);
        Assert.assertTrue(delCvpResp.getStatus() == 200);

        // Delete vArray
        ClientResponse deleteResp = rSys.path("/vdc/varrays/" + n1.getId().toString() + "/deactivate").post(
                ClientResponse.class);
        Assert.assertTrue(deleteResp != null);
        Assert.assertTrue(deleteResp.getStatus() == 200);

    }

    // @Test
    // public void testComputeVirtualPoolAcl() throws Exception {
    // // Tests of tenants and access control
    //
    // }

    @After
    public void teardown() throws Exception {
        System.out.println("-Running teardown");

        ClientResponse deleteResp = rSys.path("/vdc/admin/authnproviders/" + _goodADConfig.toString()).delete(
                ClientResponse.class);
        Assert.assertTrue(deleteResp != null);
        Assert.assertTrue(deleteResp.getStatus() == 200);

    }

}
