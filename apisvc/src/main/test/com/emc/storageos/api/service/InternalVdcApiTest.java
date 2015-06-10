/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.api.service.impl.resource.utils.InternalVdcServiceClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vdc.VirtualDataCenterList;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.sun.jersey.api.client.ClientResponse;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:coordinatorclient-var.xml"})
public class InternalVdcApiTest extends ApiTestBase{
	private String server = "localhost";
    private String apiServer = "https://" + server + ":8443";
    
    private InternalVdcServiceClient internalVdcClient;

    /** 
     * Pull the coordaintor impl from the spring context, already set up to talk to localhost
     */
    @Autowired
    private CoordinatorClientImpl coordinatorClient;

    @Before
    public void setup () throws Exception {
        internalVdcClient = new InternalVdcServiceClient();
        internalVdcClient.setCoordinatorClient(coordinatorClient);
        internalVdcClient.setServer(server);        

        List<String> urls = new ArrayList<String>();
        urls.add(apiServer);
        rSys = createHttpsClient(SYSADMIN, SYSADMIN_PASSWORD, urls);
    }

    @Test
    public void testGetVdc() throws Exception {
    	VirtualDataCenterList vdcResp = rSys.path("/vdc")
                .get(VirtualDataCenterList.class);
    	List<NamedRelatedResourceRep> vdcList = vdcResp.getVirtualDataCenters();
    	Assert.assertTrue(vdcList.size() > 0);
    	URI vdcId = vdcList.get(0).getId();
    	VirtualDataCenterRestRep vdcFromInternalApi = internalVdcClient.getVdc(vdcId);
    	Assert.assertTrue(vdcFromInternalApi != null);
    	Assert.assertEquals(vdcId, vdcFromInternalApi.getId());
    	Assert.assertEquals("vdc1", vdcFromInternalApi.getShortId());
    }

    @Test
    public void testListVdc() throws Exception {
        VirtualDataCenterList vdcResp = rSys.path("/vdc")
                .get(VirtualDataCenterList.class);
        List<NamedRelatedResourceRep> vdcList = vdcResp.getVirtualDataCenters();
        
        VirtualDataCenterList vdcListFromInternalApi = internalVdcClient.listVdc();
        Assert.assertTrue(vdcListFromInternalApi != null);
        Assert.assertEquals(vdcList.size(), vdcListFromInternalApi.getVirtualDataCenters().size());
        Assert.assertTrue(vdcList.size() > 0);
        URI vdcId = vdcList.get(0).getId();
        URI vdcIdFromInternalApi = vdcListFromInternalApi.getVirtualDataCenters().get(0).getId();
        Assert.assertEquals(vdcId, vdcIdFromInternalApi);
    }
    
    @Test
    public void testSetInUse() throws Exception {
    	VirtualDataCenterList vdcResp = rSys.path("/vdc")
                .get(VirtualDataCenterList.class);
    	List<NamedRelatedResourceRep> vdcList = vdcResp.getVirtualDataCenters();
    	Assert.assertTrue(vdcList.size() > 0);
    	URI vdcId = vdcList.get(0).getId();
    	ClientResponse resp = internalVdcClient.setVdcInUse(vdcId, true);
    	Assert.assertEquals(resp.getClientResponseStatus(), ClientResponse.Status.OK);
    	resp = internalVdcClient.setVdcInUse(vdcId, false);
    	Assert.assertEquals(resp.getClientResponseStatus(), ClientResponse.Status.OK);
    }
}
