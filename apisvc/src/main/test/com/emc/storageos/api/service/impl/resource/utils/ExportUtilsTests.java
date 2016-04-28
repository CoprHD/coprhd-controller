/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.DbClientTest.DbClientImplUnitTester;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.model.block.export.ITLBulkRep;

import junit.framework.Assert;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({ "ExportUtilsTests.xml" })
// This test is currently failing with file IO issues with Cassandra.  COP-18538.
@Ignore
public class ExportUtilsTests extends DbsvcTestBase {

    private static final Logger _log = LoggerFactory.getLogger(ExportUtilsTests.class);

    // test numbers
    final int NUM_STORAGE_PORTS = 4;
    final int NUM_EXPORT_GROUPS = 13;
    final int NUM_EXPORT_MASKS = 1;
    final int NUM_VOLUMES = 81;
    final int NUM_INITIATORS = 64;

    // Volume IDs
    List<URI> _volumeIds = new ArrayList<URI>();

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
    public void testDbClientSanity() {
        StoragePool pool1 = new StoragePool();
        pool1.setId(URI.create("pool1"));
        pool1.setLabel("Pool1");
        _dbClient.createObject(pool1);

        StoragePool tempPool = _dbClient.queryObject(StoragePool.class, URI.create("pool1"));
        assertNotNull(tempPool);
    }

    /**
     * Populate the database with ITL components
     */
    public void populateDb() {
        String[] vmaxFE = { "50:FE:FE:FE:FE:FE:FE:00", "50:FE:FE:FE:FE:FE:FE:01", "50:FE:FE:FE:FE:FE:FE:02",
                "50:FE:FE:FE:FE:FE:FE:03" };

        // Create a Network
        Network network = ExportUtilsTestUtils.createNetwork(_dbClient, vmaxFE, "VSANFE", "FC+BROCADE+FE", null);

        // Create a Virtual Array
        VirtualArray varray = ExportUtilsTestUtils.createVirtualArray(_dbClient, "varray1");

        // Create a storage system
        StorageSystem storageSystem = ExportUtilsTestUtils.createStorageSystem(_dbClient, "vmax", "vmax1");

        // Create two front-end storage ports VMAX
        List<StoragePort> vmaxPorts = new ArrayList<StoragePort>();
        for (int i = 0; i < vmaxFE.length; i++) {
            vmaxPorts.add(ExportUtilsTestUtils.createStoragePort(_dbClient, storageSystem, network, vmaxFE[i], varray,
                    StoragePort.PortType.frontend.name(), "portGroupvmax" + i, "C0+FC0" + i));
        }

        // Create initiators
        List<Initiator> initiators = new ArrayList<Initiator>();
        for (int i = 0; i < NUM_INITIATORS; i++) {
            initiators.add(ExportUtilsTestUtils.createInitiator(_dbClient, network, i));
        }

        // Create Volumes
        List<Volume> volumes = new ArrayList<Volume>();
        for (int i = 0; i < NUM_VOLUMES; i++) {
            Volume volume = ExportUtilsTestUtils.createVolume(_dbClient, varray, i);
            volumes.add(volume);
            _volumeIds.add(volume.getId());
        }

        // Create export groups
        List<ExportGroup> egs = new ArrayList<ExportGroup>();
        for (int i = 0; i < NUM_EXPORT_GROUPS; i++) {
            egs.add(ExportUtilsTestUtils.createExportGroup(_dbClient, initiators, volumes, varray, i));
        }

        // Create export masks
        List<ExportMask> ems = new ArrayList<ExportMask>();
        for (int i = 0; i < NUM_EXPORT_MASKS; i++) {
            ems.add(ExportUtilsTestUtils.createExportMask(_dbClient, egs, initiators, volumes, vmaxPorts, i));
        }
    }

    /**
     * Performance test for ITL retrieval for bulk api
     */
    @Test
    public void testITLPerformance() {
        // Populate the database
        populateDb();

        // Start a timer
        long startTime = System.currentTimeMillis();
        ITLBulkRep list = new ITLBulkRep();
        for (URI volumeId : _volumeIds) {
            queryResource(volumeId);
            list.getExportList().addAll(
                    ExportUtils.getBlockObjectInitiatorTargets(volumeId, _dbClient, false).getExportList());
        }

        // End a timer
        long endTime = System.currentTimeMillis();

        System.out.println("Time elapsed: " + (endTime - startTime) + "ms");
        // Assert the ITL entries
        
        int numItlsExpected = this.NUM_EXPORT_GROUPS * this.NUM_EXPORT_MASKS * this.NUM_INITIATORS * this.NUM_STORAGE_PORTS * this.NUM_VOLUMES;
        
        assertEquals("Number of ITLs returned", numItlsExpected, list.getExportList().size());

    }

    protected DataObject queryResource(URI id) {
        Class<? extends DataObject> blockClazz = Volume.class;

        if (URIUtil.isType(id, BlockMirror.class)) {
            blockClazz = BlockMirror.class;
        }
        if (URIUtil.isType(id, VplexMirror.class)) {
            blockClazz = VplexMirror.class;
        }
        BlockObject object = Volume.fetch(_dbClient, id);
        return object;
    }

}
