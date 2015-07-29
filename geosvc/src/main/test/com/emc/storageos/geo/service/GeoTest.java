/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.geo.service;

import java.net.URI;
import java.util.*;

import com.emc.storageos.security.geo.GeoServiceExceptionFilter;
import com.emc.storageos.security.helpers.ServiceClientRetryFilter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.ContextConfiguration;

import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.model.tenant.UserMappingAttributeParam;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.api.service.ApiTestBase;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.varray.*;

/**
 * 
 * ApiTest class to exercise the core geo functionality
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:coordinatorclient-var.xml" })
public class GeoTest extends ApiTestBase {
    private String server = System.getenv("APP_HOST_NAMES");
    // private String coordinatorURI = "coordinator://localhost:2181";
    private GeoServiceClient geoClient;

    @Autowired
    private CoordinatorClientImpl coordinatorClient;
    private URI id1;
    private URI id2;
    private URI virtualPoolId;
    private List<URI> ids;
    private int virtualArrayCount;

    private URI projectId;

    @Before
    public void setup() throws Exception {
        setupHttpsResources();

        geoClient = new GeoServiceClient();
        geoClient.setServer(server);
        geoClient.setCoordinatorClient(coordinatorClient);
        geoClient.addFilter(new ServiceClientRetryFilter(geoClient.getClientMaxRetries(), geoClient.getClientRetryInterval()));
        geoClient.addFilter(new GeoServiceExceptionFilter());

        // prepare test data
        createVirtualArrays();
        createVirtualPools();
    }

    private void createVirtualArrays() {
        ids = new ArrayList<URI>();
        virtualArrayCount = 10;

        System.out.println("create virtualArrays");

        for (int i = 0; i < virtualArrayCount; i++) {
            VirtualArrayCreateParam neighborhoodParam = new VirtualArrayCreateParam();
            neighborhoodParam.setLabel("foo" + i);
            VirtualArrayRestRep n = rSys.path("/vdc/varrays").post(VirtualArrayRestRep.class, neighborhoodParam);
            URI id = n.getId();
            Assert.assertNotNull(id);
            ids.add(id);

            if (id1 == null) {
                id1 = id;
            } else if (id2 == null) {
                id2 = id;
            }
        }
    }

    private void createVirtualPools() {
        System.out.println("create a virtualPool");
        BlockVirtualPoolParam paramCosBlock = new BlockVirtualPoolParam();

        paramCosBlock.setName("foobar-block");
        paramCosBlock.setProtocols(new HashSet<String>());
        paramCosBlock.getProtocols().add(StorageProtocol.Block.FC.name());
        paramCosBlock.setMaxPaths(2);
        paramCosBlock.setProvisionType("Thick");
        BlockVirtualPoolRestRep cos = rSys.path("/block/vpools").post(BlockVirtualPoolRestRep.class, paramCosBlock);

        virtualPoolId = cos.getId();
        Assert.assertNotNull(virtualPoolId);

        System.out.println("The VirtualPool ID:" + virtualPoolId);
    }

    private void createSubTenant() {
        TenantResponse tenantResp = rSys.path("/tenant").get(TenantResponse.class);
        rootTenantId = tenantResp.getTenant();

        /*
         * CREATE subtenant
         */
        String subtenant_url = "/tenants/" + rootTenantId.toString() + "/subtenants";
        TenantCreateParam tenantParam = new TenantCreateParam();
        String subtenant1_label = "subtenant1";
        tenantParam.setLabel(subtenant1_label);
        tenantParam.setDescription("first subtenant");
        tenantParam.setUserMappings(new ArrayList<UserMappingParam>());

        UserMappingParam tenantMapping = new UserMappingParam();

        tenantMapping = new UserMappingParam();
        tenantMapping.setDomain("sanity.LOCAL");

        // Add an attribute scope to the mapping
        UserMappingAttributeParam tenantAttr = new UserMappingAttributeParam();
        Date now = new Date();
        tenantAttr.setKey("departMent" + now);
        tenantAttr.setValues(Collections.singletonList(SUBTENANT1_ATTR));
        tenantMapping.setAttributes(Collections.singletonList(tenantAttr));

        tenantParam.getUserMappings().add(tenantMapping);

        // Add the mappings
        TenantOrgRestRep subtenant1 = rSys.path(subtenant_url).post(TenantOrgRestRep.class, tenantParam);

        Assert.assertTrue(subtenant1.getName().equals(subtenant1_label));
        Assert.assertEquals(1, subtenant1.getUserMappings().size());

        subtenant1Id = subtenant1.getId();
    }

    private void createProject() {
        ProjectParam paramProj = new ProjectParam("aclstestproject1");
        ProjectEntry project = rSys.path(String.format(_projectsUrlFormat, subtenant1Id.toString()))
                .post(ProjectEntry.class, paramProj);

        Assert.assertTrue(project.name.equals(paramProj.getName()));
        Assert.assertTrue(project.id != null);
        projectId = project.id;
    }

    @After
    public void shutdown() throws Exception {
        if (geoClient != null) {
            geoClient.shutdown();
        }

        // delete created virtual arrays
        System.out.println("delete virtual arrays");

        for (URI id : ids) {
            rSys.path("/vdc/varrays/" + id + "/deactivate").post();
        }

        System.out.println("delete virutalPool: " + virtualPoolId);

        if (virtualPoolId != null) {
            rSys.path("/block/vpools/" + virtualPoolId + "/deactivate").post();
        }

        System.out.println("delete project: " + projectId);
        if (projectId != null) {
            rSys.path("/projects/" + projectId + "/deactivate").post();
        }

        System.out.println("delete tenant: " + subtenant1Id);
        if (subtenant1Id != null) {
            rSys.path("/tenants/" + subtenant1Id + "/deactivate").post();
        }

        if (_goodADConfig != null) {
            rSys.path("/vdc/admin/authnproviders/" + _goodADConfig).delete();
        }
    }

    @Test
    public void testAll() throws Exception {
        testCheckDependency();
        testQueryByType();
        testQueryByTypeInPaginate();
        testQueryObject();
        testQueryObjects();
        testQueryObjectsField();
        testQueryByConstraint();
        testQueryByConstraintWithPageinate();
        testSyncVdcConfig();
        testRemoveVdcConfig();
        testCleanDbFlag();
        testVersion();
    }

    private void testQueryByType() throws Exception {
        Iterator<URI> it = geoClient.queryByType(VirtualArray.class, true);
        Assert.assertNotNull(it);
        int count = 0;
        while (it.hasNext()) {
            count++;
            it.next();
        }
        Assert.assertEquals(virtualArrayCount, count);

    }

    private void testCheckDependency() throws Exception {
        createSubTenant();

        createProject();

        String dependency = geoClient.checkDependencies(TenantOrg.class, subtenant1Id, true);

        Assert.assertEquals(Project.class.getSimpleName(), dependency);
    }

    private void testQueryByTypeInPaginate() throws Exception {
        URI startId = null;
        int count = 0;
        List<URI> ids;

        while (true) {
            ids = geoClient.queryByType(VirtualArray.class, true, startId, 3);

            if (ids.isEmpty())
            {
                break; // reach the end
            }
            count += ids.size();

            startId = ids.get(ids.size() - 1);
        }

        Assert.assertEquals(virtualArrayCount, count);
    }

    private void testQueryObject() throws Exception {
        VirtualArray obj = geoClient.queryObject(VirtualArray.class, id1);
        Assert.assertNotNull("Failed to find the VirtualArray " + id1, obj);
        Assert.assertEquals("The VirtualArray ID should be " + id1, obj.getId(), id1);

        obj = geoClient.queryObject(VirtualArray.class, id2);
        Assert.assertNotNull("Failed to find the VirtalArray " + id2, obj);
        Assert.assertEquals("The VirtualArray ID should be " + id2, obj.getId(), id2);
    }

    private void testQueryObjects() throws Exception {

        List<URI> ids = new ArrayList<URI>(2);
        ids.add(id1);
        ids.add(id2);
        Iterator<VirtualArray> objs = geoClient.queryObjects(VirtualArray.class, ids);

        Assert.assertNotNull("Failed to find VirtualArray objects with IDs " + ids, objs);
        while (objs.hasNext()) {
            VirtualArray obj = objs.next();
            Assert.assertTrue("The VirtualArray object has unexpected ID " + obj.getId(), ids.contains(obj.getId()));
        }
    }

    private void testQueryObjectsField() throws Exception {

        List<URI> ids = new ArrayList<URI>(2);
        ids.add(id1);
        ids.add(id2);

        Iterator<VirtualArray> objs = geoClient.queryObjectsField(VirtualArray.class, "label", ids);

        Assert.assertNotNull("Failed to query field 'label' of VirtualArray objects", objs);
        while (objs.hasNext()) {
            VirtualArray obj = objs.next();
            Assert.assertTrue("The VirtalArray object has unexpected ID " + obj.getId(), ids.contains(obj.getId()));
        }
    }

    private void testQueryByConstraint() throws Exception {
        Constraint constraint = PrefixConstraint.Factory.getConstraint(VirtualArray.class, "label", "fo");
        URIQueryResultList ret = new URIQueryResultList();
        geoClient.queryByConstraint(constraint, ret);

        boolean foundId1 = false;
        boolean foundId2 = false;

        Iterator<URI> it = ret.iterator();
        while (it.hasNext()) {
            URI id = it.next();
            if (id1.equals(id)) {
                foundId1 = true;
            } else if (id2.equals(id)) {
                foundId2 = true;
            }
        }

        Assert.assertTrue("Can't find " + id1 + " in the query result", foundId1);
        Assert.assertTrue("Can't find " + id2 + " in the query result", foundId2);

        URIQueryResultList ids = new URIQueryResultList();
        geoClient.queryByConstraint(AlternateIdConstraint.Factory.getVpoolTypeVpoolConstraint(VirtualPool.Type.block), ids);
        it = ids.iterator();
        boolean found = false;
        while (it.hasNext()) {
            if (it.next().equals(virtualPoolId)) {
                found = true;
                break;
            }
        }
        Assert.assertTrue("Failed to found VirtualPool: " + virtualPoolId, found);
    }

    private void testQueryByConstraintWithPageinate() throws Exception {
        Constraint constraint = PrefixConstraint.Factory.getConstraint(VirtualArray.class, "label", "fo");
        URI startId = null;
        int count = 0;

        while (true) {
            URIQueryResultList ret = new URIQueryResultList();
            geoClient.queryByConstraint(constraint, ret, startId, 3);

            Iterator<URI> it = ret.iterator();

            if (!it.hasNext())
            {
                break; // reach the end
            }

            while (it.hasNext()) {
                startId = it.next();

                VirtualArray obj = geoClient.queryObject(VirtualArray.class, startId);

                if (obj.getInactive() == false) {
                    count++;
                }
            }
        }

        Assert.assertEquals(virtualArrayCount, count);
    }

    private void testSyncVdcConfig() throws Exception {
        /*
         * Since the VirtualDataCenter is not a GeoVisibleResource,
         * This test is not valid anymore, TODO: Qin will work on this
         * VirtualDataCenterSyncListParam vdcConfigList = new VirtualDataCenterSyncListParam();
         * List<VirtualDataCenterSyncParam> list = vdcConfigList.getVirtualDataCenters();
         * VirtualDataCenterSyncParam vdcConfig = new VirtualDataCenterSyncParam();
         * vdcConfig.setId(new URI("vdc1"));
         * vdcConfig.setVersion(1L);
         * vdcConfig.setHostCount(1);
         * vdcConfig.setHostList(new HashMap<String,String>(){{put("standalone","1.1.1.1");
         * }});
         * list.add(vdcConfig);
         * 
         * vdcConfig = new VirtualDataCenterSyncParam();
         * vdcConfig.setId(new URI("vdc2"));
         * vdcConfig.setVersion(1L);
         * vdcConfig.setHostCount(1);
         * vdcConfig.setHostList(new HashMap<String,String>(){{put("standalone","1.1.1.2");
         * }});
         * list.add(vdcConfig);
         * 
         * vdcConfig = new VirtualDataCenterSyncParam();
         * vdcConfig.setId(new URI("vdc3"));
         * vdcConfig.setVersion(1L);
         * vdcConfig.setHostCount(1);
         * vdcConfig.setHostList(new HashMap<String,String>(){{put("standalone","1.1.1.3");
         * }});
         * list.add(vdcConfig);
         * 
         * geoClient.syncVdcConfig(vdcConfigList);
         * Assert.assertEquals(countRowOf(VirtualDataCenter.class), 3);
         */
    }

    private void testRemoveVdcConfig() throws Exception {
        /*
         * Since the VirtualDataCenter is not a GeoVisible resource,
         * we can't use geoClient to do the query on it
         * TODO: Qin will work on this
         * VirtualDataCenterSyncListParam vdcConfigList = new VirtualDataCenterSyncListParam();
         * List<VirtualDataCenterSyncParam> list = vdcConfigList.getVirtualDataCenters();
         * VirtualDataCenterSyncParam vdcConfig = new VirtualDataCenterSyncParam();
         * vdcConfig.setId(new URI("vdc1"));
         * vdcConfig.setVersion(2L);
         * vdcConfig.setHostCount(1);
         * vdcConfig.setHostList(new HashMap<String,String>(){{put("standalone","1.1.2.1");
         * }});
         * list.add(vdcConfig);
         * 
         * geoClient.syncVdcConfig(vdcConfigList);
         * 
         * Assert.assertEquals(countRowOf(VirtualDataCenter.class), 1);
         */
    }

    private void testCleanDbFlag() throws Exception {
        /*
         * Same as above
         * VirtualDataCenterSyncListParam vdcConfigList = new VirtualDataCenterSyncListParam();
         * List<VirtualDataCenterSyncParam> list = vdcConfigList.getVirtualDataCenters();
         * VirtualDataCenterSyncParam vdcConfig = new VirtualDataCenterSyncParam();
         * vdcConfig.setId(new URI("vdc1"));
         * vdcConfig.setVersion(3L);
         * vdcConfig.setHostCount(1);
         * vdcConfig.setHostList(new HashMap<String,String>(){{put("standalone","1.1.3.1");
         * }});
         * list.add(vdcConfig);
         * 
         * vdcConfig = new VirtualDataCenterSyncParam();
         * vdcConfig.setId(new URI("vdc2"));
         * vdcConfig.setVersion(3L);
         * vdcConfig.setHostCount(1);
         * vdcConfig.setHostList(new HashMap<String,String>(){{put("standalone","1.1.3.2");
         * }});
         * list.add(vdcConfig);
         * 
         * vdcConfigList.setAssignedVdcId("vdc2");
         * 
         * geoClient.syncVdcConfig(vdcConfigList);
         * 
         * checkCleanDbFlag(Constants.DBSVC_NAME);
         * checkCleanDbFlag(Constants.GEODBSVC_NAME);
         * 
         * VirtualDataCenter vdc = geoClient.queryObject(VirtualDataCenter.class, new URI("vdc2"));
         * Assert.assertTrue(vdc.getLocal());
         */
    }

    public void testVersion() {
        String version = geoClient.getViPRVersion();
        Assert.assertNotNull("version should not be null", version);
        Assert.assertTrue("version response '" + version +
                "' does not start with 'vipr-" + version, version.startsWith("vipr-"));
    }

    @SuppressWarnings("unused")
    private int countRowOf(Class<? extends GeoVisibleResource> clazz) throws Exception {
        Iterator<URI> it = geoClient.queryByType(clazz, true);

        int count = 0;
        while (it.hasNext()) {
            count++;
            it.next();
        }

        return count;
    }

    @SuppressWarnings("unused")
    private void checkCleanDbFlag(String svcName) throws Exception {
        String kind = coordinatorClient.getDbConfigPath(svcName);
        String id = svcName.equals(Constants.DBSVC_NAME) ? "db-standalone" : "geodb-standalone";
        Configuration config = coordinatorClient.queryConfiguration(kind, id);
        // Assert.assertEquals(config.getConfig(Constants.RESET_DB),
        // String.valueOf(true));
    }
}
