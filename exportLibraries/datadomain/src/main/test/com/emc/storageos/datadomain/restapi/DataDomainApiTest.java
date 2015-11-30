/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.datadomain.restapi;

import java.net.URI;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.common.http.RestAPIFactory;
import com.emc.storageos.datadomain.restapi.model.DDExportInfo;
import com.emc.storageos.datadomain.restapi.model.DDExportInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDExportList;
import com.emc.storageos.datadomain.restapi.model.DDMCInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDMTreeInfo;
import com.emc.storageos.datadomain.restapi.model.DDMTreeInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDMTreeList;
import com.emc.storageos.datadomain.restapi.model.DDNetworkDetails;
import com.emc.storageos.datadomain.restapi.model.DDNetworkInfo;
import com.emc.storageos.datadomain.restapi.model.DDNetworkList;
import com.emc.storageos.datadomain.restapi.model.DDServiceStatus;
import com.emc.storageos.datadomain.restapi.model.DDShareInfo;
import com.emc.storageos.datadomain.restapi.model.DDShareInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDSystem;
import com.emc.storageos.datadomain.restapi.model.DDSystemInfo;
import com.emc.storageos.datadomain.restapi.model.DDSystemList;
import com.emc.storageos.services.util.EnvConfig;

/*
 * Test client for DataDomainRESTClient
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="classpath:controller-conf.xml")
public class DataDomainApiTest {
    private static final Logger _log = LoggerFactory.getLogger(DataDomainApiTest.class);
    
    private static final String ddURI = EnvConfig.get("sanity", "datadomain.uri");
    
    private static final String user = EnvConfig.get("sanity", "datadomain.username");
    
    private static final String password = EnvConfig.get("sanity", "datadomain.password");
    
    private DataDomainClient _client;
    
    @Autowired
    private RestAPIFactory<DataDomainClient> datadomainfactory;

    @Before
    public synchronized void setup() throws Exception {
        _client = (DataDomainClient) datadomainfactory.getRESTClient(URI.create(ddURI), user, password);
    }

    @Test
    public void testDataDomainManagementSystem() throws Exception {
        _log.info("Test Data Domain Management System.");
        DDMCInfoDetail response = _client.getManagementSystemInfo();
        _log.info("Management System : '" + response.toString() + "'");
        DDSystemList systemlist = _client.getManagedSystemList();
        DDSystem testDD = null;
        for (DDSystemInfo systemInfo : systemlist.getSystemInfo()) {
            DDSystem system = _client.getDDSystem(systemInfo.getId());
            if (testDD == null) {
                testDD = system;
            }
            _log.info("DDSystem : \"" + system.toString() + "\"");
        }
        Assert.assertNotNull(testDD);
        DDMTreeList mtreeList = _client.getMTreeList(testDD.id);
        for (DDMTreeInfo mtreeInfo : mtreeList.mtree) {
            DDMTreeInfoDetail mtree = _client.getMTree(testDD.id, mtreeInfo.getId());
            _log.info("MTree : \"" + mtree.toString() + "\"");
        }
    }

    @Test
    public void testDataDomainCreateMtree() throws Exception {
        _log.info("Test Data Domain Create Mtree.");
        DDSystemList systemlist = _client.getManagedSystemList();
        DDSystem testDD = null;
        // DD system to test
        for (DDSystemInfo systemInfo : systemlist.getSystemInfo()) {
            testDD = _client.getDDSystem(systemInfo.getId());
            break;
        }
        Assert.assertNotNull(testDD);

        String mTreeName = "testCrMTree100018";
        DDMTreeInfoDetail mtree = null;
        try {
            DDMTreeList mtreeList = _client.getMTreeList(testDD.id);
            for (DDMTreeInfo mtreeInfo : mtreeList.mtree) {
                if (mtreeInfo.getName().contains(mTreeName)) {
                    mtree = _client.getMTree(testDD.id, mtreeInfo.getId());
                    break;
                }
            }
        } catch (Exception notFound) {
            mtree = null;
        }

        if (mtree != null && mtree.delStatus != 1) {
            // ClientResponse resp = _client.deleteMTree(testDD.id,mtree.id);
            DDServiceStatus ddSvcStatus = _client.deleteMTree(testDD.id, mtree.id);
            // Assert.assertEquals(resp.getClientResponseStatus().getStatusCode(), 200);
            Assert.assertEquals(ddSvcStatus.getCode(), 200);
        }

        DDMTreeInfo mtreeInfo = _client.createMTree(testDD.id, "/data/col1/" + mTreeName,
                4000000, true, "compliance");
        Assert.assertTrue(mtreeInfo.getName().contains(mTreeName));
        mtree = _client.getMTree(testDD.id, mtreeInfo.getId());
        Assert.assertTrue(mtreeInfo.getId().equals(mtree.id));
        Assert.assertTrue(mtree.name.contains(mTreeName));
        _log.info("MTree : \"" + mtree.toString() + "\"");
        _client.deleteMTree(testDD.id, mtree.id);
    }

    @Test
    public void testDataDomainCreateShare() throws Exception {
        _log.info("Test Data Domain Create Export.");
        DDSystemList systemlist = _client.getManagedSystemList();
        DDSystem testDD = null;
        // DD system to test
        for (DDSystemInfo systemInfo : systemlist.getSystemInfo()) {
            testDD = _client.getDDSystem(systemInfo.getId());
            break;
        }
        Assert.assertNotNull(testDD);

        // Create mtree then create export
        String mTreeName = "testCrShareMTree100018";
        DDMTreeInfoDetail mtree = null;
        try {
            DDMTreeList mtreeList = _client.getMTreeList(testDD.id);
            for (DDMTreeInfo mtreeInfo : mtreeList.mtree) {
                if (mtreeInfo.getName().contains(mTreeName)) {
                    mtree = _client.getMTree(testDD.id, mtreeInfo.getId());
                    break;
                }
            }
        } catch (Exception notFound) {
            mtree = null;
        }

        // if(mtree != null && mtree.delStatus != 1 ){
        // DDServiceStatus ddSvcStatus = _client.deleteMTree(testDD.id,mtree.id);
        // Assert.assertEquals(ddSvcStatus.getCode(), 200);
        // }
        DDMTreeInfo mtreeInfo;
        if (mtree == null || mtree.delStatus == 1) {
            mtreeInfo = _client.createMTree(testDD.id, "/data/col1/" + mTreeName,
                    4000000, true, "compliance");
            Assert.assertTrue(mtreeInfo.getName().contains(mTreeName));
            mtree = _client.getMTree(testDD.id, mtreeInfo.getId());
            Assert.assertTrue(mtreeInfo.getId().equals(mtree.id));
            Assert.assertTrue(mtree.name.contains(mTreeName));
        }

        // Now create share
        String shareName = "testCrShare100018";
        String sharePath = mtree.name;
        String desc = "Share with existing path";
        String permissionType = DataDomainApiConstants.BROWSE_ALLOW;
        String permission = DataDomainApiConstants.SHARE_CHANGE;
        DDShareInfo ddShareInfo = _client.createShare(testDD.id,
                shareName, sharePath, -1, desc, permissionType, permission);
        String newShareId = ddShareInfo.getId();
        DDShareInfoDetail ddShareInfoDetail = _client.getShare(
                testDD.id, newShareId);
        Assert.assertTrue(ddShareInfoDetail.getPathStatus() == 1);

        // Create share without existing path
        shareName = "testCrShare101018";
        sharePath = mtree.name + "_1";
        desc = "Share without existing path";
        ddShareInfo = _client.createShare(testDD.id, shareName, sharePath,
                -1, desc, permissionType, permission);
        newShareId = ddShareInfo.getId();
        ddShareInfoDetail = _client.getShare(testDD.id, newShareId);
        Assert.assertTrue(ddShareInfoDetail.getPathStatus() == 0);

        _log.info("MTree : \"" + mtree.toString() + "\"");
        _client.deleteShare(testDD.id, newShareId);
        _client.deleteMTree(testDD.id, mtree.id);
    }

    @Test
    public void testDiscoverNetwork() throws Exception {
        _log.info("Test Discover Networks (Ports) for DataDomain System.");
        DDMCInfoDetail response = _client.getManagementSystemInfo();
        _log.info("Management System : '" + response.toString() + "'");
        DDSystemList systemlist = _client.getManagedSystemList();
        DDSystem testDD = null;
        for (DDSystemInfo systemInfo : systemlist.getSystemInfo()) {
            testDD = _client.getDDSystem(systemInfo.getId());
            break;
        }
        Assert.assertNotNull(testDD);
        DDNetworkList networks = _client.getNetworks(testDD.id);
        for (DDNetworkInfo network : networks.network) {
            DDNetworkDetails network_details = _client.getNetwork(testDD.id, network.getId());
            _log.info("Network : \"" + network_details.toString() + "\"");
        }
    }

    @Test
    public void testDiscoverExport() throws Exception {
        _log.info("Test Discover Networks (Ports) for DataDomain System.");
        DDMCInfoDetail response = _client.getManagementSystemInfo();
        _log.info("Management System : '" + response.toString() + "'");
        DDSystemList systemlist = _client.getManagedSystemList();
        DDSystem testDD = null;
        for (DDSystemInfo systemInfo : systemlist.getSystemInfo()) {
            testDD = _client.getDDSystem(systemInfo.getId());
            break;
        }
        Assert.assertNotNull(testDD);
        DDExportList exports = _client.getExports(testDD.id);
        for (DDExportInfo exportInfo : exports.getExports()) {
            DDExportInfoDetail export_details = _client.getExport(testDD.id, exportInfo.getId());
            _log.info("Network : \"" + export_details.toString() + "\"");
        }
    }

    @Test
    public void testDataDomainExpandMtree() throws Exception {
        _log.info("Test Data Domain Create Mtree.");
        DDSystemList systemlist = _client.getManagedSystemList();
        DDSystem testDD = null;
        // DD system to test
        for (DDSystemInfo systemInfo : systemlist.getSystemInfo()) {
            DDSystem system = _client.getDDSystem(systemInfo.getId());
            if (testDD == null) {
                testDD = system;
                break;
            }
        }
        Assert.assertNotNull(testDD);

        DDMTreeInfoDetail mtree = null;
        try {
            DDMTreeList mtreeList = _client.getMTreeList(testDD.id);
            mtree = _client.getMTree(testDD.id, mtreeList.mtree.get(1).getId());
        } catch (Exception notFound) {
            mtree = null;
        }

        Assert.assertNotNull(mtree != null);

        long newSize = 5 + mtree.quotaConfig.getHardLimit();
        // newSize = newSize << 1;
        DDMTreeInfo mtreeInfo = _client.expandMTree(testDD.id, mtree.id, newSize);

        mtree = _client.getMTree(testDD.id, mtreeInfo.getId());

        Assert.assertEquals(newSize, mtree.logicalCapacity.getTotal());

    }

    public RestAPIFactory<DataDomainClient> getDatadomainfactory() {
        return datadomainfactory;
    }

    public void setDatadomainfactory(RestAPIFactory<DataDomainClient> datadomainfactory) {
        this.datadomainfactory = datadomainfactory;
    }

}
