/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.modelclient;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbModelClientImpl;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.modelclient.model.BlockMirror;
import com.emc.storageos.db.modelclient.model.BlockObject;
import com.emc.storageos.db.modelclient.model.BlockSnapshot;
import com.emc.storageos.db.modelclient.model.ClassAManyToMany;
import com.emc.storageos.db.modelclient.model.ClassAOneToMany;
import com.emc.storageos.db.modelclient.model.ClassAOneToOne;
import com.emc.storageos.db.modelclient.model.ClassBManyToMany;
import com.emc.storageos.db.modelclient.model.ClassBOneToMany;
import com.emc.storageos.db.modelclient.model.ClassBOneToOne;
import com.emc.storageos.db.modelclient.model.ExportGroup;
import com.emc.storageos.db.modelclient.model.ExportMask;
import com.emc.storageos.db.modelclient.model.StorageDevice;
import com.emc.storageos.db.modelclient.model.StoragePool;
import com.emc.storageos.db.modelclient.model.StorageSystem;
import com.emc.storageos.db.modelclient.model.VirtualArray;
import com.emc.storageos.db.modelclient.model.Volume;
import com.emc.storageos.db.server.DbClientTest.DbClientImplUnitTester;
import com.emc.storageos.db.server.DbsvcTestBase;

/**
 * @author cgarber
 * 
 */
public class LazyLoadTests extends DbsvcTestBase {
    private static final Logger _log = LoggerFactory.getLogger(LazyLoadTests.class);

    private DbModelClient modelClient;
    private DbClient _dbClient;

    @BeforeClass
    public static void setup() throws IOException {

        sourceVersion = new DbVersionInfo();
        sourceVersion.setSchemaVersion("1.1");
        _dataDir = new File(dataDir);
        if (_dataDir.exists() && _dataDir.isDirectory()) {
            cleanDirectory(_dataDir);
        }
        _dataDir.mkdir();

        DataObjectScanner scanner = new DataObjectScanner();
        scanner.setPackages("com.emc.storageos.db.modelclient.model");
        scanner.init();

        // setting migration status to failed is a workaround for getting into
        // an endless loop of trying to run migration; if it's set to failed, we
        // simply by-pass migration; the other side affect is we don't start any
        // of the dbsvc background tasks, but we don't need them for this test
        setMigrationStatus(MigrationStatus.FAILED);

        startDb(sourceVersion.getSchemaVersion(), sourceVersion.getSchemaVersion(), null, scanner);
    }

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
        dbClient.setDrUtil(new DrUtil(_coordinator));
        dbClient.start();

