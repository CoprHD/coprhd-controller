/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RPSiteArray;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.SupportedDriveTypes;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.DbClientTest.DbClientImplUnitTester;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation;
import com.emc.storageos.volumecontroller.RPRecommendation;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.SRDFCopyRecommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
import com.emc.storageos.volumecontroller.VPlexRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({ "PlacementTests.xml" })
public class PlacementTests extends DbsvcTestBase {

    private static final Logger _log = LoggerFactory.getLogger(PlacementTests.class);
    final String dummyVpoolUri = "urn:storageos:VirtualPool:11111111-2222-3333-4444-555555555555:vdc1";
    final String haVpoolUri = "urn:storageos:VirtualPool:11111111-2222-3333-4444-666666666666:vdc1";

    // Pool Sizes
    public final long SIZE_GB = (1024 * 1024); // 1GB in KB. Use KB since all pool capacities are represented in KB.

    @Autowired
    private ApplicationContext _context;

    @Before
    public void setupTest() {
        DbClientImplUnitTester dbClient = new DbClientImplUnitTester();
        dbClient.setCoordinatorClient(_coordinator);
        dbClient.setDbVersionInfo(sourceVersion);
        dbClient.setBypassMigrationLock(true);
        _encryptionProvider.setCoordinator(_coordinator);
        dbClient.setEncryptionProvider(_encryptionProvider);

        DbClientContext localCtx = new DbClientContext();
        localCtx.setClusterName("Test");
        localCtx.setKeyspaceName("Test");
        dbClient.setLocalContext(localCtx);

        VdcUtil.setDbClient(dbClient);

        dbClient.setBypassMigrationLock(false);
        dbClient.start();

        _dbClient = dbClient;
    }

    @After
    public void teardown() {
        if (_dbClient instanceof DbClientImplUnitTester) {
            ((DbClientImplUnitTester) _dbClient).removeAll();
        }
    }
    
