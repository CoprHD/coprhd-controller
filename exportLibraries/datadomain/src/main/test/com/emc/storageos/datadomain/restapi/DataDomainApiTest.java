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

package com.emc.storageos.datadomain.restapi;

import com.emc.storageos.datadomain.restapi.model.*;
import com.emc.storageos.services.util.EnvConfig;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/*
 * Test client for DataDomainRESTClient
 */
public class DataDomainApiTest {
    private static final Logger _log = LoggerFactory.getLogger(DataDomainApiTest.class);
    private static DataDomainClient _client;
    private static DataDomainClientFactory _factory = new DataDomainClientFactory();

    private static final int DEFAULT_MAX_CONN = 300;
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;
    private static final int DEFAULT_SOCKET_CONN_TIMEOUT = 1000 * 60 * 60;

    private static final String ddURI = EnvConfig.get("sanity", "datadomain.uri");
    private static final String user = EnvConfig.get("sanity", "datadomain.username");
    private static final String password = EnvConfig.get("sanity", "datadomain.password");

    @BeforeClass
    public static synchronized void setup() throws Exception {
        _factory = new DataDomainClientFactory();
        _factory.setConnectionTimeoutMs(DEFAULT_CONN_TIMEOUT);
        _factory.setMaxConnections(DEFAULT_MAX_CONN);
        _factory.setMaxConnectionsPerHost(DEFAULT_MAX_CONN_PER_HOST);
        _factory.setSocketConnectionTimeoutMs(DEFAULT_SOCKET_CONN_TIMEOUT);
        _factory.setNeedCertificateManager(true);
        // _factory.setNeedCertificateManager(false);
        _factory.init();
        _client = (DataDomainClient) _factory.getRESTClient(URI.create(ddURI), user, password);
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

}