        _dbClient = dbClient;
        modelClient = new DbModelClientImpl(_dbClient);
    }

    private static void setMigrationStatus(MigrationStatus status) {
        String dbConfigPath = _coordinator.getVersionedDbConfigPath("dbsvc", "1.1");
        Configuration config = _coordinator.queryConfiguration(dbConfigPath, Constants.GLOBAL_ID);
        if (config == null) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setKind(dbConfigPath);
            cfg.setId(Constants.GLOBAL_ID);
            config = cfg;
        }
        config.setConfig(Constants.MIGRATION_STATUS, status.name());
        _coordinator.persistServiceConfiguration(config);
    }

    @After
    public void teardown() {
        if (_dbClient instanceof DbClientImplUnitTester) {
            ((DbClientImplUnitTester) _dbClient).removeAll();
        }
    }

    private List<StorageSystem> createStorageSystems(int count, String labelPrefix, URI varrayId) {
        List<StorageSystem> systems = new ArrayList<StorageSystem>(count);

        for (int i = 0; i < count; i++) {
            StorageSystem system = new StorageSystem();
            system.setId(URIUtil.createId(StorageSystem.class));
            system.setLabel(labelPrefix + i);
            system.setVirtualArray(varrayId);
            modelClient.create(system);
            systems.add(system);
        }

        return systems;
    }

    private List<ExportGroup> createExportGroups(int count, Collection<ExportMask> ems, Collection<BlockSnapshot> snapshots) {
        List<ExportGroup> egs = new ArrayList<ExportGroup>(count);

        for (int i = 0; i < count; i++) {
            ExportGroup eg = new ExportGroup();
            eg.setId(URIUtil.createId(ExportGroup.class));
            String label = String.format("%1$d :/#$#@$\\: Test Label", i);
            eg.setLabel(label);
            if (ems != null) {
                StringSet emStrSet = new StringSet();
                StringSet emLabelStrSet = new StringSet();
                for (ExportMask mask : ems) {
                    emStrSet.add(mask.getId().toString());
                    emLabelStrSet.add(mask.getLabel());
                }
                eg.setExportMasks(emStrSet);
                eg.setExportMaskLabels(emLabelStrSet);
            }
            if (snapshots != null) {
                StringSet snapshotStrSet = new StringSet();
                for (BlockSnapshot snapshot : snapshots) {
                    snapshotStrSet.add(snapshot.getId().toString());
                }
                eg.setSnapshots(snapshotStrSet);
            }
            modelClient.create(eg);

            egs.add(eg);
        }

        return egs;
    }

    private <T extends DataObject> List<T> createDbObject(Class<T> clazz, int count) throws InstantiationException, IllegalAccessException {
        List<T> ems = new ArrayList<T>(count);

        for (int i = 0; i < count; i++) {
            T obj = clazz.newInstance();
            obj.setId(URIUtil.createId(clazz));
            String label = String.format("%1$d :/#$#@$\\: Test Label", i);
            obj.setLabel(label);
            modelClient.create(obj);

            ems.add(obj);
        }

        return ems;
    }

    @Test
    public void testBasicQuery() {
        try {
            int numVirtualArrays = 1;
            int numStorageSystems = 5;

            int numExportGroups = 1;
            int numExportMasks = 5;
            int numSnapshots = 3;

            URI vArrayId = createDbObject(VirtualArray.class, numVirtualArrays).iterator().next().getId();
            List<StorageSystem> systems = createStorageSystems(numStorageSystems, "system", vArrayId);

            // storageSystems in VirtualArray is mapped by a VirtualArray URI field in StorageSystems
            VirtualArray varray = modelClient.find(VirtualArray.class, vArrayId);
            List<StorageSystem> lazyLoadedSystems = varray.getStorageSystems();

            Assert.assertNotNull(lazyLoadedSystems);
            Assert.assertEquals(systems.size(), lazyLoadedSystems.size());

            // virtual array in storage system is mapped by a single URI (this tests lazy loading a single object as
            // opposed to a list)
            for (StorageSystem system : lazyLoadedSystems) {
                Assert.assertNotNull(system.getVArray());
                Assert.assertEquals(varray.getId(), system.getVArray().getId());
            }

            // export masks in export group is mapped by a stringset in export group
            // exportMaskSet is implemented as a ArrayList
            URI groupId = createExportGroups(numExportGroups, createDbObject(ExportMask.class, numExportMasks),
                    createDbObject(BlockSnapshot.class, numSnapshots)).iterator().next().getId();

            Iterator<ExportMask> allMasks = modelClient.findAll(ExportMask.class);
            int numFound = 0;
            while (allMasks.hasNext()) {
                numFound++;
                allMasks.next();
            }
            Assert.assertEquals(numExportMasks, numFound);

            ExportGroup group = modelClient.find(ExportGroup.class, groupId);
            List<ExportMask> masks = group.getExportMaskSet();

            Assert.assertNotNull(masks);
            Assert.assertEquals(numExportMasks, masks.size());

            // snapshots in export group is mapped by a stringset in export group
            // snapshotSet is implemented as a HashSet
            Assert.assertNotNull(group.getSnapshotSet());
            Assert.assertEquals(numSnapshots, group.getSnapshotSet().size());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void multiLevelSingleObjectRelationships() {
        try {
            Volume volume = createDbObject(Volume.class, 1).iterator().next();
            StoragePool pool = createDbObject(StoragePool.class, 1).iterator().next();
            StorageSystem device = createDbObject(StorageSystem.class, 1).iterator().next();
            VirtualArray vArray = createDbObject(VirtualArray.class, 1).iterator().next();

            volume.setPool(pool.getId());
            modelClient.update(volume);

            pool.setStorageDevice(device.getId());
            modelClient.update(pool);

            device.setVirtualArray(vArray.getId());
            modelClient.update(device);

            Volume queriedVol = modelClient.find(Volume.class, volume.getId());

            Assert.assertEquals(volume.getId(), queriedVol.getId());
            Assert.assertEquals(pool.getId(), queriedVol.getStoragePool().getId());
            Assert.assertEquals(device.getId(), queriedVol.getStoragePool().getStorageDeviceObj().getId());
            Assert.assertEquals(vArray.getId(), queriedVol.getStoragePool().getStorageDeviceObj().getVArray().getId());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @Ignore("not supported")
    public void testReferenceByLabel() {

        try {
            int numExportGroups = 1;
            int numExportMasks = 5;
            int numSnapshots = 3;

            URI groupId = createExportGroups(numExportGroups, createDbObject(ExportMask.class, numExportMasks),
                    createDbObject(BlockSnapshot.class, numSnapshots)).iterator().next().getId();
            ExportGroup group = modelClient.find(ExportGroup.class, groupId);

            List<ExportMask> masks = group.getExportMasksFromLabels();

            Assert.assertNotNull(masks);
            Assert.assertEquals(numExportMasks, masks.size());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }

    }

    @Test
    public void testModifyList() {
        // demonstrates that if we modify the lazy loaded list, the stringset reflects the change
        try {
            int numExportGroups = 1;

            int numExportMasks = 5;
            int numExportMasksAdded = 1;

            int numSnapshots = 3;
            int numSnapshotsAdded = 5;

            URI groupId = createExportGroups(numExportGroups, createDbObject(ExportMask.class, numExportMasks),
                    createDbObject(BlockSnapshot.class, numSnapshots)).iterator().next().getId();

            ExportGroup group = modelClient.find(ExportGroup.class, groupId);

            Assert.assertEquals(numExportMasks, group.getExportMaskSet().size());

            // add some new export masks to the lazy loaded list
            List<ExportMask> masks = createDbObject(ExportMask.class, numExportMasksAdded);
            for (ExportMask mask : masks) {
                group.addExportMask(mask);
            }

            // test before we persist
            Assert.assertEquals(numExportMasks + numExportMasksAdded, group.getExportMasks().size());

            modelClient.update(group);

            // test again after we persist (the list should not get loaded again)
            Assert.assertEquals(numExportMasks + numExportMasksAdded, group.getExportMasks().size());

            Assert.assertEquals(numSnapshots, group.getSnapshotSet().size());

            // add an entirely new snapshot list
            List<BlockSnapshot> snapshotsToAdd = createDbObject(BlockSnapshot.class, numSnapshotsAdded);
            Set<BlockSnapshot> snapshots = new HashSet<BlockSnapshot>();
            snapshots.addAll(group.getSnapshotSet());
            for (BlockSnapshot snapshot : snapshotsToAdd) {
                snapshots.add(snapshot);
            }
            group.setSnapshotSet(snapshots);

            // test before we persist
            Assert.assertEquals(numSnapshots + numSnapshotsAdded, group.getSnapshots().size());
            Assert.assertEquals(numSnapshots + numSnapshotsAdded, group.getSnapshotSet().size());

            modelClient.update(group);

            // test again after we persist (the list should not get loaded again)
            Assert.assertEquals(numSnapshots + numSnapshotsAdded, group.getSnapshots().size());
            Assert.assertEquals(numSnapshots + numSnapshotsAdded, group.getSnapshotSet().size());

            // now test removing things from lists
            BlockSnapshot snapshotToRemove = group.getSnapshotSet().iterator().next();
            group.getSnapshotSet().remove(snapshotToRemove);
            Assert.assertEquals(numSnapshots + numSnapshotsAdded - 1, group.getSnapshots().size());
            Assert.assertEquals(numSnapshots + numSnapshotsAdded - 1, group.getSnapshotSet().size());

            ExportMask maskToRemove = group.getExportMaskSet().iterator().next();
            group.removeExportMask(maskToRemove);
            Assert.assertEquals(numExportMasks + numExportMasksAdded - 1, group.getExportMasks().size());
            Assert.assertEquals(numExportMasks + numExportMasksAdded - 1, group.getExportMaskSet().size());

            modelClient.update(group);

            Assert.assertEquals(numExportMasks + numExportMasksAdded - 1, group.getExportMasks().size());
            Assert.assertEquals(numExportMasks + numExportMasksAdded - 1, group.getExportMaskSet().size());
            Assert.assertEquals(numSnapshots + numSnapshotsAdded - 1, group.getSnapshots().size());
            Assert.assertEquals(numSnapshots + numSnapshotsAdded - 1, group.getSnapshotSet().size());

            // use iterator to remove an item
            // TODO this is a gotcha: can't use iterator to manipulate the list
            // Iterator<ExportMask> maskItr = group.getExportMaskSet().iterator();
            // ExportMask maskToRemove2 = maskItr.next();
            // maskItr.remove();
            // Assert.assertEquals(numExportMasks+numExportMasksAdded-2, group.getExportMasks().size());
            // Assert.assertEquals(numExportMasks+numExportMasksAdded-2, group.getExportMaskSet().size());
            //
            // modelClient.update(group);
            // Assert.assertEquals(numExportMasks+numExportMasksAdded-2, group.getExportMasks().size());
            // Assert.assertEquals(numExportMasks+numExportMasksAdded-2, group.getExportMaskSet().size());

            // add a new ExportGroup with export masks added to the lazy loaded list
            ExportGroup group2 = new ExportGroup();
            group2.setId(URIUtil.createId(ExportGroup.class));
            group2.setLabel("group2");
            List<ExportMask> exportMasks2 = createDbObject(ExportMask.class, 3);
            for (ExportMask mask : exportMasks2) {
                group2.addExportMask(mask);
            }

            modelClient.create(group2);
            Assert.assertEquals(3, group2.getExportMasks().size());
            Assert.assertEquals(3, group2.getExportMaskSet().size());

            // add a new ExportGroup with export masks added to the lazy loaded list reseting the whole list
            ExportGroup group3 = new ExportGroup();
            group3.setId(URIUtil.createId(ExportGroup.class));
            group3.setLabel("group3");
            List<ExportMask> exportMasks3 = createDbObject(ExportMask.class, 3);
            group3.setExportMaskSet(exportMasks3);

            modelClient.create(group3);
            Assert.assertEquals(3, group3.getExportMasks().size());
            Assert.assertEquals(3, group3.getExportMaskSet().size());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testModifyStringSet() {
        // demonstrates that if we modify a stringset, the associated lazy loaded list reflects the change
        try {
            int numExportGroups = 1;

            int numExportMasks = 5;
            int numExportMasksAdded = 1;

            int numSnapshots = 3;
            int numSnapshotsAdded = 5;

            URI groupId = createExportGroups(numExportGroups, createDbObject(ExportMask.class, numExportMasks),
                    createDbObject(BlockSnapshot.class, numSnapshots)).iterator().next().getId();

            ExportGroup group = modelClient.find(ExportGroup.class, groupId);

            Assert.assertEquals(numExportMasks, group.getExportMaskSet().size());

            // add some more export masks to the stringset
            List<ExportMask> masks = createDbObject(ExportMask.class, numExportMasksAdded);
            for (ExportMask mask : masks) {
                group.addExportMask(mask.getId());
            }

            // test before we persist
            Assert.assertEquals(numExportMasks + numExportMasksAdded, group.getExportMaskSet().size());

            modelClient.update(group);

            // test again after we persist (the list should not get loaded again)
            Assert.assertEquals(numExportMasks + numExportMasksAdded, group.getExportMaskSet().size());

            List<BlockSnapshot> snapshotsToAdd = createDbObject(BlockSnapshot.class, numSnapshotsAdded);
            for (BlockSnapshot snapshot : snapshotsToAdd) {
                group.getSnapshots().add(snapshot.getId().toString());
            }

            // test before we persist
            Assert.assertEquals(numSnapshots + numSnapshotsAdded, group.getSnapshotSet().size());

            modelClient.update(group);

            // test again after we persist (the list should not get loaded again)
            Assert.assertEquals(numSnapshots + numSnapshotsAdded, group.getSnapshotSet().size());

            // now test removing things from stringsets
            group.removeExportMask(masks.iterator().next().getId());
            group.getSnapshots().remove(snapshotsToAdd.iterator().next().getId().toString());

            Assert.assertEquals(numExportMasks + numExportMasksAdded - 1, group.getExportMaskSet().size());
            Assert.assertEquals(numSnapshots + numSnapshotsAdded - 1, group.getSnapshotSet().size());

            modelClient.update(group);

            Assert.assertEquals(numExportMasks + numExportMasksAdded - 1, group.getExportMaskSet().size());
            Assert.assertEquals(numSnapshots + numSnapshotsAdded - 1, group.getSnapshotSet().size());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }

    }

    // @Ignore("not working")
    @Test
    public void testModifyDataObjectField() {
        ClassAOneToOne aInst = new ClassAOneToOne();
        aInst.setId(URIUtil.createId(ClassAOneToOne.class));
        aInst.setLabel("Instance of ClassA");

        ClassBOneToOne bInst = new ClassBOneToOne();
        bInst.setId(URIUtil.createId(ClassBOneToOne.class));
        bInst.setLabel("Instance of ClassB");

        aInst.setBinstance(bInst);

        modelClient.create(bInst);
        modelClient.create(aInst);

        ClassAOneToOne qAinst = modelClient.find(ClassAOneToOne.class, aInst.getId());

        Assert.assertNotNull(aInst.getBid());
        Assert.assertEquals(bInst.getId(), aInst.getBid());

        ClassBOneToOne bInst2 = new ClassBOneToOne();
        bInst2.setId(URIUtil.createId(ClassBOneToOne.class));
        bInst2.setLabel("Instance 2 of ClassB");
        modelClient.create(bInst2);

        qAinst.setBinstance(bInst2);

        Assert.assertNotNull(qAinst.getBid());
        Assert.assertEquals(bInst2.getId(), qAinst.getBid());

        modelClient.update(qAinst);
        ClassAOneToOne qAinst2 = modelClient.find(ClassAOneToOne.class, aInst.getId());

        Assert.assertNotNull(qAinst2.getBinstance());
        Assert.assertEquals(bInst2.getId(), qAinst2.getBinstance().getId());
    }

    @Test
    public void testModifyURIField() {
        ClassAOneToOne aInst = new ClassAOneToOne();
        aInst.setId(URIUtil.createId(ClassAOneToOne.class));
        aInst.setLabel("Instance of ClassA");

        ClassBOneToOne bInst = new ClassBOneToOne();
        bInst.setId(URIUtil.createId(ClassBOneToOne.class));
        bInst.setLabel("Instance of ClassB");

        aInst.setBinstance(bInst);

        modelClient.create(bInst);
        modelClient.create(aInst);

        ClassAOneToOne qAinst = modelClient.find(ClassAOneToOne.class, aInst.getId());

        Assert.assertNotNull(qAinst.getBid());
        Assert.assertEquals(bInst.getId(), qAinst.getBid());
        Assert.assertEquals(bInst.getId(), qAinst.getBinstance().getId());

        ClassBOneToOne bInst2 = new ClassBOneToOne();
        bInst2.setId(URIUtil.createId(ClassBOneToOne.class));
        bInst2.setLabel("Instance 2 of ClassB");
        modelClient.create(bInst2);

        qAinst.setBid(bInst2.getId());

        Assert.assertNotNull(qAinst.getBinstance());
        Assert.assertEquals(bInst2.getId(), qAinst.getBinstance().getId());

        modelClient.update(qAinst);
        ClassAOneToOne qAinst2 = modelClient.find(ClassAOneToOne.class, aInst.getId());

        Assert.assertNotNull(qAinst2.getBinstance());
        Assert.assertEquals(bInst2.getId(), qAinst2.getBinstance().getId());

    }

    @Test
    public void testConvertBetweenListAndIterator() {
        try {
            URI groupId = createExportGroups(1, createDbObject(ExportMask.class, 5), null).iterator().next().getId();

            ExportGroup group = modelClient.find(ExportGroup.class, groupId);

            // this will load iterator only
            Assert.assertTrue(group.getExportMaskSet().iterator().hasNext());

            // this will reload with a list
            Assert.assertEquals(5, group.getExportMaskSet().size());
        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testWithNewlyCreatedObj() {
        try {
            ExportGroup group = createExportGroups(1, createDbObject(ExportMask.class, 5), null).iterator().next();
            Assert.assertEquals(5, group.getExportMaskSet().size());

            group = modelClient.find(ExportGroup.class, group.getId());
            Assert.assertEquals(5, group.getExportMaskSet().size());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @Ignore("Unsupported")
    public void testCascadingInsert() {
        try {
            VirtualArray vArray = new VirtualArray();
            vArray.setId(URIUtil.createId(VirtualArray.class));
            vArray.setLabel("varray1");

            StorageSystem ss = new StorageSystem();
            ss.setId(URIUtil.createId(StorageSystem.class));
            ss.setLabel("storagesys1");

            // TODO : URI should be set automatically when we call setVArray()
            ss.setVirtualArray(vArray.getId());
            ss.setVArray(vArray);

            modelClient.create(ss);

            VirtualArray qv = modelClient.find(VirtualArray.class, vArray.getId());
            Assert.assertNotNull(qv.getStorageSystems());
            Assert.assertEquals(1, qv.getStorageSystems().size());

            StorageSystem qss = modelClient.find(StorageSystem.class, ss.getId());
            Assert.assertNotNull(qss.getVArray());
            Assert.assertEquals(vArray.getId(), qss.getVArray().getId());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void joinerTestMatchTest() {
        try {
            createExportGroups(1, createDbObject(ExportMask.class, 5), null).iterator().next().getId();
            Iterator<ExportMask> masks = modelClient.findAll(ExportMask.class);
            int count = 0;
            while (masks.hasNext()) {
                ExportMask mask = masks.next();
                mask.setIndexedField(count < 3 ? "test-value-1" : "test-value-2");
                mask.setUnIndexedField(count < 3 ? "test-value-1" : "test-value-2");
                modelClient.update(mask);
                count++;
            }

            List<ExportMask> qMasks = modelClient.join(ExportMask.class, "masks").match("indexedField", "test-value-2")
                    .go().list("masks");

            Assert.assertEquals(2, qMasks.size());

            List<ExportMask> qMasks2 = modelClient.join(ExportMask.class, "masks").match("unIndexedField", "test-value-1")
                    .go().list("masks");

            Assert.assertEquals(3, qMasks2.size());

            List<ExportMask> qMasks3 = modelClient.join(ExportGroup.class, "groups")
                    .join("groups", "exportMasks", ExportMask.class, "masks").match("indexedField", "test-value-2")
                    .go().list("masks");

            Assert.assertEquals(2, qMasks3.size());

            List<ExportMask> qMasks4 = modelClient.join(ExportGroup.class, "groups")
                    .join("groups", "exportMasks", ExportMask.class, "masks").match("unIndexedField", "test-value-1")
                    .go().list("masks");

            Assert.assertEquals(3, qMasks4.size());

        } catch (InstantiationException | IllegalAccessException e) {
            // TODO Auto-generated catch block
            _log.error(e.getMessage(), e);
        }

    }

    @Test
    public void joinerTest() {
        try {
            URI groupId = createExportGroups(1, createDbObject(ExportMask.class, 5), null).iterator().next().getId();
            ExportGroup group = modelClient.find(ExportGroup.class, groupId);

            Iterator<ExportMask> masks = modelClient.findAll(ExportMask.class);

            // URI device = URIUtil.createId(StorageDevice.class);
            List<StorageDevice> sdList = createDbObject(StorageDevice.class, 4);
            StorageDevice sd1 = sdList.get(0);
            StorageDevice sd2 = sdList.get(1);

            int count = 0;
            while (masks.hasNext()) {
                ExportMask mask = masks.next();
                mask.setStorageDevice(count < 3 ? sd1.getId() : sd2.getId());
                modelClient.update(mask);
                count++;
            }

            List<ExportMask> masks1 = modelClient.join(ExportGroup.class, "one")
                    .join("one", "exportMasks", ExportMask.class, "two")
                    .go().list("two");

            Assert.assertEquals(5, masks1.size());

            List<StorageDevice> devices1 = modelClient.join(ExportMask.class, "two")
                    .join("two", "storageDevice", StorageDevice.class, "three")
                    .go().list("three");
            Assert.assertEquals(2, devices1.size());

            List<StorageDevice> devices = modelClient.join(ExportGroup.class, "one")
                    .join("one", "exportMasks", ExportMask.class, "two")
                    .join("two", "storageDevice", StorageDevice.class, "three")
                    .go().list("three");

            Assert.assertEquals(2, devices.size());

            List<ExportMask> masksWithSdId1 = modelClient.join(StorageDevice.class, "one")
                    .match("label", sd1.getLabel())
                    .join("one", ExportMask.class, "two", "storageDevice")
                    .go().list("two");

            Assert.assertEquals(3, masksWithSdId1.size());

            List<ExportMask> masksWithSdId2 = modelClient.join(StorageDevice.class, "one")
                    .match("label", sd2.getLabel())
                    .join("one", ExportMask.class, "two", "storageDevice")
                    .go().list("two");

            Assert.assertEquals(2, masksWithSdId2.size());

            List<ExportMask> masks3 = modelClient.join(ExportMask.class, "one").match("storageDevice", sd1.getId())
                    .go().list("one");
            Assert.assertEquals(3, masks3.size());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }

    }

    @Test
    public void ModelClientTest() {
        try {

            URI vArrayId = createDbObject(VirtualArray.class, 1).iterator().next().getId();
            List<StorageSystem> systems = createStorageSystems(5, "system", vArrayId);

            Iterator<StorageSystem> queried = modelClient.find(StorageSystem.class, "varray", vArrayId);
            int num = 0;
            while (queried.hasNext()) {
                num++;
                queried.next();
            }
            Assert.assertEquals(systems.size(), num);

            queried = modelClient.find(StorageSystem.class, "varray", vArrayId.toString());
            num = 0;
            while (queried.hasNext()) {
                num++;
                queried.next();
            }
            Assert.assertEquals(systems.size(), num);

            StringSet stringset = new StringSet();
            stringset.add(vArrayId.toString());
            stringset.add(URIUtil.createId(VirtualArray.class).toString());

            queried = modelClient.find(StorageSystem.class, "varray", stringset);
            num = 0;
            while (queried.hasNext()) {
                num++;
                queried.next();
            }
            Assert.assertEquals(systems.size(), num);

            List<ExportMask> masks = createDbObject(ExportMask.class, 1);
            List<ExportGroup> groups = createExportGroups(5, masks, null);

            Iterator<ExportGroup> queriedGroups = modelClient.find(ExportGroup.class, "exportMasks", masks.iterator().next().getId());
            num = 0;
            while (queriedGroups.hasNext()) {
                num++;
                queriedGroups.next();
            }
            Assert.assertEquals(groups.size(), num);

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @Ignore("not supported")
    public void joinerTest2() {
        try {
            URI groupId = createExportGroups(1, createDbObject(ExportMask.class, 5), null).iterator().next().getId();
            ExportGroup group = modelClient.find(ExportGroup.class, groupId);

            List<ExportMask> masks = modelClient.join(ExportGroup.class, "one", group.getId())
                    .join("one", "exportMasksByLabel", ExportMask.class, "two").go().list("two");

            Assert.assertEquals(5, masks.size());

        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }

    }

    @Test
    public void testAddAllFromLLList() {
        try {
            URI groupId = createExportGroups(1, createDbObject(ExportMask.class, 3), createDbObject(BlockSnapshot.class, 3)).iterator()
                    .next().getId();
            Set<BlockSnapshot> snapshots = new HashSet<BlockSnapshot>();
            ExportGroup group = modelClient.find(ExportGroup.class, groupId);
            snapshots.addAll(group.getSnapshotSet());
            Assert.assertEquals(3, snapshots.size());
        } catch (Exception e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testOneToOne() {
        ClassAOneToOne aInst = new ClassAOneToOne();
        aInst.setId(URIUtil.createId(ClassAOneToOne.class));
        aInst.setLabel("Instance of ClassA");

        ClassBOneToOne bInst = new ClassBOneToOne();
        bInst.setId(URIUtil.createId(ClassBOneToOne.class));
        bInst.setLabel("Instance of ClassB");

        aInst.setBinstance(bInst);

        modelClient.create(aInst);
        modelClient.create(bInst);

        ClassAOneToOne qAinst = modelClient.find(ClassAOneToOne.class, aInst.getId());
        ClassBOneToOne lazyLoadedBinst = qAinst.getBinstance();

        Assert.assertNotNull(lazyLoadedBinst);
        Assert.assertEquals(bInst.getId(), lazyLoadedBinst.getId());

        ClassBOneToOne qBinst = modelClient.find(ClassBOneToOne.class, bInst.getId());
        ClassAOneToOne lazyLoadedAinst = qBinst.getAinstance();

        Assert.assertNotNull(lazyLoadedAinst);
        Assert.assertEquals(aInst.getId(), lazyLoadedAinst.getId());
    }

    @Test
    public void testOneToMany() {
        ClassAOneToMany aInst = new ClassAOneToMany();
        aInst.setId(URIUtil.createId(ClassAOneToMany.class));
        aInst.setLabel("Instance of ClassA");

        modelClient.create(aInst);

        ClassBOneToMany bInst1 = new ClassBOneToMany();
        bInst1.setId(URIUtil.createId(ClassBOneToMany.class));
        bInst1.setLabel("Instance (1) of ClassB");
        modelClient.create(bInst1);

        ClassBOneToMany bInst2 = new ClassBOneToMany();
        bInst2.setId(URIUtil.createId(ClassBOneToMany.class));
        bInst2.setLabel("Instance (2) of ClassB");
        modelClient.create(bInst2);

        StringSet bIds = new StringSet();
        bIds.add(bInst1.getId().toString());
        bIds.add(bInst2.getId().toString());
        aInst.setBids(bIds);

        // aInst.addB(bInst1);
        // aInst.addB(bInst2);

        modelClient.update(aInst);

        ClassAOneToMany qAinst = modelClient.find(ClassAOneToMany.class, aInst.getId());
        List<ClassBOneToMany> lazyLoadedBs = qAinst.getBinstances();

        Assert.assertNotNull(lazyLoadedBs);
        Assert.assertEquals(2, lazyLoadedBs.size());

        ClassBOneToMany qBinst = modelClient.find(ClassBOneToMany.class, lazyLoadedBs.iterator().next().getId());
        ClassAOneToMany lazyLoadedAinst = qBinst.getAinstance();

        Assert.assertNotNull(lazyLoadedAinst);
        Assert.assertEquals(aInst.getId(), lazyLoadedAinst.getId());
    }

    @Test
    public void testManyToMany() {
        ClassBManyToMany bInst1 = new ClassBManyToMany();
        bInst1.setId(URIUtil.createId(ClassBManyToMany.class));
        bInst1.setLabel("Instance (1) of ClassB");
        modelClient.create(bInst1);

        ClassBManyToMany bInst2 = new ClassBManyToMany();
        bInst2.setId(URIUtil.createId(ClassBManyToMany.class));
        bInst2.setLabel("Instance (2) of ClassB");
        modelClient.create(bInst2);

        StringSet bIds = new StringSet();
        bIds.add(bInst1.getId().toString());
        bIds.add(bInst2.getId().toString());

        ClassAManyToMany aInst1 = new ClassAManyToMany();
        aInst1.setId(URIUtil.createId(ClassAManyToMany.class));
        aInst1.setLabel("Instance (1) of ClassA");
        aInst1.setBids(bIds);
        modelClient.create(aInst1);

        ClassAManyToMany aInst2 = new ClassAManyToMany();
        aInst2.setId(URIUtil.createId(ClassAManyToMany.class));
        aInst2.setLabel("Instance (2) of ClassA");
        aInst2.setBids(bIds);
        modelClient.create(aInst2);

        StringSet aIds = new StringSet();
        aIds.add(aInst1.getId().toString());
        aIds.add(aInst2.getId().toString());

        bInst1.setAids(aIds);
        modelClient.update(bInst1);

        bInst2.setAids(aIds);
        modelClient.update(bInst1);

        ClassAManyToMany qAinst1 = modelClient.find(ClassAManyToMany.class, aInst1.getId());
        List<ClassBManyToMany> a1LazyLoadedBs = qAinst1.getBinstances();

        Assert.assertNotNull(a1LazyLoadedBs);
        Assert.assertEquals(2, a1LazyLoadedBs.size());

        ClassAManyToMany qAinst2 = modelClient.find(ClassAManyToMany.class, aInst1.getId());
        List<ClassBManyToMany> a2LazyLoadedBs = qAinst2.getBinstances();

        Assert.assertNotNull(a2LazyLoadedBs);
        Assert.assertEquals(2, a2LazyLoadedBs.size());

        ClassBManyToMany qBinst1 = modelClient.find(ClassBManyToMany.class, bInst1.getId());
        List<ClassAManyToMany> b1LazyLoadedAinstList = qBinst1.getAinstances();

        Assert.assertNotNull(b1LazyLoadedAinstList);
        Assert.assertEquals(2, b1LazyLoadedAinstList.size());

        ClassBManyToMany qBinst2 = modelClient.find(ClassBManyToMany.class, bInst1.getId());
        List<ClassAManyToMany> b2LazyLoadedAinstList = qBinst2.getAinstances();

        Assert.assertNotNull(b2LazyLoadedAinstList);
        Assert.assertEquals(2, b2LazyLoadedAinstList.size());
    }

    // The following inheritance structure exists for the below test (inheritanceTest()):
    //
    // DataObject (a)
    // ^
    // |
    // BlockObject (a) --------o VirtualArray (BlockObject contains a VirtualArray)
    // ^
    // / \
    // / \
    // BlockSnapshot Volume
    // ^
    // |
    // BlockMirror
    //
    @Test
    public void inheritanceTest() {
        try {
            URI vArrayId = createDbObject(VirtualArray.class, 1).iterator().next().getId();
            URI vArrayId2 = createDbObject(VirtualArray.class, 1).iterator().next().getId();

            List<Volume> vols = createDbObject(Volume.class, 5);

            for (Volume vol : vols) {
                vol.setVirtualArray(vArrayId);
            }

            modelClient.update(vols);

            List<BlockMirror> mirrors = createDbObject(BlockMirror.class, 5);

            for (BlockMirror mirror : mirrors) {
                mirror.setVirtualArray(vArrayId);
            }

            modelClient.update(mirrors);

            List<BlockSnapshot> snapshots = createDbObject(BlockSnapshot.class, 3);
            for (BlockSnapshot snapshot : snapshots) {
                snapshot.setVirtualArray(vArrayId);
            }
            modelClient.update(snapshots);

            // get all volumes for a varray using lazy loading
            VirtualArray qVarray = modelClient.find(VirtualArray.class, vArrayId);
            Assert.assertNotNull(qVarray.getVolumes());
            Assert.assertEquals(5, qVarray.getVolumes().size());

            // get the varray for a volume using lazy loading
            Volume qVol = modelClient.find(Volume.class, vols.iterator().next().getId());
            Assert.assertNotNull(qVol.getVirtualArrayObj());
            Assert.assertEquals(vArrayId, qVol.getVirtualArrayObj().getId());

            // get all block mirrors for a varray using lazy loading
            VirtualArray qVarray2 = modelClient.find(VirtualArray.class, vArrayId);
            Assert.assertNotNull(qVarray2.getVolumes());
            Assert.assertEquals(5, qVarray2.getVolumes().size());

            // get the virtual array for a block mirror using lazy loading
            BlockMirror qMirror = modelClient.find(BlockMirror.class, mirrors.iterator().next().getId());
            Assert.assertNotNull(qMirror.getVirtualArrayObj());
            Assert.assertEquals(vArrayId, qMirror.getVirtualArrayObj().getId());

            // get all volumes for a varray using joiner
            List<Volume> volsFromJoiner = modelClient.join(VirtualArray.class, "one").match("label", qVarray.getLabel())
                    .join("one", Volume.class, "two", "varray").go().list("two");
            Assert.assertEquals(5, volsFromJoiner.size());

            // get all mirrors for a varray using lazy loader
            List<BlockMirror> mirrorsFromJoiner = modelClient.join(VirtualArray.class, "one").match("label", qVarray.getLabel())
                    .join("one", BlockMirror.class, "two", "varray").go().list("two");
            Assert.assertEquals(5, mirrorsFromJoiner.size());

            // get the varray for a volume using Joiner
            Volume volume = mirrors.iterator().next();
            List<VirtualArray> volvArrayFromJoiner = modelClient.join(Volume.class, "one").match("label", volume.getLabel())
                    .join("one", "varray", VirtualArray.class, "two").go().list("two");
            Assert.assertEquals(1, volvArrayFromJoiner.size());

            // get the varray for a mirror using joiner
            BlockMirror mirror = mirrors.iterator().next();
            List<VirtualArray> mirrorvArrayFromJoiner = modelClient.join(BlockMirror.class, "one").match("label", mirror.getLabel())
                    .join("one", "varray", VirtualArray.class, "two").go().list("two");
            Assert.assertEquals(1, mirrorvArrayFromJoiner.size());

            // get all volumes and mirrors for a varray using joiner
            List<BlockObject> allBlockObjects = modelClient.join(VirtualArray.class, "one").match("label", qVarray.getLabel())
                    .join("one", BlockObject.class, "two", "varray").go().list("two");
            Assert.assertEquals(13, allBlockObjects.size());

        } catch (InstantiationException | IllegalAccessException e) {
            _log.error(e.getMessage(), e);
            Assert.fail(e.getMessage());
        }

    }

}