    @Test
    public void testSRDFBasicPlacement() {
        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        
        // Create 2 Virtual Arrays
        VirtualArray srcVarray = PlacementTestUtils.createVirtualArray(_dbClient, "srcVarray");
        VirtualArray tgtVarray = PlacementTestUtils.createVirtualArray(_dbClient, "tgtVarray");
        
        // Create 2 Networks
        StringSet connVA = new StringSet();
        connVA.add(srcVarray.getId().toString());
        Network network1 = PlacementTestUtils.createNetwork(_dbClient, vmax1FE, "VSANSite1", "FC+BROCADE+FE", connVA);

        connVA = new StringSet();
        connVA.add(tgtVarray.getId().toString());
        Network network2 = PlacementTestUtils.createNetwork(_dbClient, vmax2FE, "VSANSite2", "FC+CISCO+FE", connVA);
        
        // Create 2 storage systems
        StorageSystem[] storageSystems = PlacementTestUtils.createSRDFStorageSystems(_dbClient, "vmax1", "vmax2");
        StorageSystem storageSystem1 = storageSystems[1];
        StorageSystem storageSystem2 = storageSystems[2];
        
        ;
        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network1, vmax1FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite1vmax" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network2, vmax2FE[i], tgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite2vmax" + i, "D0+FC0" + i));
        }
        
        StoragePool[] storagePools = PlacementTestUtils.createStoragePoolsForTwo(_dbClient, storageSystem1, srcVarray, storageSystem2, tgtVarray);
        
        
        // Create a target virtual pool
        VirtualPool tgtVpool = new VirtualPool();
        tgtVpool.setId(URI.create("tgtVpool"));
        tgtVpool.setLabel("Target Vpool");
        tgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        tgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools2 = new StringSet();
        matchedPools2.add(storagePools[4].getId().toString());
        matchedPools2.add(storagePools[5].getId().toString());
        matchedPools2.add(storagePools[6].getId().toString());
        tgtVpool.setMatchedStoragePools(matchedPools2);
        tgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(tgtVarray.getId().toString());
        tgtVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(tgtVpool);
        
        // Make a remote copy protection setting
        VpoolRemoteCopyProtectionSettings settings = new VpoolRemoteCopyProtectionSettings();
        settings.setId(URI.create("remoteCopySettings"));
        settings.setCopyMode(VpoolRemoteCopyProtectionSettings.CopyModes.ASYNCHRONOUS.name());
        settings.setVirtualArray(tgtVarray.getId());
        settings.setVirtualPool(tgtVpool.getId());
        _dbClient.createObject(settings);
        
        // Create an SRDF source virtual pool
        VirtualPool srcVpool = new VirtualPool();
        srcVpool.setId(URI.create("srcVpool"));
        srcVpool.setLabel("Source Vpool");
        srcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        srcVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools1 = new StringSet();
        matchedPools1.add(storagePools[1].getId().toString());
        matchedPools1.add(storagePools[2].getId().toString());
        matchedPools1.add(storagePools[3].getId().toString());
        srcVpool.setMatchedStoragePools(matchedPools1);
        srcVpool.setUseMatchedPools(true);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(srcVarray.getId().toString());
        srcVpool.setVirtualArrays(virtualArrays1);
        StringMap remoteProtectionSettings = new StringMap();
        remoteProtectionSettings.put(tgtVarray.getId().toString(), settings.getId().toString());
        srcVpool.setProtectionRemoteCopySettings(remoteProtectionSettings);
        _dbClient.createObject(srcVpool);
        
        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("RDG1");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, null);

        // Run single volume placement: Run 10 times to make sure pool3 never comes up for source and pool6 for target.
        for (int i = 0; i < 10; i++) {
            Map<VpoolUse, List<Recommendation>> recommendationsMap = 
                    PlacementTestUtils.invokePlacementForVpool(_dbClient, _coordinator, srcVarray, project, srcVpool,
                    capabilities);
            List<Recommendation> recommendations = recommendationsMap.get(VpoolUse.ROOT);
            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            assert(recommendations.get(0) instanceof SRDFRecommendation);
            SRDFRecommendation srdfRecommendation = (SRDFRecommendation) recommendations.get(0);
            URI srcStoragePool = srdfRecommendation.getSourceStoragePool();
            assert(srcStoragePool.equals(storagePools[1].getId()) || srcStoragePool.equals(storagePools[2].getId()));
            recommendations = recommendationsMap.get(VpoolUse.SRDF_COPY);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            assert(recommendations.get(0) instanceof SRDFCopyRecommendation);
            SRDFCopyRecommendation srdfCopyRecommendation = (SRDFCopyRecommendation) recommendations.get(0);
            URI tgtStoragePool = srdfCopyRecommendation.getSourceStoragePool();
            assert(tgtStoragePool.equals(storagePools[4].getId()) || tgtStoragePool.equals(storagePools[5].getId()));
        }
    }
    
    @Test
    public void testVPlexLocalSRDFBasicPlacement() {
        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] vplexFE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplexBE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };
        
        // Create 2 Virtual Arrays
        VirtualArray srcVarray = PlacementTestUtils.createVirtualArray(_dbClient, "srcVarray");
        VirtualArray tgtVarray = PlacementTestUtils.createVirtualArray(_dbClient, "tgtVarray");
        
        // Create 2 Networks
        StringSet connVA = new StringSet();
        connVA.add(srcVarray.getId().toString());
        Network network1 = PlacementTestUtils.createNetwork(_dbClient, vmax1FE, "VSANSite1", "FC+BROCADE+FE", connVA);

        connVA = new StringSet();
        connVA.add(tgtVarray.getId().toString());
        Network network2 = PlacementTestUtils.createNetwork(_dbClient, vmax2FE, "VSANSite2", "FC+CISCO+FE", connVA);
        
        // Create 2 storage systems
        StorageSystem[] storageSystems = PlacementTestUtils.createSRDFStorageSystems(_dbClient, "vmax1", "vmax2");
        StorageSystem storageSystem1 = storageSystems[1];
        StorageSystem storageSystem2 = storageSystems[2];
        
        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network1, vmax1FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite1vmax" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network2, vmax2FE[i], tgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite2vmax" + i, "D0+FC0" + i));
        }
        
        StoragePool[] storagePools = PlacementTestUtils.createStoragePoolsForTwo(_dbClient, storageSystem1, srcVarray, storageSystem2, tgtVarray);
        
        StorageSystem vplexSystem = 
                PlacementTestUtils.createVPlexOneCluster(_dbClient, "vplex1", srcVarray, network1, network1, vplexFE, vplexBE);
        
        // Create a target virtual pool
        VirtualPool tgtVpool = new VirtualPool();
        tgtVpool.setId(URI.create("tgtVpool"));
        tgtVpool.setLabel("Target Vpool");
        tgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        tgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools2 = new StringSet();
        matchedPools2.add(storagePools[4].getId().toString());
        matchedPools2.add(storagePools[5].getId().toString());
        matchedPools2.add(storagePools[6].getId().toString());
        tgtVpool.setMatchedStoragePools(matchedPools2);
        tgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(tgtVarray.getId().toString());
        tgtVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(tgtVpool);
        
        // Make a remote copy protection setting
        VpoolRemoteCopyProtectionSettings settings = new VpoolRemoteCopyProtectionSettings();
        settings.setId(URI.create("remoteCopySettings"));
        settings.setCopyMode(VpoolRemoteCopyProtectionSettings.CopyModes.ASYNCHRONOUS.name());
        settings.setVirtualArray(tgtVarray.getId());
        settings.setVirtualPool(tgtVpool.getId());
        _dbClient.createObject(settings);
        
        // Create an VPLEX Local/SRDF source virtual pool
        VirtualPool srcVpool = new VirtualPool();
        srcVpool.setId(URI.create("srcVpool"));
        srcVpool.setLabel("Source Vpool");
        srcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        srcVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools1 = new StringSet();
        matchedPools1.add(storagePools[1].getId().toString());
        matchedPools1.add(storagePools[2].getId().toString());
        matchedPools1.add(storagePools[3].getId().toString());
        srcVpool.setMatchedStoragePools(matchedPools1);
        srcVpool.setUseMatchedPools(true);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(srcVarray.getId().toString());
        srcVpool.setVirtualArrays(virtualArrays1);
        StringMap remoteProtectionSettings = new StringMap();
        remoteProtectionSettings.put(tgtVarray.getId().toString(), settings.getId().toString());
        srcVpool.setProtectionRemoteCopySettings(remoteProtectionSettings);
        srcVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        _dbClient.createObject(srcVpool);
        
        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("RDG1");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, null);

        // Run single volume placement: Run 10 times to make sure pool3 never comes up for source and pool6 for target.
        for (int i = 0; i < 10; i++) {
            Map<VpoolUse, List<Recommendation>> recommendationsMap = 
                    PlacementTestUtils.invokePlacementForVpool(_dbClient, _coordinator, srcVarray, project, srcVpool,
                    capabilities);
            List<Recommendation> recommendations = recommendationsMap.get(VpoolUse.ROOT);
            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            assert(recommendations.get(0) instanceof VPlexRecommendation);
            VPlexRecommendation vplexRecommendation = (VPlexRecommendation) recommendations.get(0);
            URI srcStoragePool = vplexRecommendation.getSourceStoragePool();
            assert(srcStoragePool.equals(storagePools[1].getId()) || srcStoragePool.equals(storagePools[2].getId()));
            assert(vplexRecommendation.getRecommendation() instanceof SRDFRecommendation);
            recommendations = recommendationsMap.get(VpoolUse.SRDF_COPY);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            assert(recommendations.get(0) instanceof SRDFCopyRecommendation);
            SRDFCopyRecommendation srdfCopyRecommendation = (SRDFCopyRecommendation) recommendations.get(0);
            URI tgtStoragePool = srdfCopyRecommendation.getSourceStoragePool();
            assert(tgtStoragePool.equals(storagePools[4].getId()) || tgtStoragePool.equals(storagePools[5].getId()));
        }
    }

    @Test
    @Ignore
    public void testDbClientSanity() {
        StoragePool pool1 = new StoragePool();
        pool1.setId(URI.create("pool1"));
        pool1.setLabel("Pool1");
        _dbClient.persistObject(pool1);

        StoragePool tempPool = _dbClient.queryObject(StoragePool.class, URI.create("pool1"));
        assertNotNull(tempPool);
    }

    /**
     * Simple block placement. Give block two pools of different capacities.
     * Request a single volume, ensure you get the bigger pool as a recommendation.
     */
    @Test
    @Ignore
    public void testPlacementBlock() {
        // Create a Virtual Array
        VirtualArray varray = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");

        // Create a storage system
        StorageSystem storageSystem = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "storageSystem1");

        // Create a storage pool
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem, "pool1", "Pool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem, "pool2", "Pool2",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem, "pool3", "Pool3",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a virtual pool
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URI.create("vpool"));
        vpool.setLabel("vpool");
        vpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        vpool.setMatchedStoragePools(matchedPools);
        vpool.setUseMatchedPools(true);
        _dbClient.createObject(vpool);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        _dbClient.createObject(project);

        // Make a capabilities object
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, null);

        // Run single volume placement: Run 10 times to make sure pool3 never comes up.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray, project, vpool, capabilities);

            assertNotNull(recommendations);
            assertNotNull(recommendations.get(0));
            VolumeRecommendation rec = (VolumeRecommendation) recommendations.get(0);
            assertNotNull(rec.getCandidatePools());
            assertTrue(rec.getCandidatePools().size() == 1);
            assertNotNull(rec.getCandidateSystems());
            assertTrue("storageSystem1".equals(rec.getCandidateSystems().get(0).toString()));
            assertTrue(("pool2".equals(rec.getCandidatePools().get(0).toString()))
                    || ("pool1".equals(rec.getCandidatePools().get(0).toString())));
            _log.info(
                    "Recommendation " + i + ": " + recommendations.size() + ", Pool Chosen: " + rec.getCandidatePools().get(0).toString());
        }

        // Make a capabilities object
        capabilities = PlacementTestUtils.createCapabilities("2GB", 2, null);

        // Run double volume placement: Run 10 times to make sure pool3 never comes up and
        // you get two recommendation objects with only one pool with two volumes.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray, project, vpool, capabilities);

            assertNotNull(recommendations);
            assertNotNull(recommendations.get(0));
            VolumeRecommendation rec = (VolumeRecommendation) recommendations.get(0);
            VolumeRecommendation rec2 = (VolumeRecommendation) recommendations.get(1);
            assertNotNull(rec.getCandidatePools());
            assertTrue(rec.getCandidatePools().size() == 1);
            assertNotNull(rec.getCandidateSystems());
            assertTrue("storageSystem1".equals(rec.getCandidateSystems().get(0).toString()));
            assertTrue(("pool2".equals(rec.getCandidatePools().get(0).toString()))
                    || ("pool1".equals(rec.getCandidatePools().get(0).toString())));
            assertTrue((rec.getCandidatePools().get(0).toString()).equals(rec2.getCandidatePools().get(0).toString()));
            _log.info(
                    "Recommendation " + i + ": " + recommendations.size() + ", Pool Chosen: " + rec.getCandidatePools().get(0).toString());
        }

        // Make a capabilities object
        capabilities = PlacementTestUtils.createCapabilities("29GB", 2, null);

        // Run double volume placement: Make sure you end up with two recommendation objects.
        // Make sure the two recommendation objects are for different pools since neither pool can fit both.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray, project, vpool, capabilities);

            assertNotNull(recommendations);
            assertNotNull(recommendations.get(0));
            assertNotNull(recommendations.get(1));
            VolumeRecommendation rec = (VolumeRecommendation) recommendations.get(0);
            VolumeRecommendation rec2 = (VolumeRecommendation) recommendations.get(1);
            assertNotNull(rec.getCandidatePools());
            assertTrue(rec.getCandidatePools().size() == 1);
            assertNotNull(rec.getCandidateSystems());
            assertTrue("storageSystem1".equals(rec.getCandidateSystems().get(0).toString()));
            assertTrue(("pool2".equals(rec.getCandidatePools().get(0).toString()))
                    || ("pool1".equals(rec.getCandidatePools().get(0).toString())));
            // Ensure the recommendation objects are not pointing to the same storage pool.
            assertTrue(!(rec.getCandidatePools().get(0).toString()).equals(rec2.getCandidatePools().get(0).toString()));
            _log.info(
                    "Recommendation " + i + ": " + recommendations.size() + ", Pool Chosen: " + rec.getCandidatePools().get(0).toString());
        }
    }

    /**
     * Simple VPLEX local block placement.
     */
    @Test
    @Ignore
    public void testPlacementVPlex() {
        String[] vplexFE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplexBE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        String[] vmaxFE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };

        // Create a Network
        Network networkFE = PlacementTestUtils.createNetwork(_dbClient, vplexFE, "VSANFE", "FC+BROCADE+FE", null);
        Network networkBE = PlacementTestUtils.createNetwork(_dbClient, vplexBE, "VSANBE", "FC+BROCADE+BE", null);

        // Create a Virtual Array
        VirtualArray varray = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");

        // Create a storage system
        StorageSystem storageSystem = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");

        // Create two front-end storage ports VMAX
        List<StoragePort> vmaxPorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vmaxFE.length; i++) {
            vmaxPorts.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem, networkBE, vmaxFE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax" + i, "C0+FC0" + i));
        }

        // Create a VPLEX storage system
        StorageSystem vplexStorageSystem = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");

        // Create two front-end storage ports VPLEX
        List<StoragePort> fePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexFE.length; i++) {
            fePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkFE, vplexFE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupFE" + i, "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX
        List<StoragePort> bePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexBE.length; i++) {
            bePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkBE, vplexBE[i], varray,
                    StoragePort.PortType.backend.name(), "portGroupBE" + i, "B0+FC0" + i));
        }

        // Create a storage pool
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem, "pool1", "Pool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem, "pool2", "Pool2",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem, "pool3", "Pool3",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a virtual pool
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URI.create("vpool"));
        vpool.setLabel("vpool");
        vpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        vpool.setMatchedStoragePools(matchedPools);
        vpool.setUseMatchedPools(true);
        _dbClient.createObject(vpool);

        // Create a VPLEX virtual pool
        VirtualPool vplexVpool = new VirtualPool();
        vplexVpool.setId(URI.create("vplexVpool"));
        vplexVpool.setLabel("vplexVpool");
        vplexVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vplexVpool.setDriveType(SupportedDriveTypes.FC.name());
        vplexVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        vplexVpool.setMatchedStoragePools(matchedPools);
        vplexVpool.setUseMatchedPools(true);
        _dbClient.createObject(vplexVpool);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        _dbClient.createObject(project);

        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, null);

        // Run single volume placement: Run 10 times to make sure pool3 never comes up.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray, project, vplexVpool, capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            VPlexRecommendation rec = (VPlexRecommendation) recommendations.get(0);
            assertNotNull(rec.getSourceStoragePool());
            assertNotNull(rec.getSourceStorageSystem());
            assertNotNull(rec.getVPlexStorageSystem());
            assertTrue("vmax1".equals(rec.getSourceStorageSystem().toString()));
            assertTrue("vplex1".equals(rec.getVPlexStorageSystem().toString()));
            assertTrue(("pool2".equals(rec.getSourceStoragePool().toString())) || ("pool1".equals(rec.getSourceStoragePool().toString())));
            _log.info("Recommendation " + i + ": " + recommendations.size() + ", Pool Chosen: " + rec.getSourceStoragePool().toString());
        }
    }

    /**
     * VPLEX HA remote block placement.
     */
    @Test
    @Ignore
    public void testPlacementVPlexHARemote() {
        String[] vplex1FE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplex1BE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        String[] vplex2FE = { "FE:FE:FE:FE:FE:FE:FE:02", "FE:FE:FE:FE:FE:FE:FE:03" };
        String[] vplex2BE = { "BE:BE:BE:BE:BE:BE:BE:02", "BE:BE:BE:BE:BE:BE:BE:03" };

        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };

        // Create a Virtual Array
        VirtualArray varray1 = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");
        VirtualArray varray2 = PlacementTestUtils.createVirtualArray(_dbClient, "varray2");

        // Create Networks
        StringSet connVA = new StringSet();
        connVA.add(varray1.getId().toString());
        Network networkFE1 = PlacementTestUtils.createNetwork(_dbClient, vplex1FE, "VSANFE1", "FC+BROCADE+FE", connVA);
        Network networkBE1 = PlacementTestUtils.createNetwork(_dbClient, vplex1BE, "VSANBE1", "FC+BROCADE+BE", connVA);

        connVA = new StringSet();
        connVA.add(varray2.getId().toString());
        Network networkFE2 = PlacementTestUtils.createNetwork(_dbClient, vplex2FE, "VSANFE2", "FC+CISCO+FE", connVA);
        Network networkBE2 = PlacementTestUtils.createNetwork(_dbClient, vplex2BE, "VSANBE2", "FC+CISCO+BE", connVA);

        // Create a storage system
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, networkBE1, vmax1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupvmax1" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, networkBE2, vmax2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupvmax2" + i, "D0+FC0" + i));
        }

        // Create 2 VPLEX storage systems
        StorageSystem vplexStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");

        // Create two front-end storage ports VPLEX1
        List<StoragePort> fePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkFE1, vplex1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupFE1" + i, "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1
        List<StoragePort> bePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkBE1, vplex1BE[i], varray1,
                    StoragePort.PortType.backend.name(), "portGroupBE1" + i, "B0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX2
        List<StoragePort> fePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2FE.length; i++) {
            fePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkFE2, vplex2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupFE2" + i, "E0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX2
        List<StoragePort> bePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2BE.length; i++) {
            bePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkBE2, vplex2BE[i], varray2,
                    StoragePort.PortType.backend.name(), "portGroupBE2" + i, "F0+FC0" + i));
        }

        // Create a storage pool
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool1", "Pool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool2", "Pool2",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool3", "Pool3",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool4", "Pool4",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool5", "Pool5",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool6", "Pool6",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a virtual pool
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URI.create(haVpoolUri));
        vpool.setLabel("vpool");
        vpool.setType("block");
        vpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        vpool.setMatchedStoragePools(matchedPools);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(varray2.getId().toString());
        vpool.setVirtualArrays(virtualArrays2);
        vpool.setUseMatchedPools(true);
        _dbClient.createObject(vpool);

        // Create a VPLEX virtual pool
        VirtualPool vplexVpool = new VirtualPool();
        vplexVpool.setId(URI.create("vplexVpool"));
        vplexVpool.setLabel("vplexVpool");
        vplexVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vplexVpool.setDriveType(SupportedDriveTypes.FC.name());
        vplexVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_distributed.name());
        StringMap vavpMap = new StringMap();
        vavpMap.put(varray2.getId().toString(), vpool.getId().toString());
        vplexVpool.setHaVarrayVpoolMap(vavpMap);
        matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        vplexVpool.setMatchedStoragePools(matchedPools);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(varray1.getId().toString());
        vplexVpool.setVirtualArrays(virtualArrays1);
        vplexVpool.setUseMatchedPools(true);
        _dbClient.createObject(vplexVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, null);

        // Run single volume placement: Run 10 times to make sure pool3 and pool6 never come up.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray1, project, vplexVpool, capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            assertNotNull(recommendations.get(1));
            VPlexRecommendation srcRec = (VPlexRecommendation) recommendations.get(0);
            _log.info("##################################" + srcRec.toString());
            VPlexRecommendation HARec = (VPlexRecommendation) recommendations.get(1);
            assertNotNull(srcRec.getSourceStoragePool());
            assertNotNull(srcRec.getSourceStorageSystem());
            assertNotNull(srcRec.getVPlexStorageSystem());
            assertNotNull(srcRec.getVirtualArray());
            assertNotNull(srcRec.getVirtualPool());
            assertNotNull(HARec.getSourceStoragePool());
            assertNotNull(HARec.getSourceStorageSystem());
            assertNotNull(HARec.getVPlexStorageSystem());
            assertNotNull(HARec.getVirtualArray());
            assertNotNull(HARec.getVirtualPool());
            assertTrue(("pool1".equals(srcRec.getSourceStoragePool().toString()))
                    || ("pool2".equals(srcRec.getSourceStoragePool().toString())));
            assertTrue("vmax1".equals(srcRec.getSourceStorageSystem().toString()));
            assertTrue("vplex1".equals(srcRec.getVPlexStorageSystem().toString()));
            assertTrue("varray1".equals(srcRec.getVirtualArray().toString()));
            assertTrue("vplexVpool".equals(srcRec.getVirtualPool().getId().toString()));
            assertTrue(
                    ("pool4".equals(HARec.getSourceStoragePool().toString())) || ("pool5".equals(HARec.getSourceStoragePool().toString())));
            assertTrue("vmax2".equals(HARec.getSourceStorageSystem().toString()));
            assertTrue("vplex1".equals(HARec.getVPlexStorageSystem().toString()));
            assertTrue("varray2".equals(HARec.getVirtualArray().toString()));
            assertTrue(haVpoolUri.equals(HARec.getVirtualPool().getId().toString()));
            _log.info("Recommendation " + i + ": " + recommendations.size() + ", Src Pool Chosen: "
                    + srcRec.getSourceStoragePool().toString() + ", HA Pool Chosen: " + HARec.getSourceStoragePool().toString());
        }
    }

    /**
     * Simple VPLEX local XIO block placement.
     */
    @Test
    @Ignore
    public void testPlacementVPlexXIO() {
        String[] vplexFE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplexBE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        String[] xio1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] xio2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] xio3FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };

        // Create a Network
        Network networkFE = PlacementTestUtils.createNetwork(_dbClient, vplexFE, "VSANFE", "FC+BROCADE+FE", null);
        Network networkBE = PlacementTestUtils.createNetwork(_dbClient, vplexBE, "VSANBE", "FC+BROCADE+BE", null);

        // Create a Virtual Array
        VirtualArray varray = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");

        // Create 3 storage systems for xio
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio2");
        StorageSystem storageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio3");

        // Create two front-end storage ports xio1
        List<StoragePort> xio1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio1FE.length; i++) {
            xio1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, networkBE, xio1FE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupXio1" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports xio2
        List<StoragePort> xio2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio2FE.length; i++) {
            xio2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, networkBE, xio2FE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupXio2" + i, "D0+FC0" + i));
        }

        // Create two front-end storage ports xio3
        List<StoragePort> xio3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio3FE.length; i++) {
            xio3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem3, networkBE, xio3FE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupXio3" + i, "E0+FC0" + i));
        }

        // Create a VPLEX storage system
        StorageSystem vplexStorageSystem = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");

        // Create two front-end storage ports VPLEX
        List<StoragePort> fePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexFE.length; i++) {
            fePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkFE, vplexFE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupFE" + i, "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX
        List<StoragePort> bePorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vplexBE.length; i++) {
            bePorts.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem, networkBE, vplexBE[i], varray,
                    StoragePort.PortType.backend.name(), "portGroupBE" + i, "B0+FC0" + i));
        }

        // Create a storage pool on xio1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem1, "pool1", "Pool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool on xio2
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem2, "pool2", "Pool2",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool on xio3
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray, storageSystem3, "pool3", "Pool3",
                Long.valueOf(1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a virtual pool
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URI.create("vpool"));
        vpool.setLabel("vpool");
        vpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        vpool.setMatchedStoragePools(matchedPools);
        vpool.setUseMatchedPools(true);
        _dbClient.createObject(vpool);

        // Create a VPLEX virtual pool
        VirtualPool vplexVpool = new VirtualPool();
        vplexVpool.setId(URI.create("vplexVpool"));
        vplexVpool.setLabel("vplexVpool");
        vplexVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vplexVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());

        vplexVpool.setDriveType(SupportedDriveTypes.FC.name());        //
        matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        vplexVpool.setMatchedStoragePools(matchedPools);
        vplexVpool.setUseMatchedPools(true);
        _dbClient.createObject(vplexVpool);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        _dbClient.createObject(project);

        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, null);

        // Run single volume placement: Run 10 times to make sure pool2 nRever comes up.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray, project, vplexVpool, capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            VPlexRecommendation rec = (VPlexRecommendation) recommendations.get(0);
            assertNotNull(rec.getSourceStoragePool());
            assertNotNull(rec.getSourceStorageSystem());
            assertNotNull(rec.getVPlexStorageSystem());
            assertTrue(("xtremio3".equals(rec.getSourceStorageSystem().toString()))
                    || ("xtremio1".equals(rec.getSourceStorageSystem().toString())));
            assertTrue("vplex1".equals(rec.getVPlexStorageSystem().toString()));
            assertTrue(("pool3".equals(rec.getSourceStoragePool().toString())) || ("pool1".equals(rec.getSourceStoragePool().toString())));
            _log.info("Recommendation " + i + ": " + recommendations.size() + ", Pool Chosen: " + rec.getSourceStoragePool().toString());

            _log.info(rec.toString());
        }

    }

    /**
     * Simple block placement with RP
     * Basic RP Placement test - VMAX
     */
    @Test
    @Ignore
    public void testBasicRPPlacement() {
        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };

        // Create 2 Virtual Arrays
        VirtualArray rpSrcVarray = PlacementTestUtils.createVirtualArray(_dbClient, "rpSrcVarray");
        VirtualArray rpTgtVarray = PlacementTestUtils.createVirtualArray(_dbClient, "rpTgtVarray");

        // Create 2 Networks
        StringSet connVA = new StringSet();
        connVA.add(rpSrcVarray.getId().toString());
        Network network1 = PlacementTestUtils.createNetwork(_dbClient, rp1FE, "VSANSite1", "FC+BROCADE+FE", connVA);

        connVA = new StringSet();
        connVA.add(rpTgtVarray.getId().toString());
        Network network2 = PlacementTestUtils.createNetwork(_dbClient, rp2FE, "VSANSite2", "FC+CISCO+FE", connVA);

        // Create 2 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network1, vmax1FE[i], rpSrcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite1vmax" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network2, vmax2FE[i], rpTgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite2vmax" + i, "D0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);
        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", storageSystem1.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", storageSystem2.getSerialNumber()));

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, null, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vmax1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vmax2"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        // Create a storage pool for vmax1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, rpSrcVarray, storageSystem1, "pool1", "Pool1",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, rpSrcVarray, storageSystem1, "pool2", "Pool2",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, rpSrcVarray, storageSystem1, "pool3", "Pool3",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, rpTgtVarray, storageSystem2, "pool4", "Pool4",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, rpTgtVarray, storageSystem2, "pool5", "Pool5",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, rpTgtVarray, storageSystem2, "pool6", "Pool6",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a virtual pool
        VirtualPool rpTgtVpool = new VirtualPool();
        rpTgtVpool.setId(URI.create("rpTgtVpool"));
        rpTgtVpool.setLabel("RP Target Vpool");
        rpTgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpTgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        rpTgtVpool.setMatchedStoragePools(matchedPools);
        rpTgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(rpTgtVarray.getId().toString());
        rpTgtVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(rpTgtVpool);

        // Create a RP virtual pool
        VirtualPool rpSrcVpool = new VirtualPool();
        rpSrcVpool.setId(URI.create("rpSrcVpool"));
        rpSrcVpool.setLabel("RP Source Vpool");
        rpSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(rpTgtVpool.getId());
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();

        protectionVarray.put(rpTgtVarray.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpSrcVpool.setProtectionVarraySettings(protectionVarray);
        rpSrcVpool.setRpCopyMode("SYNCHRONOUS");
        rpSrcVpool.setRpRpoType("MINUTES");
        rpSrcVpool.setRpRpoValue(Long.valueOf("5"));
        matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        rpSrcVpool.setMatchedStoragePools(matchedPools);
        rpSrcVpool.setUseMatchedPools(true);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(rpSrcVarray.getId().toString());
        rpSrcVpool.setVirtualArrays(virtualArrays1);
        _dbClient.createObject(rpSrcVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, cg);

        // Run single volume placement: Run 10 times to make sure pool3 never comes up for source and pool6 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, rpSrcVarray, project, rpSrcVpool,
                    capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));

            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);
            assertNotNull(rec.getSourceRecommendations());
            assertTrue(!rec.getSourceRecommendations().isEmpty());
            assertNotNull(rec.getProtectionDevice());
            assertNotNull(rec.getPlacementStepsCompleted().name());
            assertTrue("rp1".equals(rec.getProtectionDevice().toString()));

            for (RPRecommendation rpRec : rec.getSourceRecommendations()) {
                assertNotNull(rpRec.getInternalSiteName());
                assertNotNull(rpRec.getSourceStorageSystem());
                assertNotNull(rpRec.getSourceStoragePool());
                assertTrue("site1".equals(rpRec.getInternalSiteName()));
                assertTrue("vmax1".equals(rpRec.getSourceStorageSystem().toString()));
                assertTrue(("pool2".equals(rpRec.getSourceStoragePool().toString()))
                        || ("pool1".equals(rpRec.getSourceStoragePool().toString())));

                assertNotNull(rpRec.getTargetRecommendations());
                assertTrue(!rpRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : rpRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getSourceStoragePool());
                    assertTrue("vmax2".equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue("site2".equals(targetRec.getInternalSiteName()));
                    assertTrue("pool5".equals(targetRec.getSourceStoragePool().toString())
                            || "pool4".equals(targetRec.getSourceStoragePool().toString()));
                }
            }

            // source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
            assertNotNull(rec.getSourceJournalRecommendation().getInternalSiteName());
            assertTrue(("pool2".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString()))
                    || ("pool1".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));

            // target journal
            assertNotNull(rec.getTargetJournalRecommendations());
            assertNotNull(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec.getSourceStoragePool());
                assertTrue("pool5".equals(targetJournalRec.getSourceStoragePool().toString())
                        || "pool4".equals(targetJournalRec.getSourceStoragePool().toString()));
                assertTrue("site2".equals(targetJournalRec.getInternalSiteName()));
                assertTrue("vmax2".equals(targetJournalRec.getSourceStorageSystem().toString()));

            }
            _log.info(rec.toString(_dbClient));
        }
    }

    
    /**
     * Simple block placement with RP
     * Basic RP Placement test - VMAX
     * In this test, Pool1 from VMAX1 and Pool3 from VMAX3 are available as candidates for source and source journal.
     * However, Pool3 doesnt have connectivity to the RP site and should not be chosen. 
     * Placement also should not fail here, and Pool1 should be chosen for both source and its journal.
     */
    @Test
    @Ignore
    public void testBasicRPPlacement2() {
        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax3FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };

        // Create 2 Virtual Arrays
        VirtualArray rpSrcVarray = PlacementTestUtils.createVirtualArray(_dbClient, "rpSrcVarray");
        VirtualArray rpTgtVarray = PlacementTestUtils.createVirtualArray(_dbClient, "rpTgtVarray");

        // Create 2 Networks
        StringSet connVA = new StringSet();
        connVA.add(rpSrcVarray.getId().toString());
        Network network1 = PlacementTestUtils.createNetwork(_dbClient, rp1FE, "VSANSite1", "FC+BROCADE+FE", connVA);

        connVA = new StringSet();
        connVA.add(rpTgtVarray.getId().toString());
        Network network2 = PlacementTestUtils.createNetwork(_dbClient, rp2FE, "VSANSite2", "FC+CISCO+FE", connVA);

        // Create 2 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");
        StorageSystem storageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax3");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network1, vmax1FE[i], rpSrcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite1vmax" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network2, vmax2FE[i], rpTgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite2vmax" + i, "D0+FC0" + i));
        }
        
        
        // Create two front-end storage ports VMAX3
        List<StoragePort> vmax3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax3FE.length; i++) {
            vmax3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem3, network1, vmax3FE[i], rpSrcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupSite3vmax" + i, "E0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);
        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", storageSystem1.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", storageSystem2.getSerialNumber()));

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, null, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vmax1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vmax2"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        // Create a storage pool for vmax1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, rpSrcVarray, storageSystem1, "pool1", "Pool1",
                Long.valueOf(SIZE_GB * 100), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, rpSrcVarray, storageSystem1, "pool2", "Pool2",
                Long.valueOf(SIZE_GB * 100), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, rpSrcVarray, storageSystem3, "pool3", "Pool3",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, rpTgtVarray, storageSystem2, "pool4", "Pool4",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, rpTgtVarray, storageSystem2, "pool5", "Pool5",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, rpTgtVarray, storageSystem2, "pool6", "Pool6",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a virtual pool
        VirtualPool rpTgtVpool = new VirtualPool();
        rpTgtVpool.setId(URI.create("rpTgtVpool"));
        rpTgtVpool.setLabel("RP Target Vpool");
        rpTgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpTgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        rpTgtVpool.setMatchedStoragePools(matchedPools);
        rpTgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(rpTgtVarray.getId().toString());
        rpTgtVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(rpTgtVpool);

        // Create a RP virtual pool
        VirtualPool rpSrcVpool = new VirtualPool();
        rpSrcVpool.setId(URI.create("rpSrcVpool"));
        rpSrcVpool.setLabel("RP Source Vpool");
        rpSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(rpTgtVpool.getId());
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();

        protectionVarray.put(rpTgtVarray.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpSrcVpool.setProtectionVarraySettings(protectionVarray);
        rpSrcVpool.setRpCopyMode("SYNCHRONOUS");
        rpSrcVpool.setRpRpoType("MINUTES");
        rpSrcVpool.setRpRpoValue(Long.valueOf("5"));
        matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        //matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        rpSrcVpool.setMatchedStoragePools(matchedPools);
        rpSrcVpool.setUseMatchedPools(true);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(rpSrcVarray.getId().toString());
        rpSrcVpool.setVirtualArrays(virtualArrays1);
        _dbClient.createObject(rpSrcVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, cg);

        // Run single volume placement: Run 10 times to make sure pool3 never comes up for source and pool6 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, rpSrcVarray, project, rpSrcVpool,
                    capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));

            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);
            assertNotNull(rec.getSourceRecommendations());
            assertTrue(!rec.getSourceRecommendations().isEmpty());
            assertNotNull(rec.getProtectionDevice());
            assertNotNull(rec.getPlacementStepsCompleted().name());
            assertTrue("rp1".equals(rec.getProtectionDevice().toString()));

            for (RPRecommendation rpRec : rec.getSourceRecommendations()) {
                assertNotNull(rpRec.getInternalSiteName());
                assertNotNull(rpRec.getSourceStorageSystem());
                assertNotNull(rpRec.getSourceStoragePool());
                assertTrue("site1".equals(rpRec.getInternalSiteName()));
                assertTrue("vmax1".equals(rpRec.getSourceStorageSystem().toString()));
                assertTrue(("pool2".equals(rpRec.getSourceStoragePool().toString()))
                        || ("pool1".equals(rpRec.getSourceStoragePool().toString())));

                assertNotNull(rpRec.getTargetRecommendations());
                assertTrue(!rpRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : rpRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getSourceStoragePool());
                    assertTrue("vmax2".equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue("site2".equals(targetRec.getInternalSiteName()));
                    assertTrue("pool5".equals(targetRec.getSourceStoragePool().toString())
                            || "pool4".equals(targetRec.getSourceStoragePool().toString()));
                }
            }

            // source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
            assertNotNull(rec.getSourceJournalRecommendation().getInternalSiteName());
            assertTrue(("pool2".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString()))
                    || ("pool1".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));

            // target journal
            assertNotNull(rec.getTargetJournalRecommendations());
            assertNotNull(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec.getSourceStoragePool());
                assertTrue("pool5".equals(targetJournalRec.getSourceStoragePool().toString())
                        || "pool4".equals(targetJournalRec.getSourceStoragePool().toString()));
                assertTrue("site2".equals(targetJournalRec.getInternalSiteName()));
                assertTrue("vmax2".equals(targetJournalRec.getSourceStorageSystem().toString()));

            }
            _log.info(rec.toString(_dbClient));
        }
    }
    
    /**
     * Simple block placement with RP
     * Basic RP Placement test - VMAX
     * This is a negative test. Placement should fail.
     */
    @Test
    @Ignore
    public void testNegativeBasicRPPlacement() {
        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };

        // Create 2 Virtual Arrays
        VirtualArray varray1 = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");
        VirtualArray varray2 = PlacementTestUtils.createVirtualArray(_dbClient, "varray2");

        // Create 2 Networks
        StringSet connVA = new StringSet();
        connVA.add(varray1.getId().toString());
        Network network1 = PlacementTestUtils.createNetwork(_dbClient, rp1FE, "VSANSite1", "FC+BROCADE+FE", connVA);

        connVA = new StringSet();
        connVA.add(varray2.getId().toString());
        Network network2 = PlacementTestUtils.createNetwork(_dbClient, rp2FE, "VSANSite2", "FC+CISCO+FE", connVA);

        // Create 2 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network1, vmax1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupSite1vmax" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network2, vmax2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupSite2vmax" + i, "D0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);
        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", storageSystem1.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", storageSystem2.getSerialNumber()));

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, null, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vmax1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vmax2"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        // Create a storage pool for vmax1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool1", "Pool1",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool2", "Pool2",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool3", "Pool3",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool4", "Pool4",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool5", "Pool5",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool6", "Pool6",
                Long.valueOf(SIZE_GB * 1), Long.valueOf(SIZE_GB * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a RP virtual pool
        VirtualPool rpTgtVpool = new VirtualPool();
        rpTgtVpool.setId(URI.create("rpTgtVpool"));
        rpTgtVpool.setLabel("rpTgtVpool");
        rpTgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpTgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(URI.create("vpool"));
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();

        protectionVarray.put(varray2.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpTgtVpool.setProtectionVarraySettings(protectionVarray);
        rpTgtVpool.setRpCopyMode("SYNCHRONOUS");
        rpTgtVpool.setRpRpoType("MINUTES");
        rpTgtVpool.setRpRpoValue(Long.valueOf("5"));
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        rpTgtVpool.setMatchedStoragePools(matchedPools);
        rpTgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(varray1.getId().toString());
        rpTgtVpool.setVirtualArrays(virtualArrays1);
        _dbClient.createObject(rpTgtVpool);

        // Create a virtual pool
        VirtualPool rpSrcVpool = new VirtualPool();
        rpSrcVpool.setId(URI.create("vpool"));
        rpSrcVpool.setLabel("vpool");
        rpSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        rpSrcVpool.setMatchedStoragePools(matchedPools);
        rpSrcVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(varray2.getId().toString());
        rpSrcVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(rpSrcVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, cg);

        // Run single volume placement: Run 10 times to make sure placement fails. There are no pools for RP source that can satisfy the
        // requested size of volume.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray1, project, rpTgtVpool, capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));

            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);
            assertNotNull(rec.getSourceRecommendations());
            assertTrue(!rec.getSourceRecommendations().isEmpty());
            assertNotNull(rec.getProtectionDevice());
            assertNotNull(rec.getPlacementStepsCompleted().name());
            assertTrue("rp1".equals(rec.getProtectionDevice().toString()));

            for (RPRecommendation rpRec : rec.getSourceRecommendations()) {
                assertNotNull(rpRec.getInternalSiteName());
                assertNotNull(rpRec.getSourceStorageSystem());
                assertNotNull(rpRec.getSourceStoragePool());
                assertTrue("site1".equals(rpRec.getInternalSiteName()));
                assertTrue("vmax1".equals(rpRec.getSourceStorageSystem().toString()));
                assertTrue(("pool1".equals(rpRec.getSourceStoragePool().toString())));

                assertNotNull(rpRec.getTargetRecommendations());
                assertTrue(!rpRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : rpRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getSourceStoragePool());
                    assertTrue("vmax2".equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue("site2".equals(targetRec.getInternalSiteName()));
                    assertTrue("pool5".equals(targetRec.getSourceStoragePool().toString())
                            || "pool4".equals(targetRec.getSourceStoragePool().toString()));
                }
            }

            // source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
            assertNotNull(rec.getSourceJournalRecommendation().getInternalSiteName());
            assertTrue(("pool1".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));

            // target journal
            assertNotNull(rec.getTargetJournalRecommendations());
            assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec.getSourceStoragePool());
                assertTrue("pool5".equals(targetJournalRec.getSourceStoragePool().toString())
                        || "pool4".equals(targetJournalRec.getSourceStoragePool().toString()));
                assertTrue("site2".equals(targetJournalRec.getInternalSiteName()));
                assertTrue("vmax2".equals(targetJournalRec.getSourceStorageSystem().toString()));

            }
            _log.info(rec.toString(_dbClient));
        }
    }

    /**
     * RP placement tests with XIO (no VPLEX)
     */
    @Test
    @Ignore
    public void testPlacementRpXIONoVplex() {
        String[] xio1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] xio2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] xio3FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };
        String[] xio4FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };
        String[] xio5FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };
        String[] xio6FE = { "55:FE:FE:FE:FE:FE:FE:00", "55:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "56:FE:FE:FE:FE:FE:FE:00", "56:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "57:FE:FE:FE:FE:FE:FE:00", "57:FE:FE:FE:FE:FE:FE:01" };

        // Create 2 Virtual Arrays
        VirtualArray varray1 = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");
        VirtualArray varray2 = PlacementTestUtils.createVirtualArray(_dbClient, "varray2");

        // Create 2 Networks
        StringSet connVA = new StringSet();
        connVA.add(varray1.getId().toString());
        Network network1 = PlacementTestUtils.createNetwork(_dbClient, rp1FE, "VSANSite1", "FC+BROCADE+FE", connVA);

        connVA = new StringSet();
        connVA.add(varray2.getId().toString());
        Network network2 = PlacementTestUtils.createNetwork(_dbClient, rp2FE, "VSANSite2", "FC+CISCO+FE", connVA);

        // Create 6 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio2");
        StorageSystem storageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio3");
        StorageSystem storageSystem4 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio4");
        StorageSystem storageSystem5 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio5");
        StorageSystem storageSystem6 = PlacementTestUtils.createStorageSystem(_dbClient, "xtremio", "xtremio6");

        // Create two front-end storage ports XIO1
        List<StoragePort> xio1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio1FE.length; i++) {
            xio1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network1, xio1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupSite1xio1" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports XIO2
        List<StoragePort> xio2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio2FE.length; i++) {
            xio2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network1, xio2FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupSite1xio2" + i, "D0+FC0" + i));
        }

        // Create two front-end storage ports XIO3
        List<StoragePort> xio3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio3FE.length; i++) {
            xio3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem3, network1, xio3FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupSite1xio3" + i, "E0+FC0" + i));
        }

        // Create two front-end storage ports XIO4
        List<StoragePort> xio4Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio4FE.length; i++) {
            xio4Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem4, network2, xio4FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupSite2xio4" + i, "F0+FC0" + i));
        }

        // Create two front-end storage ports XIO5
        List<StoragePort> xio5Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio5FE.length; i++) {
            xio5Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem5, network2, xio5FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupSite2xio5" + i, "G0+FC0" + i));
        }

        // Create two front-end storage ports XIO6
        List<StoragePort> xio6Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < xio6FE.length; i++) {
            xio6Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem6, network2, xio6FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupSite2xio6" + i, "H0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);

        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", storageSystem1.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", storageSystem2.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", storageSystem3.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", storageSystem4.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", storageSystem5.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", storageSystem6.getSerialNumber()));

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, null, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("xtremio1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("xtremio2"));
        rpSiteArray2.setRpInternalSiteName("site1");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        RPSiteArray rpSiteArray3 = new RPSiteArray();
        rpSiteArray3.setId(URI.create("rsa3"));
        rpSiteArray3.setStorageSystem(URI.create("xtremio3"));
        rpSiteArray3.setRpInternalSiteName("site1");
        rpSiteArray3.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray3);

        RPSiteArray rpSiteArray4 = new RPSiteArray();
        rpSiteArray4.setId(URI.create("rsa4"));
        rpSiteArray4.setStorageSystem(URI.create("xtremio4"));
        rpSiteArray4.setRpInternalSiteName("site2");
        rpSiteArray4.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray4);

        RPSiteArray rpSiteArray5 = new RPSiteArray();
        rpSiteArray5.setId(URI.create("rsa5"));
        rpSiteArray5.setStorageSystem(URI.create("xtremio5"));
        rpSiteArray5.setRpInternalSiteName("site2");
        rpSiteArray5.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray5);

        RPSiteArray rpSiteArray6 = new RPSiteArray();
        rpSiteArray6.setId(URI.create("rsa6"));
        rpSiteArray6.setStorageSystem(URI.create("xtremio6"));
        rpSiteArray6.setRpInternalSiteName("site2");
        rpSiteArray6.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray6);

        // Create a storage pool for xio1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool1", "Pool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for xio2
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem2, "pool2", "Pool2",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for xio3
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem3, "pool3", "Pool3",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for xio4
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem4, "pool4", "Pool4",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for xio5
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem5, "pool5", "Pool5",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for xio6
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem6, "pool6", "Pool6",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a RP virtual pool
        VirtualPool rpVpool = new VirtualPool();
        rpVpool.setId(URI.create("rpVpool"));
        rpVpool.setLabel("rpVpool");
        rpVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpVpool.setDriveType(SupportedDriveTypes.FC.name());
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(URI.create("vpool"));
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();

        protectionVarray.put(varray2.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpVpool.setProtectionVarraySettings(protectionVarray);
        rpVpool.setRpCopyMode("SYNCHRONOUS");
        rpVpool.setRpRpoType("MINUTES");
        rpVpool.setRpRpoValue(Long.valueOf("5"));
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        rpVpool.setMatchedStoragePools(matchedPools);
        rpVpool.setUseMatchedPools(true);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(varray1.getId().toString());
        rpVpool.setVirtualArrays(virtualArrays1);
        _dbClient.createObject(rpVpool);

        // Create a virtual pool
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URI.create("vpool"));
        vpool.setLabel("vpool");
        vpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vpool.setDriveType(SupportedDriveTypes.FC.name());
        matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        vpool.setMatchedStoragePools(matchedPools);
        vpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(varray2.getId().toString());
        vpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(vpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, cg);

        // Run single volume placement: Run 10 times to make sure pool3 never comes up for source and pool6 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray1, project, rpVpool, capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));

            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);
            assertNotNull(rec.getSourceRecommendations());
            assertTrue(!rec.getSourceRecommendations().isEmpty());
            assertNotNull(rec.getProtectionDevice());
            assertNotNull(rec.getPlacementStepsCompleted().name());
            assertTrue("rp1".equals(rec.getProtectionDevice().toString()));

            for (RPRecommendation sourceRec : rec.getSourceRecommendations()) {
                assertNotNull(sourceRec);
                assertNotNull(sourceRec.getInternalSiteName());
                assertNotNull(sourceRec.getSourceStorageSystem());
                assertNotNull(sourceRec.getSourceStoragePool());
                assertTrue(sourceRec.getVirtualArray().toString().equals("varray1"));
                assertTrue("site1".equals(sourceRec.getInternalSiteName()));
                assertTrue("xtremio2".equals(sourceRec.getSourceStorageSystem().toString()));
                assertTrue(("pool2".equals(sourceRec.getSourceStoragePool().toString()))
                        || ("pool1".equals(sourceRec.getSourceStoragePool().toString())));

                assertNotNull(sourceRec.getTargetRecommendations());
                assertTrue(!sourceRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : sourceRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getSourceStoragePool());
                    assertTrue("xtremio4".equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue("site2".equals(targetRec.getInternalSiteName()));
                    assertTrue(targetRec.getVirtualArray().toString().equals("varray2"));
                    assertTrue("pool4".equals(targetRec.getSourceStoragePool().toString())
                            || "pool5".equals(targetRec.getSourceStoragePool().toString()));
                }
            }

            // source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
            assertTrue(("pool2".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString()))
                    || ("pool1".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));

            // target journal
            assertNotNull(rec.getTargetJournalRecommendations());
            assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec.getSourceStoragePool());
                assertTrue(targetJournalRec.getVirtualArray().toString().equals("varray2"));
                assertTrue("pool5".equals(targetJournalRec.getSourceStoragePool().toString())
                        || "pool4".equals(targetJournalRec.getSourceStoragePool().toString()));
                assertTrue("site2".equals(targetJournalRec.getInternalSiteName()));
                assertTrue("xtremio5".equals(targetJournalRec.getSourceStorageSystem().toString()));

            }

            _log.info(rec.toString(_dbClient));
        }
    }

    /**
     * RP VPLEX placement
     */

    @Test
    @Ignore
    public void testPlacementRpVplex() {

        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax3FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };

        String[] vplex1FE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplex1BE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        String[] vplex2FE = { "FE:FE:FE:FE:FE:FE:FE:02", "FE:FE:FE:FE:FE:FE:FE:03" };
        String[] vplex2BE = { "BE:BE:BE:BE:BE:BE:BE:02", "BE:BE:BE:BE:BE:BE:BE:03" };

        String[] vplex3FE = { "FE:FE:FE:FE:FE:FE:FE:04", "FE:FE:FE:FE:FE:FE:FE:05" };
        String[] vplex3BE = { "BE:BE:BE:BE:BE:BE:BE:04", "BE:BE:BE:BE:BE:BE:BE:05" };

        // Create 3 Virtual Arrays
        VirtualArray varray1 = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");
        VirtualArray varray2 = PlacementTestUtils.createVirtualArray(_dbClient, "varray2");
        VirtualArray varray3 = PlacementTestUtils.createVirtualArray(_dbClient, "varray3");

        // Create 2 Networks
        StringSet connVA = new StringSet();
        connVA.add(varray1.getId().toString());
        Network networkFE1 = PlacementTestUtils.createNetwork(_dbClient, vplex1FE, "VSANFE1", "FC+BROCADE+FE", connVA);
        Network networkBE1 = PlacementTestUtils.createNetwork(_dbClient, vplex1BE, "VSANBE1", "FC+BROCADE+BE", connVA);

        connVA = new StringSet();
        connVA.add(varray2.getId().toString());
        Network networkFE2 = PlacementTestUtils.createNetwork(_dbClient, (String[]) ArrayUtils.addAll(vplex2FE, rp1FE), "VSANFE2",
                "FC+CISCO+FE", connVA);
        Network networkBE2 = PlacementTestUtils.createNetwork(_dbClient, vplex2BE, "VSANBE2", "FC+CISCO+BE", connVA);

        connVA = new StringSet();
        connVA.add(varray3.getId().toString());
        Network networkFE3 = PlacementTestUtils.createNetwork(_dbClient, (String[]) ArrayUtils.addAll(vplex3FE, rp2FE), "VSANFE3",
                "FC+IBM+FE", connVA);
        Network networkBE3 = PlacementTestUtils.createNetwork(_dbClient, vplex3BE, "VSANBE3", "FC+IBM+BE", connVA);

        // Create 3 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");
        StorageSystem storageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax3");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, networkBE1, vmax1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupvmax1" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, networkBE2, vmax2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupvmax2" + i, "D0+FC0" + i));
        }

        // Create two front-end storage ports VMAX3
        List<StoragePort> vmax3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax3FE.length; i++) {
            vmax3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem3, networkBE3, vmax3FE[i], varray3,
                    StoragePort.PortType.frontend.name(), "portGroupvmax3" + i, "E0+FC0" + i));
        }

        // Create 2 VPLEX storage systems
        StorageSystem vplexStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");
        StorageSystem vplexStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex2");

        // Create two front-end storage ports VPLEX1
        List<StoragePort> fePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkFE1, vplex1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupFE1-" + (i + 1), "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1
        List<StoragePort> bePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkBE1, vplex1BE[i], varray1,
                    StoragePort.PortType.backend.name(), "portGroupBE1-" + (i + 1), "B0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX2
        List<StoragePort> fePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2FE.length; i++) {
            fePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkFE2, vplex2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupFE2-" + (i + 1), "F0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX2
        List<StoragePort> bePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2BE.length; i++) {
            bePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkBE2, vplex2BE[i], varray2,
                    StoragePort.PortType.backend.name(), "portGroupBE2-" + (i + 1), "G0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX3
        List<StoragePort> fePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3FE.length; i++) {
            fePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, networkFE3, vplex3FE[i], varray3,
                    StoragePort.PortType.frontend.name(), "portGroupFE3-" + (i + 1), "H0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX3
        List<StoragePort> bePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3BE.length; i++) {
            bePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, networkBE3, vplex3BE[i], varray3,
                    StoragePort.PortType.backend.name(), "portGroupBE3-" + (i + 1), "I0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);

        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex1cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex2cluster1"));

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, null, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vplex1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vplex2"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        // Create a storage pool for vmax1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool1", "Pool1",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool2", "Pool2",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool3", "Pool3",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool4", "Pool4",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool5", "Pool5",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool6", "Pool6",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool7 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool7", "Pool7",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool8 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool8", "Pool8",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool9 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool9", "Pool9",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a base HA virtual pool
        VirtualPool haVpool = new VirtualPool();
        haVpool.setId(URI.create(dummyVpoolUri));
        haVpool.setLabel("vpoolHA");
        haVpool.setType("block");
        haVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        haVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        haVpool.setMatchedStoragePools(matchedPools);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(varray1.getId().toString());
        haVpool.setVirtualArrays(virtualArrays1);
        haVpool.setUseMatchedPools(true);
        _dbClient.createObject(haVpool);

        // Create a base RP virtual pool
        VirtualPool tgtVpool = new VirtualPool();
        tgtVpool.setId(URI.create("vpoolRP"));
        tgtVpool.setLabel("vpoolRP");
        tgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        tgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        tgtVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        matchedPools = new StringSet();
        matchedPools.add(pool7.getId().toString());
        matchedPools.add(pool8.getId().toString());
        matchedPools.add(pool9.getId().toString());
        tgtVpool.setMatchedStoragePools(matchedPools);
        tgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays3 = new StringSet();
        virtualArrays3.add(varray3.getId().toString());
        tgtVpool.setVirtualArrays(virtualArrays3);
        _dbClient.createObject(tgtVpool);

        // Create a RP VPLEX virtual pool
        VirtualPool rpSrcVpool = new VirtualPool();
        rpSrcVpool.setId(URI.create("rpVplexVpool"));
        rpSrcVpool.setLabel("rpVplexVpool");
        rpSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        rpSrcVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_distributed.name());
        rpSrcVpool.setJournalSize("2.5X");
        StringMap vavpMap = new StringMap();
        vavpMap.put(varray1.getId().toString(), haVpool.getId().toString());
        rpSrcVpool.setHaVarrayVpoolMap(vavpMap);
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(tgtVpool.getId());
        protectionSettings.setJournalSize("32GB");
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();

        protectionVarray.put(varray3.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpSrcVpool.setProtectionVarraySettings(protectionVarray);
        rpSrcVpool.setRpCopyMode("SYNCHRONOUS");
        rpSrcVpool.setRpRpoType("MINUTES");
        rpSrcVpool.setRpRpoValue(Long.valueOf("5"));
        // rpSrcVpool.setHaVarrayConnectedToRp(varray1.getId().toString());
        matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        rpSrcVpool.setMatchedStoragePools(matchedPools);
        rpSrcVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(varray2.getId().toString());
        rpSrcVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(rpSrcVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("5GB", 4, cg);

        // Run single volume placement: Run 10 times to make sure pool6 never comes up for source and pool9 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray2, project, rpSrcVpool, capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);

            for (RPRecommendation rpVplexRec : rec.getSourceRecommendations()) {
                assertNotNull(rpVplexRec.getVirtualVolumeRecommendation());
                assertNotNull(rpVplexRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                assertNotNull(rpVplexRec.getSourceStoragePool());
                assertNotNull(rpVplexRec.getSourceStorageSystem());
                assertNotNull(rpVplexRec.getHaRecommendation());
                assertNotNull(rpVplexRec.getTargetRecommendations());
                assertTrue(!rpVplexRec.getTargetRecommendations().isEmpty());
                assertNotNull(rpVplexRec.getInternalSiteName());
                assertTrue("site1".equals(rpVplexRec.getInternalSiteName()));
                assertTrue("vmax2".equals(rpVplexRec.getSourceStorageSystem().toString()));
                assertTrue(("pool5".equals(rpVplexRec.getSourceStoragePool().toString()))
                        || ("pool4".equals(rpVplexRec.getSourceStoragePool().toString())));
                assertTrue("vplex1".equals(rpVplexRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

                assertNotNull(rpVplexRec.getHaRecommendation().getSourceStoragePool());
                assertNotNull(rpVplexRec.getHaRecommendation().getSourceStorageSystem());
                assertNotNull(rpVplexRec.getHaRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem());

                for (RPRecommendation targetRec : rpVplexRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getSourceStoragePool());
                    assertNotNull(targetRec.getInternalSiteName());
                    assertNotNull(targetRec.getSourceStorageSystem());
                    assertNotNull(targetRec.getVirtualPool());
                    assertTrue("vpoolRP".equals(targetRec.getVirtualPool().getId().toString()));
                    assertTrue("varray3".equals(targetRec.getVirtualArray().toString()));
                    assertTrue("site2".equals(targetRec.getInternalSiteName()));
                    assertTrue("vplex2".equals(targetRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
                    assertTrue("vmax3".equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue(("pool8".equals(targetRec.getSourceStoragePool().toString()))
                            || ("pool7".equals(targetRec.getSourceStoragePool().toString())));
                }
            }

            assertNotNull(rec.getSourceJournalRecommendation());
            assertNotNull(rec.getSourceJournalRecommendation().getInternalSiteName());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStorageSystem());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
            if (rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation() != null) {
                assertTrue("vplex1"
                        .equals(rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
            }
            assertTrue("site1".equals(rec.getSourceJournalRecommendation().getInternalSiteName().toString()));
            assertTrue(("pool5".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString()))
                    || ("pool4".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));

            assertNotNull(rec.getTargetJournalRecommendations());
            assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec.getVirtualArray());
                assertNotNull(targetJournalRec.getInternalSiteName());
                assertNotNull(targetJournalRec.getSourceStorageSystem());
                assertNotNull(targetJournalRec.getSourceStoragePool());
                assertTrue("varray3".equals(targetJournalRec.getVirtualArray().toString()));
                assertTrue("vmax3".equals(targetJournalRec.getSourceStorageSystem().toString()));
                assertTrue("site2".equals(targetJournalRec.getInternalSiteName().toString()));
                assertTrue(("pool9".equals(targetJournalRec.getSourceStoragePool().toString()) ||
                        "pool7".equals(targetJournalRec.getSourceStoragePool().toString())) ||
                        "pool8".equals(targetJournalRec.getSourceStoragePool().toString()));
                assertTrue("vplex2".equals(targetJournalRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

            }
            _log.info(rec.toString(_dbClient));
        }
    }

    /**
     * RP VPLEX placement -- placement decision based on RP array visibility
     */
    @Test
    @Ignore
    public void testPlacementRpVplexAdvancedSite2toSite1() {

        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax3FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };

        String[] vplex1FE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplex1BE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        String[] vplex2FE = { "FE:FE:FE:FE:FE:FE:FE:02", "FE:FE:FE:FE:FE:FE:FE:03" };
        String[] vplex2BE = { "BE:BE:BE:BE:BE:BE:BE:02", "BE:BE:BE:BE:BE:BE:BE:03" };

        String[] vplex3FE = { "FE:FE:FE:FE:FE:FE:FE:04", "FE:FE:FE:FE:FE:FE:FE:05" };
        String[] vplex3BE = { "BE:BE:BE:BE:BE:BE:BE:04", "BE:BE:BE:BE:BE:BE:BE:05" };

        // Create 3 Virtual Arrays
        VirtualArray varray1 = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");
        VirtualArray varray2 = PlacementTestUtils.createVirtualArray(_dbClient, "varray2");
        VirtualArray varray3 = PlacementTestUtils.createVirtualArray(_dbClient, "varray3");

        // Create 1 Network
        StringSet connVA = new StringSet();
        connVA.add(varray1.getId().toString());
        connVA.add(varray2.getId().toString());
        connVA.add(varray3.getId().toString());
        Network network = PlacementTestUtils.createNetwork(_dbClient, vplex1FE, "VSAN", "FC+BROCADE", connVA);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex3FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex1BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex2BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex3BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, rp1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, rp2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax3FE);

        // Create 3 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");
        StorageSystem storageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax3");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network, vmax1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupvmax1" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network, vmax2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupvmax2" + i, "D0+FC0" + i));
        }

        // Create two front-end storage ports VMAX3
        List<StoragePort> vmax3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax3FE.length; i++) {
            vmax3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem3, network, vmax3FE[i], varray3,
                    StoragePort.PortType.frontend.name(), "portGroupvmax3" + i, "E0+FC0" + i));
        }

        // Create 2 VPLEX storage systems
        StorageSystem vplexStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");
        StorageSystem vplexStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex2");

        // Create two front-end storage ports VPLEX1
        List<StoragePort> fePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupFE1-" + (i + 1), "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1
        List<StoragePort> bePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex1BE[i], varray1,
                    StoragePort.PortType.backend.name(), "portGroupBE1-" + (i + 1), "B0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX2
        List<StoragePort> fePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2FE.length; i++) {
            fePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupFE2-" + (i + 1), "F0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX2
        List<StoragePort> bePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2BE.length; i++) {
            bePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex2BE[i], varray2,
                    StoragePort.PortType.backend.name(), "portGroupBE2-" + (i + 1), "G0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX3
        List<StoragePort> fePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3FE.length; i++) {
            fePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, network, vplex3FE[i], varray3,
                    StoragePort.PortType.frontend.name(), "portGroupFE3-" + (i + 1), "H0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX3
        List<StoragePort> bePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3BE.length; i++) {
            bePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, network, vplex3BE[i], varray3,
                    StoragePort.PortType.backend.name(), "portGroupBE3-" + (i + 1), "I0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);

        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex1cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex1cluster2"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex2cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex2cluster2"));

        StringSetMap rpVisibleSystems = new StringSetMap();
        StringSet storageIds = new StringSet();
        storageIds.add(vplexStorageSystem2.getId().toString());
        rpVisibleSystems.put("site1", storageIds);
        StringSet storageIds2 = new StringSet();
        storageIds2.add(vplexStorageSystem1.getId().toString());
        rpVisibleSystems.put("site2", storageIds2);

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, rpVisibleSystems, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vplex1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vplex2"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        // Create a storage pool for vmax1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool1", "Pool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 100), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool2", "Pool2",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 100), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool3", "Pool3",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 10), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool4", "Pool4",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool5", "Pool5",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 100), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool6", "Pool6",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1024), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool7 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool7", "Pool7",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool8 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool8", "Pool8",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 1024), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool9 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool9", "Pool9",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a base HA virtual pool
        VirtualPool vpoolHA = new VirtualPool();
        vpoolHA.setId(URI.create("urn:storageos:VirtualPool:015810fc-0793-4ca1-8281-16adef26dd41:vdc1"));
        vpoolHA.setLabel("vpoolHA");
        vpoolHA.setType("block");
        vpoolHA.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vpoolHA.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        vpoolHA.setMatchedStoragePools(matchedPools);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(varray1.getId().toString());
        vpoolHA.setVirtualArrays(virtualArrays1);
        vpoolHA.setUseMatchedPools(true);
        _dbClient.createObject(vpoolHA);

        // Create a base RP virtual pool
        VirtualPool vpoolRP = new VirtualPool();
        vpoolRP.setId(URI.create("vpoolRP"));
        vpoolRP.setLabel("vpoolRP");
        vpoolRP.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        vpoolRP.setDriveType(SupportedDriveTypes.FC.name());
        vpoolRP.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        matchedPools = new StringSet();
        matchedPools.add(pool7.getId().toString());
        matchedPools.add(pool8.getId().toString());
        matchedPools.add(pool9.getId().toString());
        vpoolRP.setMatchedStoragePools(matchedPools);
        vpoolRP.setUseMatchedPools(true);
        StringSet virtualArrays3 = new StringSet();
        virtualArrays3.add(varray3.getId().toString());
        vpoolRP.setVirtualArrays(virtualArrays3);
        _dbClient.createObject(vpoolRP);

        // Create a RP VPLEX virtual pool
        VirtualPool rpVplexVpool = new VirtualPool();
        rpVplexVpool.setId(URI.create("rpVplexVpool"));
        rpVplexVpool.setLabel("rpVplexVpool");
        rpVplexVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpVplexVpool.setDriveType(SupportedDriveTypes.FC.name());
        rpVplexVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_distributed.name());
        StringMap vavpMap = new StringMap();
        vavpMap.put(varray1.getId().toString(), vpoolHA.getId().toString());
        rpVplexVpool.setHaVarrayVpoolMap(vavpMap);
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(vpoolRP.getId());
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();

        protectionVarray.put(varray3.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpVplexVpool.setProtectionVarraySettings(protectionVarray);
        rpVplexVpool.setRpCopyMode("SYNCHRONOUS");
        rpVplexVpool.setRpRpoType("MINUTES");
        rpVplexVpool.setRpRpoValue(Long.valueOf("5"));
        matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        rpVplexVpool.setMatchedStoragePools(matchedPools);
        rpVplexVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(varray2.getId().toString());
        rpVplexVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(rpVplexVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 2, cg);

        // Run single volume placement: Run 10 times to make sure pool6 never comes up for source and pool9 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray2, project, rpVplexVpool,
                    capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);

            for (RPRecommendation rpRec : rec.getSourceRecommendations()) {
                assertNotNull(rpRec.getVirtualArray());
                assertNotNull(rpRec.getVirtualPool());
                assertNotNull(rpRec.getInternalSiteName());
                assertNotNull(rpRec.getSourceStorageSystem());
                assertNotNull(rpRec.getSourceStoragePool());
                assertTrue("site2".equals(rpRec.getInternalSiteName()));
                assertTrue("vmax2".equals(rpRec.getSourceStorageSystem().toString()));
                assertTrue(("pool4".equals(rpRec.getSourceStoragePool().toString()))
                        || ("pool5".equals(rec.getSourceStoragePool().toString())));

                assertNotNull(rpRec.getVirtualVolumeRecommendation());
                assertTrue("vplex1".equals(rpRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

                assertNotNull(rpRec.getHaRecommendation());
                assertTrue(
                        "vplex1".equals(rpRec.getHaRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
                assertTrue("varray1".equals(rpRec.getHaRecommendation().getVirtualArray().toString()));
                assertTrue("urn:storageos:VirtualPool:015810fc-0793-4ca1-8281-16adef26dd41:vdc1"
                        .equals(rpRec.getHaRecommendation().getVirtualPool().getId().toString()));
                assertTrue("vmax1".equals(rpRec.getHaRecommendation().getSourceStorageSystem().toString()));
                assertTrue(("pool2".equals(rpRec.getHaRecommendation().getSourceStoragePool().toString()))
                        || ("pool1".equals(rpRec.getHaRecommendation().getSourceStoragePool().toString())));

                assertNotNull(rpRec.getTargetRecommendations());
                assertTrue(!rpRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : rpRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getInternalSiteName());
                    assertNotNull(targetRec.getVirtualArray());
                    assertNotNull(targetRec.getVirtualPool());
                    assertNotNull(targetRec.getSourceStorageSystem());
                    assertNotNull(targetRec.getSourceStoragePool());

                    assertTrue("vmax3".equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue("varray3".equals(targetRec.getVirtualArray().toString()));
                    assertTrue("site1".equals(targetRec.getInternalSiteName()));
                    assertTrue("vpoolRP".equals(targetRec.getVirtualPool().getId().toString()));
                    assertTrue(("pool8".equals(targetRec.getSourceStoragePool().toString()))
                            || ("pool7".equals(targetRec.getSourceStoragePool().toString())));
                }
            }

            // Source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            assertNotNull(rec.getSourceJournalRecommendation().getInternalSiteName());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStorageSystem());
            assertNotNull(rec.getSourceJournalRecommendation().getVirtualArray());
            assertNotNull(rec.getSourceJournalRecommendation().getVirtualPool());
            assertTrue("site2".equals(rec.getSourceJournalRecommendation().getInternalSiteName()));
            assertTrue(("pool5".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString()))
                    || ("pool4".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));

            // TargetJournal
            assertNotNull(rec.getTargetJournalRecommendations());
            assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec);
                assertNotNull(targetJournalRec.getInternalSiteName());
                assertNotNull(targetJournalRec.getSourceStoragePool());
                assertNotNull(targetJournalRec.getSourceStorageSystem());
                assertNotNull(targetJournalRec.getVirtualArray());
                assertNotNull(targetJournalRec.getVirtualPool());

                assertTrue("vmax3".equals(targetJournalRec.getSourceStorageSystem().toString()));
                assertTrue("site1".equals(targetJournalRec.getInternalSiteName()));
                assertTrue(("pool8".equals(targetJournalRec.getSourceStoragePool().toString()))
                        || ("pool7".equals(targetJournalRec.getSourceStoragePool().toString())));

            }
            _log.info(String.format("Placement results (#%s) : \n %s", i, rec.toString(_dbClient)));
        }
    }

    /**
     * Metropoint placement - Single remote copy
     */
    @Test
    @Ignore
    public void testPlacementRpMetropointCrr() {

        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax1BE = { "50:BE:BE:BE:BE:BE:BE:00", "50:BE:BE:BE:BE:BE:BE:01" };

        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2BE = { "51:BE:BE:BE:BE:BE:BE:00", "51:BE:BE:BE:BE:BE:BE:01" };

        String[] vmax3FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax3BE = { "52:BE:BE:BE:BE:BE:BE:00", "52:BE:BE:BE:BE:BE:BE:01" };

        String[] vnx1FE = { "60:FE:FE:FE:FE:FE:FE:00", "60:FE:FE:FE:FE:FE:FE:01" };
        String[] vnx1BE = { "60:BE:BE:BE:BE:BE:BE:00", "60:BE:BE:BE:BE:BE:BE:01" };

        String[] vnx2FE = { "61:FE:FE:FE:FE:FE:FE:00", "61:FE:FE:FE:FE:FE:FE:01" };
        String[] vnx2BE = { "61:BE:BE:BE:BE:BE:BE:00", "62:BE:BE:BE:BE:BE:BE:01" };

        String[] vnx3FE = { "62:FE:FE:FE:FE:FE:FE:00", "62:FE:FE:FE:FE:FE:FE:01" };
        String[] vnx3BE = { "62:BE:BE:BE:BE:BE:BE:00", "62:BE:BE:BE:BE:BE:BE:01" };

        String[] rp1FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };
        String[] rp3FE = { "55:FE:FE:FE:FE:FE:FE:00", "55:FE:FE:FE:FE:FE:FE:01" };

        // vplex1 cluster1
        String[] vplex1FE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplex1BE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        // vplex1 cluster2
        String[] vplex2FE = { "FE:FE:FE:FE:FE:FE:FE:02", "FE:FE:FE:FE:FE:FE:FE:03" };
        String[] vplex2BE = { "BE:BE:BE:BE:BE:BE:BE:02", "BE:BE:BE:BE:BE:BE:BE:03" };

        // vplex2 cluster1
        String[] vplex3FE = { "FE:FE:FE:FE:FE:FE:FE:04", "FE:FE:FE:FE:FE:FE:FE:05" };
        String[] vplex3BE = { "BE:BE:BE:BE:BE:BE:BE:04", "BE:BE:BE:BE:BE:BE:BE:05" };

        // Create 3 Virtual Arrays
        VirtualArray srcVarray = PlacementTestUtils.createVirtualArray(_dbClient, "srcVarray");
        VirtualArray haVarray = PlacementTestUtils.createVirtualArray(_dbClient, "haVarray");
        VirtualArray tgtVarray = PlacementTestUtils.createVirtualArray(_dbClient, "tgtVarray");

        // Create Journal Varrays
        VirtualArray srcJournalVarray = PlacementTestUtils.createVirtualArray(_dbClient, "srcJournalVarray");
        VirtualArray haJournalVarray = PlacementTestUtils.createVirtualArray(_dbClient, "haJournalVarray");
        VirtualArray tgtJournalVarray = PlacementTestUtils.createVirtualArray(_dbClient, "tgtJournalVarray");

        // Create 1 Network
        StringSet connVA = new StringSet();
        connVA.add(srcVarray.getId().toString());
        connVA.add(haVarray.getId().toString());
        connVA.add(tgtVarray.getId().toString());
        connVA.add(srcJournalVarray.getId().toString());
        connVA.add(haJournalVarray.getId().toString());
        connVA.add(tgtJournalVarray.getId().toString());

        Network network = PlacementTestUtils.createNetwork(_dbClient, vplex1FE, "VSAN", "FC+BROCADE", connVA);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex3BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex3FE);

        PlacementTestUtils.addEndpoints(_dbClient, network, vplex1BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex1FE);

        PlacementTestUtils.addEndpoints(_dbClient, network, vplex2BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex2FE);

        PlacementTestUtils.addEndpoints(_dbClient, network, rp1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, rp2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, rp3FE);

        PlacementTestUtils.addEndpoints(_dbClient, network, vmax1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax1BE);

        PlacementTestUtils.addEndpoints(_dbClient, network, vmax2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax2BE);

        PlacementTestUtils.addEndpoints(_dbClient, network, vmax3FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax3BE);

        PlacementTestUtils.addEndpoints(_dbClient, network, vnx1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vnx1BE);

        PlacementTestUtils.addEndpoints(_dbClient, network, vnx2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vnx2BE);

        PlacementTestUtils.addEndpoints(_dbClient, network, vnx3FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vnx3BE);

        // Create 3 storage systems
        StorageSystem vmaxStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem vmaxStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");
        StorageSystem vmaxStorageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax3");

        StorageSystem vnxStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vnx", "vnx1");
        StorageSystem vnxStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vnx", "vnx2");
        StorageSystem vnxStorageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vnx", "vnx3");

        // Create 2 VPLEX storage systems
        StorageSystem vplexStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");
        StorageSystem vplexStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex2");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vmaxStorageSystem1, network, vmax1FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax1" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vmaxStorageSystem2, network, vmax2FE[i], haVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax2" + i, "D0+FC0" + i));
        }

        // Create two front-end storage ports VMAX3
        List<StoragePort> vmax3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax3FE.length; i++) {
            vmax3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vmaxStorageSystem3, network, vmax3FE[i], tgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax3" + i, "E0+FC0" + i));
        }

        // Create two front-end storage ports VNX1
        List<StoragePort> vnx1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vnx1FE.length; i++) {
            vnx1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vnxStorageSystem1, network, vnx1FE[i], srcJournalVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvnx1" + i, "C1+FC1" + i));
        }
        // Create two front-end storage ports VNX2
        List<StoragePort> vnx2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vnx2FE.length; i++) {
            vnx2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vnxStorageSystem2, network, vnx2FE[i], haJournalVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvnx2" + i, "D1+FC1" + i));
        }
        // Create two front-end storage ports VNX3
        List<StoragePort> vnx3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vnx1FE.length; i++) {
            vnx3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vnxStorageSystem3, network, vnx3FE[i], tgtJournalVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvnx3" + i, "E1+FC1" + i));
        }

        // Create two back-end storage ports VPLEX1cluster1
        List<StoragePort> fePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex1FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE1-" + (i + 1), "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1cluster1
        List<StoragePort> bePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex1BE[i], srcVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE1-" + (i + 1), "B0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX1cluster2
        List<StoragePort> fePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2FE.length; i++) {
            fePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex2FE[i], haVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE2-" + (i + 1), "F0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1cluster2
        List<StoragePort> bePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2BE.length; i++) {
            bePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex2BE[i], haVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE2-" + (i + 1), "G0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX2cluster1
        List<StoragePort> fePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3FE.length; i++) {
            fePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, network, vplex3FE[i], tgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE3-" + (i + 1), "H0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX2cluster1
        List<StoragePort> bePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3BE.length; i++) {
            bePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, network, vplex3BE[i], tgtVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE3-" + (i + 1), "I0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }
        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }
        initiatorsSiteMap.put("site2", wwnSite2);

        AbstractChangeTrackingSet<String> wwnSite3 = new StringSet();
        for (int i = 0; i < rp3FE.length; i++) {
            wwnSite3.add(rp3FE[i]);
        }
        initiatorsSiteMap.put("site3", wwnSite3);

        StringSet storSystems = new StringSet();

        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex1cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", vnxStorageSystem1.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", vmaxStorageSystem1.getSerialNumber()));

        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex1cluster2"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", vnxStorageSystem2.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", vmaxStorageSystem2.getSerialNumber()));

        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site3", "vplex2cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site3", vnxStorageSystem3.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site3", vmaxStorageSystem3.getSerialNumber()));

        StringSetMap rpVisibleSystems = new StringSetMap();
        StringSet storageIds = new StringSet();
        storageIds.add(vplexStorageSystem1.getId().toString());
        storageIds.add(vmaxStorageSystem1.getId().toString());
        storageIds.add(vnxStorageSystem1.getId().toString());
        rpVisibleSystems.put("site1", storageIds);

        StringSet storageIds2 = new StringSet();
        storageIds2.add(vplexStorageSystem1.getId().toString());
        storageIds2.add(vmaxStorageSystem2.getId().toString());
        storageIds2.add(vnxStorageSystem2.getId().toString());
        rpVisibleSystems.put("site2", storageIds2);

        StringSet storageIds3 = new StringSet();
        storageIds3.add(vplexStorageSystem2.getId().toString());
        storageIds3.add(vmaxStorageSystem3.getId().toString());
        storageIds3.add(vnxStorageSystem3.getId().toString());
        rpVisibleSystems.put("site3", storageIds3);

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");
        siteVolCap.put("site3", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");
        siteVolCnt.put("site3", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", "site3", "IP",
                initiatorsSiteMap, storSystems, rpVisibleSystems, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vplex1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vplex1"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        RPSiteArray rpSiteArray3 = new RPSiteArray();
        rpSiteArray3.setId(URI.create("rsa3"));
        rpSiteArray3.setStorageSystem(URI.create("vplex2"));
        rpSiteArray3.setRpInternalSiteName("site3");
        rpSiteArray3.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray3);

        // Create a storage pool for vmax1
        StoragePool srcPool1 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, vmaxStorageSystem1, "SrcPool1", "SrcPool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool srcPool2 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, vmaxStorageSystem1, "SrcPool2", "SrcPool2",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool srcPool3 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, vmaxStorageSystem1, "SrcPool3", "SrcPool3",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool haPool4 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, vmaxStorageSystem2, "HaPool4", "HaPool4",
                Long.valueOf(1024 * 1024 * 1024), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool haPool5 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, vmaxStorageSystem2, "HaPool5", "HaPool5",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool haPool6 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, vmaxStorageSystem2, "Hapool6", "HaPool6",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool tgtPool7 = PlacementTestUtils.createStoragePool(_dbClient, tgtVarray, vmaxStorageSystem3, "TgtPool7", "TgtPool7",
                Long.valueOf(1024 * 1024 * 30), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool tgtPool8 = PlacementTestUtils.createStoragePool(_dbClient, tgtVarray, vmaxStorageSystem3, "Tgtpool8", "TgtPool8",
                Long.valueOf(1024 * 1024 * 30), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vnx1
        StoragePool sjPool9 = PlacementTestUtils.createStoragePool(_dbClient, srcJournalVarray, vnxStorageSystem1, "Sjpool9", "SjPool9",
                Long.valueOf(1024 * 1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vnx1
        StoragePool hajPool10 = PlacementTestUtils.createStoragePool(_dbClient, haJournalVarray, vnxStorageSystem2, "HaJpool10",
                "HaJPool10",
                Long.valueOf(1024 * 1024 * 1024), Long.valueOf(1024 * 1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vnx1
        StoragePool tjPool11 = PlacementTestUtils.createStoragePool(_dbClient, tgtJournalVarray, vnxStorageSystem3, "Tjpool11", "TjPool11",
                Long.valueOf(1024 * 1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create HA vpool
        // haPool6 should never be selected by placement
        VirtualPool haVpool = new VirtualPool();
        haVpool.setId(URI.create("urn:storageos:VirtualPool:11111111-2222-3333-4444-555555555555:vdc1"));
        haVpool.setLabel("haVpool");
        haVpool.setType("block");
        haVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        haVpool.setDriveType(SupportedDriveTypes.FC.name());
        haVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(haPool4.getId().toString());
        matchedPools.add(haPool5.getId().toString());
        haVpool.setMatchedStoragePools(matchedPools);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(haVarray.getId().toString());
        haVpool.setVirtualArrays(virtualArrays1);
        haVpool.setUseMatchedPools(true);
        _dbClient.createObject(haVpool);

        // Create HA Journal Vpool
        VirtualPool haJournalVpool = new VirtualPool();
        haJournalVpool.setId(URI.create("haJournalVpool"));
        haJournalVpool.setLabel("haJournalVpool");
        haJournalVpool.setType("block");
        haJournalVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        // haJournalVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name()); //Uncomment this line to fail placement.
        // haJournalVpool doesnt have a storagesystem that VPLEX can see
        haJournalVpool.setDriveType(SupportedDriveTypes.FC.name());
        matchedPools = new StringSet();
        matchedPools.add(hajPool10.getId().toString());
        haJournalVpool.setMatchedStoragePools(matchedPools);
        StringSet haJournalVarrays = new StringSet();
        haJournalVarrays.add(haJournalVarray.getId().toString());
        haJournalVpool.setVirtualArrays(haJournalVarrays);
        haJournalVpool.setUseMatchedPools(true);
        _dbClient.createObject(haJournalVpool);

        // Create tgt journal vpool
        VirtualPool tgtJournalVpool = new VirtualPool();
        tgtJournalVpool.setId(URI.create("tgtJournalVpool"));
        tgtJournalVpool.setLabel("tgtJournalVpool");
        tgtJournalVpool.setType("block");
        tgtJournalVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        tgtJournalVpool.setDriveType(SupportedDriveTypes.FC.name());
        matchedPools = new StringSet();
        matchedPools.add(tjPool11.getId().toString());
        tgtJournalVpool.setMatchedStoragePools(matchedPools);
        StringSet tgtJournalVarrays = new StringSet();
        tgtJournalVarrays.add(tgtJournalVarray.getId().toString());
        tgtJournalVpool.setVirtualArrays(tgtJournalVarrays);
        tgtJournalVpool.setUseMatchedPools(true);
        _dbClient.createObject(tgtJournalVpool);

        // Create src journal vpool
        VirtualPool srcJournalVpool = new VirtualPool();
        srcJournalVpool.setId(URI.create("srcJournalVpool"));
        srcJournalVpool.setLabel("srcJournalVpool");
        srcJournalVpool.setType("block");
        srcJournalVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        srcJournalVpool.setDriveType(SupportedDriveTypes.FC.name());
        matchedPools = new StringSet();
        matchedPools.add(sjPool9.getId().toString());
        srcJournalVpool.setMatchedStoragePools(matchedPools);
        StringSet srcJournalVarrays = new StringSet();
        srcJournalVarrays.add(srcJournalVarray.getId().toString());
        srcJournalVpool.setVirtualArrays(srcJournalVarrays);
        srcJournalVpool.setUseMatchedPools(true);
        _dbClient.createObject(srcJournalVpool);

        // Create RP MetroPoint target vpool
        VirtualPool mpTgtVpool = new VirtualPool();
        mpTgtVpool.setId(URI.create("mpTargetVpool"));
        mpTgtVpool.setLabel("mpTargetVpool");
        mpTgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        mpTgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        // mpTgtVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        matchedPools = new StringSet();
        matchedPools.add(tgtPool7.getId().toString());
        matchedPools.add(tgtPool8.getId().toString());
        mpTgtVpool.setMatchedStoragePools(matchedPools);
        mpTgtVpool.setUseMatchedPools(true);
        StringSet tgtVarrays = new StringSet();
        tgtVarrays.add(tgtVarray.getId().toString());
        mpTgtVpool.setVirtualArrays(tgtVarrays);
        _dbClient.createObject(mpTgtVpool);

        // Create a RP VPLEX virtual pool
        // srcPool3 should never be chosen during placement
        VirtualPool mpSrcVpool = new VirtualPool();
        mpSrcVpool.setId(URI.create("mpSrcVpool"));
        mpSrcVpool.setLabel("mpSrcVpool");
        mpSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        mpSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        mpSrcVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_distributed.name());
        matchedPools = new StringSet();
        matchedPools.add(srcPool1.getId().toString());
        matchedPools.add(srcPool2.getId().toString());
        mpSrcVpool.setMatchedStoragePools(matchedPools);
        mpSrcVpool.setUseMatchedPools(true);
        mpSrcVpool.setJournalVarray(srcJournalVarray.getId().toString());
        mpSrcVpool.setJournalVpool(srcJournalVpool.getId().toString());
        mpSrcVpool.setStandbyJournalVarray(haJournalVarray.getId().toString());
        mpSrcVpool.setStandbyJournalVpool(haJournalVpool.getId().toString());
        mpSrcVpool.setJournalSize("2X");
        StringMap vavpMap = new StringMap();
        vavpMap.put(haVarray.getId().toString(), haVpool.getId().toString());
        mpSrcVpool.setHaVarrayVpoolMap(vavpMap);
        mpSrcVpool.setMetroPoint(true);

        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(mpTgtVpool.getId());
        protectionSettings.setId(URI.create("protectionSettings"));
        protectionSettings.setJournalVarray(tgtJournalVarray.getId());
        protectionSettings.setJournalVpool(tgtJournalVpool.getId());
        mpSrcVpool.setHaVarrayConnectedToRp(haVarray.getId().toString());
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();
        protectionVarray.put(tgtVarray.getId().toString(), protectionSettingsList.get(0).getId().toString());
        mpSrcVpool.setProtectionVarraySettings(protectionVarray);
        mpSrcVpool.setRpCopyMode("SYNCHRONOUS");
        mpSrcVpool.setRpRpoType("MINUTES");
        mpSrcVpool.setRpRpoValue(Long.valueOf("5"));
        StringSet srcVarrays = new StringSet();
        srcVarrays.add(srcVarray.getId().toString());
        mpSrcVpool.setVirtualArrays(srcVarrays);
        _dbClient.createObject(mpSrcVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 8, cg);

        // Run single volume placement: Run 10 times to make sure pool6 never comes up for source and pool9 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, srcVarray, project, mpSrcVpool,
                    capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);

            for (RPRecommendation rpRec : rec.getSourceRecommendations()) {
                assertNotNull(rpRec.getVirtualArray());
                assertNotNull(rpRec.getVirtualPool());
                assertNotNull(rpRec.getInternalSiteName());
                assertNotNull(rpRec.getSourceStorageSystem());
                assertNotNull(rpRec.getSourceStoragePool());
                assertTrue("site1".equals(rpRec.getInternalSiteName()));
                assertTrue(vmaxStorageSystem1.getId().toString().equals(rpRec.getSourceStorageSystem().toString()));
                assertTrue((srcPool1.getId().toString().equals(rpRec.getSourceStoragePool().toString()))
                        || (srcPool2.getId().toString().equals(rpRec.getSourceStoragePool().toString()))
                        || (srcPool3.getId().toString().equals(rpRec.getSourceStoragePool().toString())));

                assertNotNull(rpRec.getVirtualVolumeRecommendation());
                assertNotNull(rpRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                assertTrue("vplex1".equals(rpRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

                assertNotNull(rpRec.getHaRecommendation());
                assertNotNull(rpRec.getHaRecommendation().getInternalSiteName());
                assertTrue(
                        "vplex1".equals(rpRec.getHaRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
                assertTrue("site2".equals(rpRec.getHaRecommendation().getInternalSiteName()));
                assertTrue(haVarray.getId().toString().equals(rpRec.getHaRecommendation().getVirtualArray().toString()));
                assertTrue("urn:storageos:VirtualPool:11111111-2222-3333-4444-555555555555:vdc1"
                        .equals(rpRec.getHaRecommendation().getVirtualPool().getId().toString()));
                assertTrue(vmaxStorageSystem2.getId().toString().equals(rpRec.getHaRecommendation().getSourceStorageSystem().toString()));
                assertTrue((haPool4.getId().toString().equals(rpRec.getHaRecommendation().getSourceStoragePool().toString()))
                        || (haPool5.getId().toString().equals(rpRec.getHaRecommendation().getSourceStoragePool().toString()))
                        || (haPool6.getId().toString().equals(rpRec.getHaRecommendation().getSourceStoragePool().toString())));

                assertNotNull(rpRec.getTargetRecommendations());
                assertTrue(!rpRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : rpRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getInternalSiteName());
                    assertNotNull(targetRec.getVirtualArray());
                    assertNotNull(targetRec.getVirtualPool());
                    assertNotNull(targetRec.getSourceStorageSystem());
                    assertNotNull(targetRec.getSourceStoragePool());

                    if (VirtualPool.vPoolSpecifiesHighAvailability(mpTgtVpool)) {
                        assertNotNull(targetRec.getVirtualVolumeRecommendation());
                        assertNotNull(targetRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                        assertTrue(vplexStorageSystem2.getId().toString()
                                .equals(targetRec.getVirtualVolumeRecommendation().getVPlexStorageSystem()));
                    }

                    assertTrue(vmaxStorageSystem3.getId().toString().equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue(tgtVarray.getId().toString().equals(targetRec.getVirtualArray().toString()));
                    assertTrue("site3".equals(targetRec.getInternalSiteName()));
                    assertTrue(mpTgtVpool.getId().toString().equals(targetRec.getVirtualPool().getId().toString()));
                    assertTrue((tgtPool7.getId().toString().equals(targetRec.getSourceStoragePool().toString()))
                            || (tgtPool8.getId().toString().equals(targetRec.getSourceStoragePool().toString())));
                }
            }

            // Source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            assertNotNull(rec.getSourceJournalRecommendation().getInternalSiteName());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStorageSystem());
            assertNotNull(rec.getSourceJournalRecommendation().getVirtualArray());
            assertNotNull(rec.getSourceJournalRecommendation().getVirtualPool());
            assertTrue("site1".equals(rec.getSourceJournalRecommendation().getInternalSiteName()));
            assertTrue(
                    vnxStorageSystem1.getId().toString().equals(rec.getSourceJournalRecommendation().getSourceStorageSystem().toString()));
            assertTrue((sjPool9.getId().toString().equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));
            if (VirtualPool.vPoolSpecifiesHighAvailability(srcJournalVpool)) {
                assertNotNull(rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation());
                assertNotNull(rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem());
                assertTrue(vplexStorageSystem1.getId().toString()
                        .equals(rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem()));
            }

            // Source HA journal
            assertNotNull(rec.getStandbyJournalRecommendation());
            assertNotNull(rec.getStandbyJournalRecommendation().getInternalSiteName());
            assertNotNull(rec.getStandbyJournalRecommendation().getSourceStoragePool());
            assertNotNull(rec.getStandbyJournalRecommendation().getSourceStorageSystem());
            assertNotNull(rec.getStandbyJournalRecommendation().getVirtualArray());
            assertNotNull(rec.getStandbyJournalRecommendation().getVirtualPool());
            assertTrue("site2".equals(rec.getStandbyJournalRecommendation().getInternalSiteName()));
            assertTrue(
                    vnxStorageSystem2.getId().toString().equals(rec.getStandbyJournalRecommendation().getSourceStorageSystem().toString()));
            assertTrue((hajPool10.getId().toString().equals(rec.getStandbyJournalRecommendation().getSourceStoragePool().toString())));
            if (VirtualPool.vPoolSpecifiesHighAvailability(haJournalVpool)) {
                assertNotNull(rec.getStandbyJournalRecommendation().getVirtualVolumeRecommendation());
                assertNotNull(rec.getStandbyJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem());
                assertTrue(vplexStorageSystem1.getId().toString()
                        .equals(rec.getStandbyJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem()));
            }

            // TargetJournal
            assertNotNull(rec.getTargetJournalRecommendations());
            assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec);
                assertNotNull(targetJournalRec.getInternalSiteName());
                assertNotNull(targetJournalRec.getSourceStoragePool());
                assertNotNull(targetJournalRec.getSourceStorageSystem());
                assertNotNull(targetJournalRec.getVirtualArray());
                assertNotNull(targetJournalRec.getVirtualPool());

                assertTrue("site3".equals(targetJournalRec.getInternalSiteName()));
                assertTrue(vnxStorageSystem3.getId().toString().equals(targetJournalRec.getSourceStorageSystem().toString()));
                assertTrue((tjPool11.getId().toString().equals(targetJournalRec.getSourceStoragePool().toString())));

                if (VirtualPool.vPoolSpecifiesHighAvailability(tgtJournalVpool)) {
                    assertNotNull(targetJournalRec.getVirtualVolumeRecommendation());
                    assertNotNull(targetJournalRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                    assertTrue(vplexStorageSystem2.getId().toString()
                            .equals(targetJournalRec.getVirtualVolumeRecommendation().getVPlexStorageSystem()));
                }
            }
            _log.info(String.format("Placement results (#%s) : \n %s", i, rec.toString(_dbClient)));
        }
        _log.info("### PASS ###");
    }

    /*
     * Metropoint placement - 2 local copies, one on each side
     */

    @Test
    @Ignore
    public void testPlacementRpMetropointCdp() {

        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax1BE = { "50:BE:BE:BE:BE:BE:BE:00", "50:BE:BE:BE:BE:BE:BE:01" };

        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2BE = { "51:BE:BE:BE:BE:BE:BE:00", "51:BE:BE:BE:BE:BE:BE:01" };

        String[] vmax3FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax3BE = { "52:BE:BE:BE:BE:BE:BE:00", "52:BE:BE:BE:BE:BE:BE:01" };

        String[] vnx1FE = { "60:FE:FE:FE:FE:FE:FE:00", "60:FE:FE:FE:FE:FE:FE:01" };
        String[] vnx1BE = { "60:BE:BE:BE:BE:BE:BE:00", "60:BE:BE:BE:BE:BE:BE:01" };

        String[] vnx2FE = { "61:FE:FE:FE:FE:FE:FE:00", "61:FE:FE:FE:FE:FE:FE:01" };
        String[] vnx2BE = { "61:BE:BE:BE:BE:BE:BE:00", "62:BE:BE:BE:BE:BE:BE:01" };

        String[] vnx3FE = { "62:FE:FE:FE:FE:FE:FE:00", "62:FE:FE:FE:FE:FE:FE:01" };
        String[] vnx3BE = { "62:BE:BE:BE:BE:BE:BE:00", "62:BE:BE:BE:BE:BE:BE:01" };

        String[] rp1FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };
        String[] rp3FE = { "55:FE:FE:FE:FE:FE:FE:00", "55:FE:FE:FE:FE:FE:FE:01" };

        // vplex1 cluster1
        String[] vplex11FE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplex11BE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        // vplex1 cluster2
        String[] vplex12FE = { "FE:FE:FE:FE:FE:FE:FE:02", "FE:FE:FE:FE:FE:FE:FE:03" };
        String[] vplex12BE = { "BE:BE:BE:BE:BE:BE:BE:02", "BE:BE:BE:BE:BE:BE:BE:03" };

        // vplex2 cluster1
        String[] vplex21FE = { "FE:FE:FE:FE:FE:FE:FE:04", "FE:FE:FE:FE:FE:FE:FE:05" };
        String[] vplex21BE = { "BE:BE:BE:BE:BE:BE:BE:04", "BE:BE:BE:BE:BE:BE:BE:05" };

        // Create 3 Virtual Arrays
        VirtualArray srcVarray = PlacementTestUtils.createVirtualArray(_dbClient, "srcVarray");
        VirtualArray haVarray = PlacementTestUtils.createVirtualArray(_dbClient, "haVarray");
        VirtualArray activeTgtVarray = PlacementTestUtils.createVirtualArray(_dbClient, "activeTgtVarray");
        VirtualArray standbyTgtVarray = PlacementTestUtils.createVirtualArray(_dbClient, "standbyTgtVarray");

        // Create Journal Varrays
        VirtualArray srcJournalVarray = PlacementTestUtils.createVirtualArray(_dbClient, "srcJournalVarray");
        VirtualArray haJournalVarray = PlacementTestUtils.createVirtualArray(_dbClient, "haJournalVarray");
        VirtualArray tgtJournalVarray = PlacementTestUtils.createVirtualArray(_dbClient, "tgtJournalVarray");

        // Create network for VPLEX source side
        StringSet sourceConnectedVa = new StringSet();
        sourceConnectedVa.add(srcVarray.getId().toString());
        sourceConnectedVa.add(activeTgtVarray.getId().toString());
        sourceConnectedVa.add(srcJournalVarray.getId().toString());
        // create network for VPLEX HA side
        StringSet haConnectedVa = new StringSet();
        haConnectedVa.add(haVarray.getId().toString());
        haConnectedVa.add(standbyTgtVarray.getId().toString());
        haConnectedVa.add(haJournalVarray.getId().toString());

        Network sourceNetwork = PlacementTestUtils.createNetwork(_dbClient, vplex11FE, "VSAN", "FC+BROCADE", sourceConnectedVa);
        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vplex11BE);

        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, rp1FE);
        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, rp3FE);

        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vmax1FE);
        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vmax1BE);

        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vmax3FE);
        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vmax3BE);

        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vnx1FE);
        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vnx1BE);

        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vnx3FE);
        PlacementTestUtils.addEndpoints(_dbClient, sourceNetwork, vnx3BE);

        Network haNetwork = PlacementTestUtils.createNetwork(_dbClient, vplex12FE, "VSAN", "FC+BROCADE", haConnectedVa);
        PlacementTestUtils.addEndpoints(_dbClient, haNetwork, vplex12BE);

        PlacementTestUtils.addEndpoints(_dbClient, haNetwork, rp2FE);

        PlacementTestUtils.addEndpoints(_dbClient, haNetwork, vmax2FE);
        PlacementTestUtils.addEndpoints(_dbClient, haNetwork, vmax2BE);

        PlacementTestUtils.addEndpoints(_dbClient, haNetwork, vnx2FE);
        PlacementTestUtils.addEndpoints(_dbClient, haNetwork, vnx2BE);

        // Create 3 storage systems
        StorageSystem vmaxStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem vmaxStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");
        StorageSystem vmaxStorageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax3");

        StorageSystem vnxStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vnx", "vnx1");
        StorageSystem vnxStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vnx", "vnx2");
        StorageSystem vnxStorageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vnx", "vnx3");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vmaxStorageSystem1, sourceNetwork, vmax1FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax1" + i, "FE0+FC0" + i));
        }

        // Create two front-end storage ports VMAX1
        vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1BE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vmaxStorageSystem1, sourceNetwork, vmax1BE[i], srcVarray,
                    StoragePort.PortType.backend.name(), "portGroupvmax1" + i, "BE0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vmaxStorageSystem2, haNetwork, vmax2FE[i], haVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax2" + i, "D0+FC0" + i));
        }

        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vmaxStorageSystem2, haNetwork, vmax2FE[i], standbyTgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax2" + i, "D0+FC0" + i));
        }

        // Create two front-end storage ports VMAX3
        List<StoragePort> vmax3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax3FE.length; i++) {
            vmax3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vmaxStorageSystem3, sourceNetwork, vmax3FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax3" + i, "E0+FC0" + i));
        }

        // Create two front-end storage ports VNX1
        List<StoragePort> vnx1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vnx1FE.length; i++) {
            vnx1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vnxStorageSystem1, sourceNetwork, vnx1FE[i], srcJournalVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvnx1" + i, "C1+FC1" + i));
        }
        // Create two front-end storage ports VNX2
        List<StoragePort> vnx2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vnx2FE.length; i++) {
            vnx2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vnxStorageSystem2, sourceNetwork, vnx2FE[i], haJournalVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvnx2" + i, "D1+FC1" + i));
        }
        // Create two front-end storage ports VNX3
        List<StoragePort> vnx3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vnx1FE.length; i++) {
            vnx3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, vnxStorageSystem3, sourceNetwork, vnx3FE[i], tgtJournalVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvnx3" + i, "E1+FC1" + i));
        }

        // Create 2 VPLEX storage systems
        StorageSystem vplexStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");
        StorageSystem vplexStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex2");

        // Create two back-end storage ports VPLEX1cluster1
        List<StoragePort> fePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex11FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, sourceNetwork, vplex11FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE1-" + (i + 1), "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1cluster1
        List<StoragePort> bePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex11BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, sourceNetwork, vplex11BE[i], srcVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE1-" + (i + 1), "B0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX1cluster2
        List<StoragePort> fePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex12FE.length; i++) {
            fePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, haNetwork, vplex12FE[i], haVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE2-" + (i + 1), "F0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1cluster2
        List<StoragePort> bePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex12BE.length; i++) {
            bePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, haNetwork, vplex12BE[i], haVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE2-" + (i + 1), "G0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX2cluster1
        // List<StoragePort> fePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex21FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, sourceNetwork, vplex21FE[i], activeTgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE3-" + (i + 1), "H0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX2cluster1
        // List<StoragePort> bePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex21BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, sourceNetwork, vplex21BE[i], activeTgtVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE3-" + (i + 1), "I0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }
        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }
        initiatorsSiteMap.put("site2", wwnSite2);

        AbstractChangeTrackingSet<String> wwnSite3 = new StringSet();
        for (int i = 0; i < rp3FE.length; i++) {
            wwnSite3.add(rp3FE[i]);
        }
        initiatorsSiteMap.put("site3", wwnSite3);

        StringSet storSystems = new StringSet();

        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex1cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", vnxStorageSystem1.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", vmaxStorageSystem1.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", vnxStorageSystem3.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", vmaxStorageSystem3.getSerialNumber()));

        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex1cluster2"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", vnxStorageSystem2.getSerialNumber()));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", vmaxStorageSystem2.getSerialNumber()));

        StringSetMap rpVisibleSystems = new StringSetMap();
        StringSet storageIds = new StringSet();
        storageIds.add(vplexStorageSystem1.getId().toString());
        storageIds.add(vmaxStorageSystem1.getId().toString());
        storageIds.add(vnxStorageSystem1.getId().toString());
        storageIds.add(vmaxStorageSystem3.getId().toString());
        storageIds.add(vnxStorageSystem3.getId().toString());
        rpVisibleSystems.put("site1", storageIds);

        StringSet storageIds2 = new StringSet();
        storageIds2.add(vplexStorageSystem1.getId().toString());
        storageIds2.add(vmaxStorageSystem2.getId().toString());
        storageIds2.add(vnxStorageSystem2.getId().toString());
        rpVisibleSystems.put("site2", storageIds2);

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", "site3", "IP",
                initiatorsSiteMap, storSystems, rpVisibleSystems, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vplex1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vplex1"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        RPSiteArray rpSiteArray3 = new RPSiteArray();
        rpSiteArray3.setId(URI.create("rsa3"));
        rpSiteArray3.setStorageSystem(URI.create("vplex2"));
        rpSiteArray3.setRpInternalSiteName("site3");
        rpSiteArray3.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray3);

        // Create a storage pool for vmax1
        StoragePool srcPool1 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, vmaxStorageSystem1, "SrcPool1", "SrcPool1",
                Long.valueOf(SIZE_GB * 75), Long.valueOf(SIZE_GB * 150), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool srcPool2 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, vmaxStorageSystem1, "SrcPool2", "SrcPool2",
                Long.valueOf(SIZE_GB * 75), Long.valueOf(SIZE_GB * 150), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool srcPool3 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, vmaxStorageSystem1, "SrcPool3", "SrcPool3",
                Long.valueOf(SIZE_GB * 200), Long.valueOf(SIZE_GB * 200), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool haPool4 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, vmaxStorageSystem2, "HaPool4", "HaPool4",
                Long.valueOf(SIZE_GB * 1024), Long.valueOf(SIZE_GB * 1024 * 1024), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool haPool5 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, vmaxStorageSystem2, "HaPool5", "HaPool5",
                Long.valueOf(SIZE_GB * 300), Long.valueOf(SIZE_GB * 300), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        // StoragePool haPool6 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, vmaxStorageSystem2, "Hapool6", "HaPool6",
        // Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
        // StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        StoragePool haPool6 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, vmaxStorageSystem3, "Hapool6", "HaPool6",
                Long.valueOf(SIZE_GB * 100), Long.valueOf(SIZE_GB * 300), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());
        // Create a storage pool for vmax3
        StoragePool tgtPool7 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, vmaxStorageSystem3, "TgtPool7", "TgtPool7",
                Long.valueOf(SIZE_GB * 300), Long.valueOf(SIZE_GB * 300), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool tgtPool8 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, vmaxStorageSystem3, "Tgtpool8", "TgtPool8",
                Long.valueOf(SIZE_GB * 300), Long.valueOf(SIZE_GB * 300), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vnx1
        StoragePool sjPool9 = PlacementTestUtils.createStoragePool(_dbClient, srcJournalVarray, vnxStorageSystem1, "Sjpool9", "SjPool9",
                Long.valueOf(SIZE_GB * 1024), Long.valueOf(SIZE_GB * 1024 * 1024), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vnx1
        StoragePool hajPool10 = PlacementTestUtils.createStoragePool(_dbClient, haJournalVarray, vnxStorageSystem2, "HaJpool10",
                "HaJPool10",
                Long.valueOf(SIZE_GB * 1024), Long.valueOf(SIZE_GB * 1024 * 1024), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vnx1
        StoragePool tjPool11 = PlacementTestUtils.createStoragePool(_dbClient, tgtJournalVarray, vnxStorageSystem3, "Tjpool11", "TjPool11",
                Long.valueOf(SIZE_GB * 1024), Long.valueOf(SIZE_GB * 1024 * 1024), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create HA vpool
        // haPool6 should never be selected by placement
        VirtualPool haVpool = new VirtualPool();
        haVpool.setId(URI.create("urn:storageos:VirtualPool:11111111-2222-3333-4444-555555555555:vdc1"));
        haVpool.setLabel("haVpool");
        haVpool.setType("block");
        haVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        haVpool.setDriveType(SupportedDriveTypes.FC.name());
        haVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(haPool4.getId().toString());
        matchedPools.add(haPool5.getId().toString());
        haVpool.setMatchedStoragePools(matchedPools);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(haVarray.getId().toString());
        haVpool.setVirtualArrays(virtualArrays1);
        haVpool.setUseMatchedPools(true);
        _dbClient.createObject(haVpool);

        // Create HA Journal Vpool
        VirtualPool haJournalVpool = new VirtualPool();
        haJournalVpool.setId(URI.create("haJournalVpool"));
        haJournalVpool.setLabel("haJournalVpool");
        haJournalVpool.setType("block");
        haJournalVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        // haJournalVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name()); //Uncomment this line to fail placement.
        // haJournalVpool doesnt have a storagesystem that VPLEX can see
        haJournalVpool.setDriveType(SupportedDriveTypes.FC.name());
        matchedPools = new StringSet();
        matchedPools.add(hajPool10.getId().toString());
        haJournalVpool.setMatchedStoragePools(matchedPools);
        StringSet haJournalVarrays = new StringSet();
        haJournalVarrays.add(haJournalVarray.getId().toString());
        haJournalVpool.setVirtualArrays(haJournalVarrays);
        haJournalVpool.setUseMatchedPools(true);
        _dbClient.createObject(haJournalVpool);

        // Create tgt journal vpool
        VirtualPool tgtJournalVpool = new VirtualPool();
        tgtJournalVpool.setId(URI.create("tgtJournalVpool"));
        tgtJournalVpool.setLabel("tgtJournalVpool");
        tgtJournalVpool.setType("block");
        tgtJournalVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        tgtJournalVpool.setDriveType(SupportedDriveTypes.FC.name());
        matchedPools = new StringSet();
        matchedPools.add(tjPool11.getId().toString());
        tgtJournalVpool.setMatchedStoragePools(matchedPools);
        StringSet tgtJournalVarrays = new StringSet();
        tgtJournalVarrays.add(tgtJournalVarray.getId().toString());
        tgtJournalVpool.setVirtualArrays(tgtJournalVarrays);
        tgtJournalVpool.setUseMatchedPools(true);
        _dbClient.createObject(tgtJournalVpool);

        // Create src journal vpool
        VirtualPool srcJournalVpool = new VirtualPool();
        srcJournalVpool.setId(URI.create("srcJournalVpool"));
        srcJournalVpool.setLabel("srcJournalVpool");
        srcJournalVpool.setType("block");
        srcJournalVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        srcJournalVpool.setDriveType(SupportedDriveTypes.FC.name());
        matchedPools = new StringSet();
        matchedPools.add(sjPool9.getId().toString());
        srcJournalVpool.setMatchedStoragePools(matchedPools);
        StringSet srcJournalVarrays = new StringSet();
        srcJournalVarrays.add(srcJournalVarray.getId().toString());
        srcJournalVpool.setVirtualArrays(srcJournalVarrays);
        srcJournalVpool.setUseMatchedPools(true);
        _dbClient.createObject(srcJournalVpool);

        // Create RP MetroPoint active target vpool
        VirtualPool mpActiveTgtVpool = new VirtualPool();
        mpActiveTgtVpool.setId(URI.create("mpTargetVpool"));
        mpActiveTgtVpool.setLabel("mpTargetVpool");
        mpActiveTgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        mpActiveTgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        mpActiveTgtVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        matchedPools = new StringSet();
        matchedPools.add(tgtPool7.getId().toString());
        matchedPools.add(tgtPool8.getId().toString());
        mpActiveTgtVpool.setMatchedStoragePools(matchedPools);
        mpActiveTgtVpool.setUseMatchedPools(true);
        StringSet activeTgtVarrays = new StringSet();
        activeTgtVarrays.add(srcVarray.getId().toString());
        mpActiveTgtVpool.setVirtualArrays(activeTgtVarrays);
        _dbClient.createObject(mpActiveTgtVpool);

        // Create RP MetroPoint standby target vpool
        VirtualPool mpStandbyTgtVpool = new VirtualPool();
        mpStandbyTgtVpool.setId(URI.create("mpStandbyTargetVpool"));
        mpStandbyTgtVpool.setLabel("mpStandbyTargetVpool");
        mpStandbyTgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        mpStandbyTgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        // mpTgtVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        matchedPools = new StringSet();
        // matchedPools.add(tgtPool7.getId().toString());
        matchedPools.add(haPool4.getId().toString());
        mpStandbyTgtVpool.setMatchedStoragePools(matchedPools);
        mpStandbyTgtVpool.setUseMatchedPools(true);
        StringSet standbyTgtVarrays = new StringSet();
        standbyTgtVarrays.add(haVarray.getId().toString());
        mpStandbyTgtVpool.setVirtualArrays(standbyTgtVarrays);
        _dbClient.createObject(mpStandbyTgtVpool);

        // Create a RP VPLEX virtual pool
        // srcPool3 should never be chosen during placement
        VirtualPool mpSrcVpool = new VirtualPool();
        mpSrcVpool.setId(URI.create("mpSrcVpool"));
        mpSrcVpool.setLabel("mpSrcVpool");
        mpSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        mpSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        mpSrcVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_distributed.name());
        matchedPools = new StringSet();
        matchedPools.add(srcPool1.getId().toString());
        matchedPools.add(srcPool2.getId().toString());
        mpSrcVpool.setMatchedStoragePools(matchedPools);
        mpSrcVpool.setUseMatchedPools(true);
        mpSrcVpool.setJournalVarray(srcJournalVarray.getId().toString());
        mpSrcVpool.setJournalVpool(srcJournalVpool.getId().toString());
        mpSrcVpool.setStandbyJournalVarray(haJournalVarray.getId().toString());
        mpSrcVpool.setStandbyJournalVpool(haJournalVpool.getId().toString());
        mpSrcVpool.setJournalSize("2X");
        StringMap vavpMap = new StringMap();
        vavpMap.put(haVarray.getId().toString(), haVpool.getId().toString());
        mpSrcVpool.setHaVarrayVpoolMap(vavpMap);
        mpSrcVpool.setMetroPoint(true);

        VpoolProtectionVarraySettings activeProtectionSettings = new VpoolProtectionVarraySettings();
        activeProtectionSettings.setVirtualPool(mpActiveTgtVpool.getId());
        activeProtectionSettings.setJournalVpool(mpActiveTgtVpool.getId());
        activeProtectionSettings.setId(URI.create("activeProtectionSettings"));
        _dbClient.createObject(activeProtectionSettings);

        StringMap protectionVarray = new StringMap();
        protectionVarray.put(srcVarray.getId().toString(), activeProtectionSettings.getId().toString());

        VpoolProtectionVarraySettings standbyProtectionSettings = new VpoolProtectionVarraySettings();
        standbyProtectionSettings.setVirtualPool(mpStandbyTgtVpool.getId());
        standbyProtectionSettings.setJournalVpool(mpStandbyTgtVpool.getId());
        standbyProtectionSettings.setId(URI.create("standbyProtectionSettings"));
        _dbClient.createObject(standbyProtectionSettings);

        protectionVarray.put(haVarray.getId().toString(), standbyProtectionSettings.getId().toString());
        mpSrcVpool.setProtectionVarraySettings(protectionVarray);

        mpSrcVpool.setRpCopyMode("SYNCHRONOUS");
        mpSrcVpool.setRpRpoType("MINUTES");
        mpSrcVpool.setRpRpoValue(Long.valueOf("5"));
        StringSet srcVarrays = new StringSet();
        srcVarrays.add(srcVarray.getId().toString());
        mpSrcVpool.setVirtualArrays(srcVarrays);
        _dbClient.createObject(mpSrcVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("10GB", 13, cg);

        // Run single volume placement: Run 10 times to make sure pool6 never comes up for source and pool9 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, srcVarray, project, mpSrcVpool,
                    capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);

            for (RPRecommendation rpRec : rec.getSourceRecommendations()) {
                assertNotNull(rpRec.getVirtualArray());
                assertNotNull(rpRec.getVirtualPool());
                assertNotNull(rpRec.getInternalSiteName());
                assertNotNull(rpRec.getSourceStorageSystem());
                assertNotNull(rpRec.getSourceStoragePool());
                assertTrue("site1".equals(rpRec.getInternalSiteName()));
                assertTrue(vmaxStorageSystem1.getId().toString().equals(rpRec.getSourceStorageSystem().toString()));
                assertTrue((srcPool1.getId().toString().equals(rpRec.getSourceStoragePool().toString())) ||
                        (srcPool2.getId().toString().equals(rpRec.getSourceStoragePool().toString())) ||
                        (srcPool3.getId().toString().equals(rpRec.getSourceStoragePool().toString())));

                assertNotNull(rpRec.getVirtualVolumeRecommendation());
                assertNotNull(rpRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                assertTrue("vplex1".equals(rpRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

                assertNotNull(rpRec.getHaRecommendation());
                assertNotNull(rpRec.getHaRecommendation().getInternalSiteName());
                assertTrue(
                        "vplex1".equals(rpRec.getHaRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
                assertTrue("site2".equals(rpRec.getHaRecommendation().getInternalSiteName()));
                assertTrue(haVarray.getId().toString().equals(rpRec.getHaRecommendation().getVirtualArray().toString()));
                assertTrue("urn:storageos:VirtualPool:11111111-2222-3333-4444-555555555555:vdc1".equals(
                        rpRec.getHaRecommendation().getVirtualPool().getId().toString()));
                assertTrue(vmaxStorageSystem2.getId().toString().equals(rpRec.getHaRecommendation().getSourceStorageSystem().toString()));
                assertTrue((haPool4.getId().toString().equals(rpRec.getHaRecommendation().getSourceStoragePool().toString())) ||
                        (haPool5.getId().toString().equals(rpRec.getHaRecommendation().getSourceStoragePool().toString())) ||
                        (haPool6.getId().toString().equals(rpRec.getHaRecommendation().getSourceStoragePool().toString())));

                assertNotNull(rpRec.getTargetRecommendations());
                assertTrue(!rpRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : rpRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getInternalSiteName());
                    assertNotNull(targetRec.getVirtualArray());
                    assertNotNull(targetRec.getVirtualPool());
                    assertNotNull(targetRec.getSourceStorageSystem());
                    assertNotNull(targetRec.getSourceStoragePool());

                    if (VirtualPool.vPoolSpecifiesHighAvailability(mpActiveTgtVpool)) {
                        assertNotNull(targetRec.getVirtualVolumeRecommendation());
                        assertNotNull(targetRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                        assertTrue(vplexStorageSystem1.getId().toString().equals(
                                targetRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
                        assertTrue(vmaxStorageSystem3.getId().toString().equals(targetRec.getSourceStorageSystem().toString()));
                        assertTrue(srcVarray.getId().toString().equals(targetRec.getVirtualArray().toString()));
                        assertTrue("site1".equals(targetRec.getInternalSiteName()));
                        assertTrue(mpActiveTgtVpool.getId().toString().equals(targetRec.getVirtualPool().getId().toString()));
                        assertTrue((tgtPool7.getId().toString().equals(targetRec.getSourceStoragePool().toString())) ||
                                (tgtPool8.getId().toString().equals(targetRec.getSourceStoragePool().toString())));
                    }

                    if (VirtualPool.vPoolSpecifiesHighAvailability(mpStandbyTgtVpool)) {
                        assertNotNull(targetRec.getVirtualVolumeRecommendation());
                        assertNotNull(targetRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                        assertTrue(vplexStorageSystem2.getId().toString().equals(
                                targetRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
                        assertTrue(vmaxStorageSystem3.getId().toString().equals(targetRec.getSourceStorageSystem().toString()));
                        assertTrue(standbyTgtVarray.getId().toString().equals(targetRec.getVirtualArray().toString()));
                        assertTrue("site2".equals(targetRec.getInternalSiteName()));
                        assertTrue(mpActiveTgtVpool.getId().toString().equals(targetRec.getVirtualPool().getId().toString()));
                        assertTrue((tgtPool7.getId().toString().equals(targetRec.getSourceStoragePool().toString())) ||
                                (tgtPool8.getId().toString().equals(targetRec.getSourceStoragePool().toString())));
                    }
                }
            }

            // Source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            assertNotNull(rec.getSourceJournalRecommendation().getInternalSiteName());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
            assertNotNull(rec.getSourceJournalRecommendation().getSourceStorageSystem());
            assertNotNull(rec.getSourceJournalRecommendation().getVirtualArray());
            assertNotNull(rec.getSourceJournalRecommendation().getVirtualPool());
            assertTrue("site1".equals(rec.getSourceJournalRecommendation().getInternalSiteName()));
            assertTrue(
                    vnxStorageSystem1.getId().toString().equals(rec.getSourceJournalRecommendation().getSourceStorageSystem().toString()));
            assertTrue((sjPool9.getId().toString().equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));
            if (VirtualPool.vPoolSpecifiesHighAvailability(srcJournalVpool)) {
                assertNotNull(rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation());
                assertNotNull(rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem());
                assertTrue(vplexStorageSystem1.getId().toString().equals(
                        rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem()));
            }

            // Source HA journal
            assertNotNull(rec.getStandbyJournalRecommendation());
            assertNotNull(rec.getStandbyJournalRecommendation().getInternalSiteName());
            assertNotNull(rec.getStandbyJournalRecommendation().getSourceStoragePool());
            assertNotNull(rec.getStandbyJournalRecommendation().getSourceStorageSystem());
            assertNotNull(rec.getStandbyJournalRecommendation().getVirtualArray());
            assertNotNull(rec.getStandbyJournalRecommendation().getVirtualPool());
            assertTrue("site2".equals(rec.getStandbyJournalRecommendation().getInternalSiteName()));
            assertTrue(
                    vnxStorageSystem2.getId().toString().equals(rec.getStandbyJournalRecommendation().getSourceStorageSystem().toString()));
            assertTrue((hajPool10.getId().toString().equals(rec.getStandbyJournalRecommendation().getSourceStoragePool().toString())));
            if (VirtualPool.vPoolSpecifiesHighAvailability(haJournalVpool)) {
                assertNotNull(rec.getStandbyJournalRecommendation().getVirtualVolumeRecommendation());
                assertNotNull(rec.getStandbyJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem());
                assertTrue(vplexStorageSystem1.getId().toString().equals(
                        rec.getStandbyJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem()));
            }

            // TargetJournal
            assertNotNull(rec.getTargetJournalRecommendations());
            assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec);
                assertNotNull(targetJournalRec.getInternalSiteName());
                assertNotNull(targetJournalRec.getSourceStoragePool());
                assertNotNull(targetJournalRec.getSourceStorageSystem());
                assertNotNull(targetJournalRec.getVirtualArray());
                assertNotNull(targetJournalRec.getVirtualPool());

                if (targetJournalRec.getVirtualArray().toString().equals(srcVarray.getId().toString())) {
                    assertTrue("site1".equals(targetJournalRec.getInternalSiteName()));
                    assertTrue(mpActiveTgtVpool.getId().toString().equals(targetJournalRec.getVirtualPool().getId().toString()));
                    assertTrue((tgtPool7.getId().toString().equals(targetJournalRec.getSourceStoragePool().toString())) ||
                            haPool4.getId().toString().equals(targetJournalRec.getSourceStoragePool().toString()));
                    assertTrue(vmaxStorageSystem3.getId().toString().equals(targetJournalRec.getSourceStorageSystem().toString()));

                    if (VirtualPool.vPoolSpecifiesHighAvailability(mpActiveTgtVpool)) {
                        assertNotNull(targetJournalRec.getVirtualVolumeRecommendation());
                        assertNotNull(targetJournalRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                        assertTrue(vplexStorageSystem1.getId().toString().equals(
                                targetJournalRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

                    }
                }

                if (targetJournalRec.getVirtualArray().toString().equals(haVarray.getId().toString())) {
                    assertTrue("site2".equals(targetJournalRec.getInternalSiteName()));
                    assertTrue(mpStandbyTgtVpool.getId().toString().equals(targetJournalRec.getVirtualPool().getId().toString()));
                    assertTrue((haPool4.getId().toString().equals(targetJournalRec.getSourceStoragePool().toString())));
                    assertTrue(vmaxStorageSystem2.getId().toString().equals(targetJournalRec.getSourceStorageSystem().toString()));

                    if (VirtualPool.vPoolSpecifiesHighAvailability(mpStandbyTgtVpool)) {
                        assertNotNull(targetJournalRec.getVirtualVolumeRecommendation());
                        assertNotNull(targetJournalRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
                        assertTrue(vplexStorageSystem2.getId().toString().equals(
                                targetJournalRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

                    }
                }
            }
            _log.info(String.format("Placement results (#%s) : \n %s", i, rec.toString(_dbClient)));
        }
        _log.info("### PASS ###");
    }

    /**
     * RP VPLEX placement -- placement decision based on RP array visibility
     */
    @Test
    @Ignore
    public void testPlacementRpVplexAdvancedSite1toSite2() {

        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax3FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };

        String[] vplex1FE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplex1BE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        String[] vplex2FE = { "FE:FE:FE:FE:FE:FE:FE:02", "FE:FE:FE:FE:FE:FE:FE:03" };
        String[] vplex2BE = { "BE:BE:BE:BE:BE:BE:BE:02", "BE:BE:BE:BE:BE:BE:BE:03" };

        String[] vplex3FE = { "FE:FE:FE:FE:FE:FE:FE:04", "FE:FE:FE:FE:FE:FE:FE:05" };
        String[] vplex3BE = { "BE:BE:BE:BE:BE:BE:BE:04", "BE:BE:BE:BE:BE:BE:BE:05" };

        // Create 3 Virtual Arrays
        VirtualArray varray1 = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");
        VirtualArray varray2 = PlacementTestUtils.createVirtualArray(_dbClient, "varray2");
        VirtualArray varray3 = PlacementTestUtils.createVirtualArray(_dbClient, "varray3");

        // Create 1 Network
        StringSet connVA = new StringSet();
        connVA.add(varray1.getId().toString());
        connVA.add(varray2.getId().toString());
        connVA.add(varray3.getId().toString());
        Network network = PlacementTestUtils.createNetwork(_dbClient, vplex1FE, "VSAN", "FC+BROCADE", connVA);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex3FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex1BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex2BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex3BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, rp1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, rp2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax3FE);

        // Create 3 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");
        StorageSystem storageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax3");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network, vmax1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupvmax1" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network, vmax2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupvmax2" + i, "D0+FC0" + i));
        }

        // Create two front-end storage ports VMAX3
        List<StoragePort> vmax3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax3FE.length; i++) {
            vmax3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem3, network, vmax3FE[i], varray3,
                    StoragePort.PortType.frontend.name(), "portGroupvmax3" + i, "E0+FC0" + i));
        }

        // Create 2 VPLEX storage systems
        StorageSystem vplexStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");
        StorageSystem vplexStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex2");

        // Create two front-end storage ports VPLEX1
        List<StoragePort> fePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupFE1-" + (i + 1), "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1
        List<StoragePort> bePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex1BE[i], varray1,
                    StoragePort.PortType.backend.name(), "portGroupBE1-" + (i + 1), "B0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX2
        List<StoragePort> fePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2FE.length; i++) {
            fePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex2FE[i], varray2,
                    StoragePort.PortType.frontend.name(), "portGroupFE2-" + (i + 1), "F0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX2
        List<StoragePort> bePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2BE.length; i++) {
            bePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex2BE[i], varray2,
                    StoragePort.PortType.backend.name(), "portGroupBE2-" + (i + 1), "G0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX3
        List<StoragePort> fePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3FE.length; i++) {
            fePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, network, vplex3FE[i], varray3,
                    StoragePort.PortType.frontend.name(), "portGroupFE3-" + (i + 1), "H0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX3
        List<StoragePort> bePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3BE.length; i++) {
            bePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, network, vplex3BE[i], varray3,
                    StoragePort.PortType.backend.name(), "portGroupBE3-" + (i + 1), "I0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);

        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex1cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex1cluster2"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex2cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex2cluster2"));

        StringSetMap rpVisibleSystems = new StringSetMap();
        StringSet storageIds = new StringSet();
        storageIds.add(vplexStorageSystem1.getId().toString());
        rpVisibleSystems.put("site1", storageIds);
        StringSet storageIds2 = new StringSet();
        storageIds2.add(vplexStorageSystem2.getId().toString());
        rpVisibleSystems.put("site2", storageIds2);

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, rpVisibleSystems, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vplex1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vplex2"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        // Create a storage pool for vmax1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool1", "Pool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool2", "Pool2",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool3", "Pool3",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool4", "Pool4",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool5", "Pool5",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 50), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, varray2, storageSystem2, "pool6", "Pool6",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool7 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool7", "Pool7",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool8 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool8", "Pool8",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool9 = PlacementTestUtils.createStoragePool(_dbClient, varray3, storageSystem3, "pool9", "Pool9",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create HA virtual pool
        VirtualPool haVpool = new VirtualPool();
        haVpool.setId(URI.create(haVpoolUri));
        haVpool.setLabel("haVpool");
        haVpool.setType("block");
        haVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        haVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        haVpool.setMatchedStoragePools(matchedPools);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(varray1.getId().toString());
        haVpool.setVirtualArrays(virtualArrays1);
        haVpool.setUseMatchedPools(true);
        _dbClient.createObject(haVpool);

        // Create RP target vpool
        VirtualPool rpTgtVpool = new VirtualPool();
        rpTgtVpool.setId(URI.create("rpTgtVpool"));
        rpTgtVpool.setLabel("rpTgtVpool");
        rpTgtVpool.setType("block");
        rpTgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpTgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        rpTgtVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        matchedPools = new StringSet();
        matchedPools.add(pool7.getId().toString());
        matchedPools.add(pool8.getId().toString());
        matchedPools.add(pool9.getId().toString());
        rpTgtVpool.setMatchedStoragePools(matchedPools);
        rpTgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays3 = new StringSet();
        virtualArrays3.add(varray3.getId().toString());
        rpTgtVpool.setVirtualArrays(virtualArrays3);
        _dbClient.createObject(rpTgtVpool);

        // Create a RP VPLEX source virtual pool
        VirtualPool rpVplexSrcVpool = new VirtualPool();
        rpVplexSrcVpool.setId(URI.create(dummyVpoolUri));
        rpVplexSrcVpool.setLabel("rpVplexSrcVpool");
        rpVplexSrcVpool.setType("block");
        rpVplexSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpVplexSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        rpVplexSrcVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_distributed.name());
        StringMap vavpMap = new StringMap();
        vavpMap.put(varray1.getId().toString(), haVpool.getId().toString());
        rpVplexSrcVpool.setHaVarrayVpoolMap(vavpMap);
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(rpTgtVpool.getId());
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();

        protectionVarray.put(varray3.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpVplexSrcVpool.setProtectionVarraySettings(protectionVarray);
        rpVplexSrcVpool.setRpCopyMode("SYNCHRONOUS");
        rpVplexSrcVpool.setRpRpoType("MINUTES");
        rpVplexSrcVpool.setRpRpoValue(Long.valueOf("5"));
        matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        rpVplexSrcVpool.setMatchedStoragePools(matchedPools);
        rpVplexSrcVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(varray2.getId().toString());
        rpVplexSrcVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(rpVplexSrcVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, cg);

        // Run single volume placement: Run 10 times to make sure pool6 never comes up for source and pool9 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray2, project, rpVplexSrcVpool,
                    capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);
            assertNotNull(rec.getSourceRecommendations());
            assertNotNull(!rec.getSourceRecommendations().isEmpty());
            assertNotNull(rec.getProtectionDevice());
            assertTrue("rp1".equals(rec.getProtectionDevice().toString()));

            for (RPRecommendation sourceRec : rec.getSourceRecommendations()) {
                assertNotNull(sourceRec.getInternalSiteName());
                assertNotNull(sourceRec.getVirtualArray());
                assertNotNull(sourceRec.getVirtualPool());
                assertNotNull(sourceRec.getVirtualVolumeRecommendation());
                assertNotNull(sourceRec.getHaRecommendation());
                assertNotNull(sourceRec.getTargetRecommendations());
                assertTrue(!sourceRec.getTargetRecommendations().isEmpty());

                assertTrue("site1".equals(sourceRec.getInternalSiteName()));
                assertTrue("vmax2".equals(sourceRec.getSourceStorageSystem().toString()));
                assertTrue(("pool5".equals(sourceRec.getSourceStoragePool().toString()))
                        || ("pool4".equals(sourceRec.getSourceStoragePool().toString())));
                if (VirtualPool.vPoolSpecifiesHighAvailability(sourceRec.getVirtualPool())) {
                    assertNotNull(sourceRec.getVirtualVolumeRecommendation());
                }

                assertNotNull(sourceRec.getHaRecommendation().getVirtualVolumeRecommendation());
                assertTrue("vplex1"
                        .equals(sourceRec.getHaRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
                assertTrue("varray1".equals(sourceRec.getHaRecommendation().getVirtualArray().toString()));
                assertTrue(haVpoolUri.equals(sourceRec.getHaRecommendation().getVirtualPool().getId().toString()));
                assertTrue("vmax1".equals(sourceRec.getHaRecommendation().getSourceStorageSystem().toString()));
                assertTrue(("pool2".equals(sourceRec.getHaRecommendation().getSourceStoragePool().toString()))
                        || ("pool1".equals(sourceRec.getHaRecommendation().getSourceStoragePool().toString())));

                assertNotNull(sourceRec.getTargetRecommendations());
                assertTrue(!sourceRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : sourceRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getInternalSiteName());
                    assertNotNull(targetRec.getVirtualArray());
                    assertNotNull(targetRec.getVirtualPool());

                    if (VirtualPool.vPoolSpecifiesHighAvailability(targetRec.getVirtualPool())) {
                        assertNotNull(targetRec.getVirtualVolumeRecommendation());
                    }

                    assertTrue("varray3".equals(targetRec.getVirtualArray().toString()));
                    assertTrue(rpTgtVpool.getId().toString().equals(targetRec.getVirtualPool().getId().toString()));
                    assertTrue("site2".equals(targetRec.getInternalSiteName()));
                    assertTrue("vmax3".equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue(("pool8".equals(targetRec.getSourceStoragePool().toString()))
                            || ("pool7".equals(targetRec.getSourceStoragePool().toString())));
                }
            }

            // source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            RPRecommendation sourceJournalRec = rec.getSourceJournalRecommendation();
            if (VirtualPool.vPoolSpecifiesHighAvailability(sourceJournalRec.getVirtualPool())) {
                assertNotNull(sourceJournalRec.getVirtualVolumeRecommendation());
            }
            assertTrue(("pool5".equals(sourceJournalRec.getSourceStoragePool().toString()))
                    || ("pool4".equals(sourceJournalRec.getSourceStoragePool().toString())));

            // target journals
            assertNotNull(rec.getTargetJournalRecommendations());
            assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec.getInternalSiteName());
                assertNotNull(targetJournalRec.getVirtualArray());
                assertNotNull(targetJournalRec.getVirtualPool());
                if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalRec.getVirtualPool())) {
                    assertNotNull(targetJournalRec.getVirtualVolumeRecommendation());
                }

                assertTrue("vmax3".equals(targetJournalRec.getSourceStorageSystem().toString()));
                assertTrue(("pool8".equals(targetJournalRec.getSourceStoragePool().toString()))
                        || ("pool7".equals(targetJournalRec.getSourceStoragePool().toString())));
            }
            _log.info("Recommendation : " + rec.toString(_dbClient));
        }
    }

    /**
     * RP VPLEX placement -- placement decision based on RP array visibility
     * Protect HA side of the VPLEX Metro volume.
     */
    @Test
    @Ignore
    public void testPlacementRpVplexProtectHASite() {

        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax2FE = { "51:FE:FE:FE:FE:FE:FE:00", "51:FE:FE:FE:FE:FE:FE:01" };
        String[] vmax3FE = { "52:FE:FE:FE:FE:FE:FE:00", "52:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };

        String[] vplex1FE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplex1BE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };

        String[] vplex2FE = { "FE:FE:FE:FE:FE:FE:FE:02", "FE:FE:FE:FE:FE:FE:FE:03" };
        String[] vplex2BE = { "BE:BE:BE:BE:BE:BE:BE:02", "BE:BE:BE:BE:BE:BE:BE:03" };

        String[] vplex3FE = { "FE:FE:FE:FE:FE:FE:FE:04", "FE:FE:FE:FE:FE:FE:FE:05" };
        String[] vplex3BE = { "BE:BE:BE:BE:BE:BE:BE:04", "BE:BE:BE:BE:BE:BE:BE:05" };

        // Create 3 Virtual Arrays
        VirtualArray haVarray = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");
        VirtualArray srcVarray = PlacementTestUtils.createVirtualArray(_dbClient, "varray2");
        VirtualArray tgtVarray = PlacementTestUtils.createVirtualArray(_dbClient, "varray3");

        // Create 1 Network
        StringSet connVA = new StringSet();
        connVA.add(haVarray.getId().toString());
        connVA.add(srcVarray.getId().toString());
        connVA.add(tgtVarray.getId().toString());
        Network network = PlacementTestUtils.createNetwork(_dbClient, vplex1FE, "VSAN", "FC+BROCADE", connVA);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex3FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex1BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex2BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vplex3BE);
        PlacementTestUtils.addEndpoints(_dbClient, network, rp1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, rp2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax1FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax2FE);
        PlacementTestUtils.addEndpoints(_dbClient, network, vmax3FE);

        // Create 3 storage systems
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");
        StorageSystem storageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax2");
        StorageSystem storageSystem3 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax3");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, network, vmax1FE[i], haVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax1" + i, "C0+FC0" + i));
        }

        // Create two front-end storage ports VMAX2
        List<StoragePort> vmax2Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax2FE.length; i++) {
            vmax2Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem2, network, vmax2FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax2" + i, "D0+FC0" + i));
        }

        // Create two front-end storage ports VMAX3
        List<StoragePort> vmax3Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax3FE.length; i++) {
            vmax3Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem3, network, vmax3FE[i], tgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax3" + i, "E0+FC0" + i));
        }

        // Create 2 VPLEX storage systems
        StorageSystem vplexStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");
        StorageSystem vplexStorageSystem2 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex2");

        // Create two front-end storage ports VPLEX1
        List<StoragePort> fePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex1FE[i], haVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE1-" + (i + 1), "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1
        List<StoragePort> bePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex1BE[i], haVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE1-" + (i + 1), "B0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX2
        List<StoragePort> fePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2FE.length; i++) {
            fePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex2FE[i], srcVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE2-" + (i + 1), "F0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX2
        List<StoragePort> bePorts2 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex2BE.length; i++) {
            bePorts2.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, network, vplex2BE[i], srcVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE2-" + (i + 1), "G0+FC0" + i));
        }

        // Create two front-end storage ports VPLEX3
        List<StoragePort> fePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3FE.length; i++) {
            fePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, network, vplex3FE[i], tgtVarray,
                    StoragePort.PortType.frontend.name(), "portGroupFE3-" + (i + 1), "H0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX3
        List<StoragePort> bePorts3 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex3BE.length; i++) {
            bePorts3.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem2, network, vplex3BE[i], tgtVarray,
                    StoragePort.PortType.backend.name(), "portGroupBE3-" + (i + 1), "I0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);

        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex1cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex1cluster2"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex2cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex2cluster2"));

        StringSetMap rpVisibleSystems = new StringSetMap();
        StringSet storageIds = new StringSet();
        storageIds.add(vplexStorageSystem1.getId().toString());
        rpVisibleSystems.put("site1", storageIds);
        StringSet storageIds2 = new StringSet();
        storageIds2.add(vplexStorageSystem2.getId().toString());
        rpVisibleSystems.put("site2", storageIds2);

        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, rpVisibleSystems, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vplex1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);

        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vplex2"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);

        // Create a storage pool for vmax1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, storageSystem1, "pool1", "Pool1",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool2 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, storageSystem1, "pool2", "Pool2",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax1
        StoragePool pool3 = PlacementTestUtils.createStoragePool(_dbClient, haVarray, storageSystem1, "pool3", "Pool3",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool4 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, storageSystem2, "pool4", "Pool4",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool5 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, storageSystem2, "pool5", "Pool5",
                Long.valueOf(SIZE_GB * 10), Long.valueOf(SIZE_GB * 50), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax2
        StoragePool pool6 = PlacementTestUtils.createStoragePool(_dbClient, srcVarray, storageSystem2, "pool6", "Pool6",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool7 = PlacementTestUtils.createStoragePool(_dbClient, tgtVarray, storageSystem3, "pool7", "Pool7",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool8 = PlacementTestUtils.createStoragePool(_dbClient, tgtVarray, storageSystem3, "pool8", "Pool8",
                Long.valueOf(1024 * 1024 * 10), Long.valueOf(1024 * 1024 * 10), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create a storage pool for vmax3
        StoragePool pool9 = PlacementTestUtils.createStoragePool(_dbClient, tgtVarray, storageSystem3, "pool9", "Pool9",
                Long.valueOf(1024 * 1024 * 1), Long.valueOf(1024 * 1024 * 1), 100, 100,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());

        // Create HA virtual pool
        VirtualPool haVpool = new VirtualPool();
        haVpool.setId(URI.create(haVpoolUri));
        haVpool.setLabel("haVpool");
        haVpool.setType("block");
        haVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        haVpool.setDriveType(SupportedDriveTypes.FC.name());
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());
        matchedPools.add(pool2.getId().toString());
        matchedPools.add(pool3.getId().toString());
        haVpool.setMatchedStoragePools(matchedPools);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(haVarray.getId().toString());
        haVpool.setVirtualArrays(virtualArrays1);
        haVpool.setUseMatchedPools(true);
        _dbClient.createObject(haVpool);

        // Create RP target vpool
        VirtualPool rpTgtVpool = new VirtualPool();
        rpTgtVpool.setId(URI.create("rpTgtVpool"));
        rpTgtVpool.setLabel("rpTgtVpool");
        rpTgtVpool.setType("block");
        rpTgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpTgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        rpTgtVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        matchedPools = new StringSet();
        matchedPools.add(pool7.getId().toString());
        matchedPools.add(pool8.getId().toString());
        matchedPools.add(pool9.getId().toString());
        rpTgtVpool.setMatchedStoragePools(matchedPools);
        rpTgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays3 = new StringSet();
        virtualArrays3.add(tgtVarray.getId().toString());
        rpTgtVpool.setVirtualArrays(virtualArrays3);
        _dbClient.createObject(rpTgtVpool);

        // Create a RP VPLEX source virtual pool
        VirtualPool rpVplexSrcVpool = new VirtualPool();
        rpVplexSrcVpool.setId(URI.create(dummyVpoolUri));
        rpVplexSrcVpool.setLabel("rpVplexSrcVpool");
        rpVplexSrcVpool.setType("block");
        rpVplexSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpVplexSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        rpVplexSrcVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_distributed.name());
        StringMap vavpMap = new StringMap();
        vavpMap.put(haVarray.getId().toString(), haVpool.getId().toString());
        rpVplexSrcVpool.setHaVarrayVpoolMap(vavpMap);
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(rpTgtVpool.getId());
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();

        protectionVarray.put(tgtVarray.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpVplexSrcVpool.setProtectionVarraySettings(protectionVarray);
        rpVplexSrcVpool.setRpCopyMode("SYNCHRONOUS");
        rpVplexSrcVpool.setRpRpoType("MINUTES");
        rpVplexSrcVpool.setRpRpoValue(Long.valueOf("5"));
        matchedPools = new StringSet();
        matchedPools.add(pool4.getId().toString());
        matchedPools.add(pool5.getId().toString());
        matchedPools.add(pool6.getId().toString());
        rpVplexSrcVpool.setMatchedStoragePools(matchedPools);
        rpVplexSrcVpool.setHaVarrayConnectedToRp(haVarray.getId().toString());
        rpVplexSrcVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(srcVarray.getId().toString());
        rpVplexSrcVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(rpVplexSrcVpool);

        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("2GB", 1, cg);

        // Run single volume placement: Run 10 times to make sure pool6 never comes up for source and pool9 for target.
        for (int i = 0; i < 10; i++) {
            List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, srcVarray, project, rpVplexSrcVpool,
                    capabilities);

            assertNotNull(recommendations);
            assertTrue(!recommendations.isEmpty());
            assertNotNull(recommendations.get(0));
            RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);
            assertNotNull(rec.getSourceRecommendations());
            assertTrue(!rec.getSourceRecommendations().isEmpty());
            assertNotNull(rec.getProtectionDevice());
            assertTrue("rp1".equals(rec.getProtectionDevice().toString()));

            for (RPRecommendation sourceRec : rec.getSourceRecommendations()) {
                assertNotNull(sourceRec.getInternalSiteName());
                assertNotNull(sourceRec.getVirtualArray());
                assertNotNull(sourceRec.getVirtualPool());
                assertNotNull(sourceRec.getVirtualVolumeRecommendation());
                assertNotNull(sourceRec.getHaRecommendation());
                assertNotNull(sourceRec.getTargetRecommendations());
                assertTrue(!sourceRec.getTargetRecommendations().isEmpty());

                assertTrue("site1".equals(sourceRec.getInternalSiteName()));
                assertTrue("vmax1".equals(sourceRec.getSourceStorageSystem().toString()));
                assertTrue(("pool1".equals(sourceRec.getSourceStoragePool().toString()))
                        || ("pool2".equals(sourceRec.getSourceStoragePool().toString())));
                assertTrue("vplex1".equals(sourceRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

                assertNotNull(sourceRec.getHaRecommendation().getVirtualVolumeRecommendation());
                assertTrue("vplex1"
                        .equals(sourceRec.getHaRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
                assertTrue("varray2".equals(sourceRec.getHaRecommendation().getVirtualArray().toString()));
                assertTrue(dummyVpoolUri.equals(sourceRec.getHaRecommendation().getVirtualPool().getId().toString()));
                assertTrue("vmax2".equals(sourceRec.getHaRecommendation().getSourceStorageSystem().toString()));
                assertTrue(("pool4".equals(sourceRec.getHaRecommendation().getSourceStoragePool().toString()))
                        || ("pool5".equals(sourceRec.getHaRecommendation().getSourceStoragePool().toString())));

                assertNotNull(sourceRec.getTargetRecommendations());
                assertTrue(!sourceRec.getTargetRecommendations().isEmpty());
                for (RPRecommendation targetRec : sourceRec.getTargetRecommendations()) {
                    assertNotNull(targetRec.getInternalSiteName());
                    assertNotNull(targetRec.getVirtualArray());
                    assertNotNull(targetRec.getVirtualPool());

                    if (VirtualPool.vPoolSpecifiesHighAvailability(targetRec.getVirtualPool())) {
                        assertNotNull(targetRec.getVirtualVolumeRecommendation());
                    }

                    assertTrue("varray3".equals(targetRec.getVirtualArray().toString()));
                    assertTrue(rpTgtVpool.getId().toString().equals(targetRec.getVirtualPool().getId().toString()));
                    assertTrue("site2".equals(targetRec.getInternalSiteName()));
                    assertTrue("vmax3".equals(targetRec.getSourceStorageSystem().toString()));
                    assertTrue(("pool8".equals(targetRec.getSourceStoragePool().toString()))
                            || ("pool7".equals(targetRec.getSourceStoragePool().toString())));
                }
            }

            // source journal
            assertNotNull(rec.getSourceJournalRecommendation());
            RPRecommendation sourceJournalRec = rec.getSourceJournalRecommendation();
            if (VirtualPool.vPoolSpecifiesHighAvailability(sourceJournalRec.getVirtualPool())) {
                assertNotNull(sourceJournalRec.getVirtualVolumeRecommendation());
            }
            assertTrue(("pool1".equals(sourceJournalRec.getSourceStoragePool().toString()))
                    || ("pool2".equals(sourceJournalRec.getSourceStoragePool().toString())));

            // target journals
            assertNotNull(rec.getTargetJournalRecommendations());
            assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
            for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
                assertNotNull(targetJournalRec.getInternalSiteName());
                assertNotNull(targetJournalRec.getVirtualArray());
                assertNotNull(targetJournalRec.getVirtualPool());
                if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalRec.getVirtualPool())) {
                    assertNotNull(targetJournalRec.getVirtualVolumeRecommendation());
                }

                assertTrue("vmax3".equals(targetJournalRec.getSourceStorageSystem().toString()));
                assertTrue(("pool8".equals(targetJournalRec.getSourceStoragePool().toString()))
                        || ("pool7".equals(targetJournalRec.getSourceStoragePool().toString())));
            }
            _log.info("Recommendation : " + rec.toString(_dbClient));
        }
    }
    
    
    /**
     * RP VPLEX connectivity with only the vplex is visible to the rpsites
     */

    @Test
    @Ignore
    public void testRpVplexConnectivtyAndPlacement() {
        // Create Tenant
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URI.create("tenant"));
        _dbClient.createObject(tenant);

        // Create a project object
        Project project = new Project();
        project.setId(URI.create("project"));
        project.setLabel("project");
        project.setTenantOrg(new NamedURI(tenant.getId(), project.getLabel()));
        _dbClient.createObject(project);

        // Create block consistency group
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setProject(new NamedURI(project.getId(), project.getLabel()));
        cg.setId(URI.create("blockCG"));
        _dbClient.createObject(cg);

        // Create capabilities
        VirtualPoolCapabilityValuesWrapper capabilities = PlacementTestUtils.createCapabilities("5GB", 4, cg);               

        
        StringMap siteVolCap = new StringMap();
        siteVolCap.put("site1", "3221225472");
        siteVolCap.put("site2", "3221225472");

        StringMap siteVolCnt = new StringMap();
        siteVolCnt.put("site1", "10");
        siteVolCnt.put("site2", "10");

        String[] vmax1FE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01" };

        String[] rp1FE = { "53:FE:FE:FE:FE:FE:FE:00", "53:FE:FE:FE:FE:FE:FE:01" };
        String[] rp2FE = { "54:FE:FE:FE:FE:FE:FE:00", "54:FE:FE:FE:FE:FE:FE:01" };

        String[] vplex1FE = { "FE:FE:FE:FE:FE:FE:FE:00", "FE:FE:FE:FE:FE:FE:FE:01" };
        String[] vplex1BE = { "BE:BE:BE:BE:BE:BE:BE:00", "BE:BE:BE:BE:BE:BE:BE:01" };        

        // Create Virtual Array1
        VirtualArray varray1 = PlacementTestUtils.createVirtualArray(_dbClient, "varray1");

        // Create 2 Networks
        StringSet connVA = new StringSet();
        connVA.add(varray1.getId().toString());
        Network networkFE1 = PlacementTestUtils.createNetwork(_dbClient, (String[]) ArrayUtils.addAll(vplex1FE, rp1FE), "VSANFE1", "FC+BROCADE+FE", connVA);
        Network networkBE1 = PlacementTestUtils.createNetwork(_dbClient, vplex1BE, "VSANBE1", "FC+BROCADE+BE", connVA);        

        // Create storage system
        StorageSystem storageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");

        // Create two front-end storage ports VMAX1
        List<StoragePort> vmax1Ports = new ArrayList<StoragePort>();
        for (int i = 0; i < vmax1FE.length; i++) {
            vmax1Ports.add(PlacementTestUtils.createStoragePort(_dbClient, storageSystem1, networkBE1, vmax1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupvmax1" + i, "C0+FC0" + i));
        }
        
        // Create VPLEX storage system
        StorageSystem vplexStorageSystem1 = PlacementTestUtils.createStorageSystem(_dbClient, "vplex", "vplex1");

        // Create two front-end storage ports VPLEX1
        List<StoragePort> fePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1FE.length; i++) {
            fePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkFE1, vplex1FE[i], varray1,
                    StoragePort.PortType.frontend.name(), "portGroupFE1-" + (i + 1), "A0+FC0" + i));
        }

        // Create two back-end storage ports VPLEX1
        List<StoragePort> bePorts1 = new ArrayList<StoragePort>();
        for (int i = 0; i < vplex1BE.length; i++) {
            bePorts1.add(PlacementTestUtils.createStoragePort(_dbClient, vplexStorageSystem1, networkBE1, vplex1BE[i], varray1,
                    StoragePort.PortType.backend.name(), "portGroupBE1-" + (i + 1), "B0+FC0" + i));
        }

        // Create RP system
        AbstractChangeTrackingSet<String> wwnSite1 = new StringSet();
        for (int i = 0; i < rp1FE.length; i++) {
            wwnSite1.add(rp1FE[i]);
        }

        StringSetMap initiatorsSiteMap = new StringSetMap();
        initiatorsSiteMap.put("site1", wwnSite1);

        AbstractChangeTrackingSet<String> wwnSite2 = new StringSet();
        for (int i = 0; i < rp2FE.length; i++) {
            wwnSite2.add(rp2FE[i]);
        }

        initiatorsSiteMap.put("site2", wwnSite2);

        StringSet storSystems = new StringSet();
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site1", "vplex1cluster1"));
        storSystems.add(ProtectionSystem.generateAssociatedStorageSystem("site2", "vplex1cluster1"));        

        ProtectionSystem rpSystem = PlacementTestUtils.createProtectionSystem(_dbClient, "rp", "rp1", "site1", "site2", null, "IP",
                initiatorsSiteMap, storSystems, null, Long.valueOf("3221225472"), Long.valueOf("2"), siteVolCap, siteVolCnt);

        // RP Site Array objects
        RPSiteArray rpSiteArray1 = new RPSiteArray();
        rpSiteArray1.setId(URI.create("rsa1"));
        rpSiteArray1.setStorageSystem(URI.create("vplex1"));
        rpSiteArray1.setRpInternalSiteName("site1");
        rpSiteArray1.setRpSiteName("site1");
        rpSiteArray1.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray1);
        
        
        RPSiteArray rpSiteArray2 = new RPSiteArray();
        rpSiteArray2.setId(URI.create("rsa2"));
        rpSiteArray2.setStorageSystem(URI.create("vplex1"));
        rpSiteArray2.setRpInternalSiteName("site2");
        rpSiteArray2.setRpSiteName("site2");
        rpSiteArray2.setRpProtectionSystem(rpSystem.getId());
        _dbClient.createObject(rpSiteArray2);
                
        ConnectivityUtil.updateRpSystemConnectivity(rpSystem, _dbClient);                
        
        StringSet virtualArrays = rpSystem.getVirtualArrays();
       
        assertNotNull(virtualArrays);
        assertTrue(!virtualArrays.isEmpty());
        
        for (String varrayURIStr : virtualArrays) {
        	 _log.info("Protection is possible to Virtual Array: " + varrayURIStr);
        }                
        

        // Create a storage pool for vmax1
        StoragePool pool1 = PlacementTestUtils.createStoragePool(_dbClient, varray1, storageSystem1, "pool1", "Pool1",
                Long.valueOf(SIZE_GB * 500), Long.valueOf(SIZE_GB * 500), 300, 300,
                StoragePool.SupportedResourceTypes.THIN_ONLY.toString());        

        // Create a base RP virtual pool
        VirtualPool tgtVpool = new VirtualPool();
        tgtVpool.setId(URI.create("vpoolRP"));
        tgtVpool.setLabel("vpoolRP");
        tgtVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        tgtVpool.setDriveType(SupportedDriveTypes.FC.name());
        tgtVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());        
        StringSet matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());        
        tgtVpool.setMatchedStoragePools(matchedPools);
        tgtVpool.setUseMatchedPools(true);
        StringSet virtualArrays1 = new StringSet();
        virtualArrays1.add(varray1.getId().toString());
        tgtVpool.setVirtualArrays(virtualArrays1);
        _dbClient.createObject(tgtVpool);

        // Create a RP VPLEX virtual pool
        VirtualPool rpSrcVpool = new VirtualPool();
        rpSrcVpool.setId(URI.create("rpVplexVpool"));
        rpSrcVpool.setLabel("rpVplexVpool");
        rpSrcVpool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        rpSrcVpool.setDriveType(SupportedDriveTypes.FC.name());
        rpSrcVpool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_local.name());
        rpSrcVpool.setJournalSize("10GB");                
        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        protectionSettings.setVirtualPool(tgtVpool.getId());
        protectionSettings.setJournalSize("10GB");
        protectionSettings.setId(URI.create("protectionSettings"));
        _dbClient.createObject(protectionSettings);

        List<VpoolProtectionVarraySettings> protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        protectionSettingsList.add(protectionSettings);
        StringMap protectionVarray = new StringMap();
        protectionVarray.put(varray1.getId().toString(), protectionSettingsList.get(0).getId().toString());
        rpSrcVpool.setProtectionVarraySettings(protectionVarray);
        rpSrcVpool.setRpCopyMode("SYNCHRONOUS");
        rpSrcVpool.setRpRpoType("MINUTES");
        rpSrcVpool.setRpRpoValue(Long.valueOf("5"));
        matchedPools = new StringSet();
        matchedPools.add(pool1.getId().toString());       
        rpSrcVpool.setMatchedStoragePools(matchedPools);
        rpSrcVpool.setUseMatchedPools(true);
        StringSet virtualArrays2 = new StringSet();
        virtualArrays2.add(varray1.getId().toString());
        rpSrcVpool.setVirtualArrays(virtualArrays2);
        _dbClient.createObject(rpSrcVpool);


        List recommendations = PlacementTestUtils.invokePlacement(_dbClient, _coordinator, varray1, project, rpSrcVpool, capabilities);

        assertNotNull(recommendations);
        assertTrue(!recommendations.isEmpty());
        assertNotNull(recommendations.get(0));
        RPProtectionRecommendation rec = (RPProtectionRecommendation) recommendations.get(0);

        for (RPRecommendation rpVplexRec : rec.getSourceRecommendations()) {
        	assertNotNull(rpVplexRec.getVirtualVolumeRecommendation());
        	assertNotNull(rpVplexRec.getVirtualVolumeRecommendation().getVPlexStorageSystem());
        	assertNotNull(rpVplexRec.getSourceStoragePool());
        	assertNotNull(rpVplexRec.getSourceStorageSystem());
        	assertNotNull(rpVplexRec.getTargetRecommendations());
        	assertTrue(!rpVplexRec.getTargetRecommendations().isEmpty());
        	assertNotNull(rpVplexRec.getInternalSiteName());
        	assertTrue("site1".equals(rpVplexRec.getInternalSiteName()));
        	assertTrue("vmax1".equals(rpVplexRec.getSourceStorageSystem().toString()));
        	assertTrue(("pool1".equals(rpVplexRec.getSourceStoragePool().toString())));        			
        	assertTrue("vplex1".equals(rpVplexRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
        	

        	for (RPRecommendation targetRec : rpVplexRec.getTargetRecommendations()) {
        		assertNotNull(targetRec.getSourceStoragePool());
        		assertNotNull(targetRec.getInternalSiteName());
        		assertNotNull(targetRec.getSourceStorageSystem());
        		assertNotNull(targetRec.getVirtualPool());
        		assertTrue("vpoolRP".equals(targetRec.getVirtualPool().getId().toString()));
        		assertTrue("varray1".equals(targetRec.getVirtualArray().toString()));
        		assertTrue("site1".equals(targetRec.getInternalSiteName()));
        		assertTrue("vplex1".equals(targetRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
        		assertTrue("vmax1".equals(targetRec.getSourceStorageSystem().toString()));
        		assertTrue(("pool1".equals(targetRec.getSourceStoragePool().toString())));
        	}
        }

        assertNotNull(rec.getSourceJournalRecommendation());
        assertNotNull(rec.getSourceJournalRecommendation().getInternalSiteName());
        assertNotNull(rec.getSourceJournalRecommendation().getSourceStorageSystem());
        assertNotNull(rec.getSourceJournalRecommendation().getSourceStoragePool());
        if (rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation() != null) {
        	assertTrue("vplex1".equals(rec.getSourceJournalRecommendation().getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));
        }
        assertTrue("site1".equals(rec.getSourceJournalRecommendation().getInternalSiteName().toString()));
        assertTrue(("pool1".equals(rec.getSourceJournalRecommendation().getSourceStoragePool().toString())));
        assertNotNull(rec.getTargetJournalRecommendations());
        assertTrue(!rec.getTargetJournalRecommendations().isEmpty());
        for (RPRecommendation targetJournalRec : rec.getTargetJournalRecommendations()) {
        	assertNotNull(targetJournalRec.getVirtualArray());
        	assertNotNull(targetJournalRec.getInternalSiteName());
        	assertNotNull(targetJournalRec.getSourceStorageSystem());
        	assertNotNull(targetJournalRec.getSourceStoragePool());
        	assertTrue("varray1".equals(targetJournalRec.getVirtualArray().toString()));
        	assertTrue("vmax1".equals(targetJournalRec.getSourceStorageSystem().toString()));
        	assertTrue("site1".equals(targetJournalRec.getInternalSiteName().toString()));
        	assertTrue(("pool1".equals(targetJournalRec.getSourceStoragePool().toString())));
        	assertTrue("vplex1".equals(targetJournalRec.getVirtualVolumeRecommendation().getVPlexStorageSystem().toString()));

        }
        _log.info(rec.toString(_dbClient));

    }
    
    

}
