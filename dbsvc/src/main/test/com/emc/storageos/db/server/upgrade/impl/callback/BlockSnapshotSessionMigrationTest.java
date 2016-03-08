/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.BlockSnapshotSessionMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test class for the BlockSnashotSessionMigration upgrade callback class.
 */
public class BlockSnapshotSessionMigrationTest extends DbSimpleMigrationTestBase {

    // Test constants.
    private static final String PROJECT_NAME = "Project";
    private static final String PARENT_NAME = "Parent";
    private static final String GRP_PARENT_NAME = "GrpParent";
    private static final String BASE_SNAPSHOT_NAME = "Snapshot";
    private static final String BASE_SNAPVX_SNAPSHOT_NAME = "SnapVx_Snapshot";
    private static final String BASE_GRP_SNAPVX_SNAPSHOT_NAME = "Grp_SnapVx_Snapshot";
    private static final String BASE_SETTINGS_INSTANCE = "Settings";
    private static final String BASE_GRP_SETTINGS_INSTANCE = "Grp_Settings";
    private static final String VMAX3_SYSTEM_FW_VERSION = "5977.xxx.xxx";
    private static final int SNAPSHOT_COUNT = 5;
    private static final int SNAPVX_SNAPSHOT_COUNT = 5;

    // A map of the snapshots whose system supports snapshot sessions keyed by the snapshot id.
    private static Map<String, BlockSnapshot> _linkedTargetsMap = new HashMap<String, BlockSnapshot>();

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionMigrationTest.class);

    /**
     * Setup method executed before test is executed.
     * 
     * @throws IOException
     */
    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("2.4", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;

            {
                // Add your implementation of migration callback below.
                add(new BlockSnapshotSessionMigration());
            }
        });

        // Adding this, which is typically executed in the base class
        // call, as it is needed to clear the DB file between runs.
        _dataDir = new File(dataDir);
        if (_dataDir.exists() && _dataDir.isDirectory()) {
            cleanDirectory(_dataDir);
        }
        _dataDir.mkdir();

        // Commenting this out as it prevents the migration callback
        // from being executed when the test is executed.
        // DbsvcTestBase.setup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getSourceVersion() {
        return "2.4";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTargetVersion() {
        return "3.0";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareData() throws Exception {
        s_logger.info("Preparing data for BlockSnapshotSession migration test.");
        prepareSingleSnapshotData();
        prepareGroupSnapshotData();
    }

    /**
     * Prepares single volume test data.
     */
    private void prepareSingleSnapshotData() {
        // A list of database object instances to be created.
        ArrayList<DataObject> newObjectsToBeCreated = new ArrayList<DataObject>();

        // Create some snapshots on a storage system that does not support
        // snapshot sessions. Snapshots on VNX do not currently support
        // snapshot sessions.
        StorageSystem system = new StorageSystem();
        URI systemURI = URIUtil.createId(StorageSystem.class);
        system.setId(systemURI);
        system.setSystemType(DiscoveredDataObject.Type.vnxblock.name());
        newObjectsToBeCreated.add(system);
        for (int i = 0; i < SNAPSHOT_COUNT; i++) {
            BlockSnapshot snapshot = new BlockSnapshot();
            URI snapshotURI = URIUtil.createId(BlockSnapshot.class);
            snapshot.setId(snapshotURI);
            snapshot.setLabel(BASE_SNAPSHOT_NAME + i);
            snapshot.setSnapsetLabel(snapshot.getLabel());
            URI projectURI = URIUtil.createId(Project.class);
            snapshot.setProject(new NamedURI(projectURI, PROJECT_NAME));
            URI parentURI = URIUtil.createId(Volume.class);
            snapshot.setParent(new NamedURI(parentURI, PARENT_NAME + i));
            snapshot.setSettingsInstance(BASE_SETTINGS_INSTANCE + i);
            snapshot.setStorageController(systemURI);
            newObjectsToBeCreated.add(snapshot);
        }

        // Now create some BlockSnapshot instances on a storage system
        // that does support snapshot sessions. VMAX3 is the only storage
        // system for which we currently support snapshot sessions. We
        // set up the system so that the method on StorageSystem
        // "checkIfVmax3" returns true.
        system = new StorageSystem();
        systemURI = URIUtil.createId(StorageSystem.class);
        system.setId(systemURI);
        system.setSystemType(DiscoveredDataObject.Type.vmax.name());
        system.setFirmwareVersion(VMAX3_SYSTEM_FW_VERSION);
        newObjectsToBeCreated.add(system);
        for (int i = 0; i < SNAPVX_SNAPSHOT_COUNT; i++) {
            BlockSnapshot snapshot = new BlockSnapshot();
            URI snapshotURI = URIUtil.createId(BlockSnapshot.class);
            snapshot.setId(snapshotURI);
            snapshot.setLabel(BASE_SNAPVX_SNAPSHOT_NAME + i);
            snapshot.setSnapsetLabel(snapshot.getLabel());
            URI projectURI = URIUtil.createId(Project.class);
            snapshot.setProject(new NamedURI(projectURI, PROJECT_NAME));
            URI parentURI = URIUtil.createId(Volume.class);
            snapshot.setParent(new NamedURI(parentURI, PARENT_NAME + i));
            snapshot.setSettingsInstance(BASE_SETTINGS_INSTANCE + i);
            snapshot.setStorageController(systemURI);
            newObjectsToBeCreated.add(snapshot);
            _linkedTargetsMap.put(snapshotURI.toString(), snapshot);
        }

        // Create the database objects.
        _dbClient.createObject(newObjectsToBeCreated);
    }

    /**
     * Prepares group snapshot test data.
     */
    private void prepareGroupSnapshotData() {
        // A list of database object instances to be created.
        ArrayList<DataObject> newObjectsToBeCreated = new ArrayList<DataObject>();

        // Create some group BlockSnapshot instances on a storage system
        // that supports snapshot sessions. VMAX3 is the only storage
        // system for which we currently support snapshot sessions. We
        // set up the system so that the method on StorageSystem
        // "checkIfVmax3" returns true.
        StorageSystem system = new StorageSystem();
        URI systemURI = URIUtil.createId(StorageSystem.class);
        system.setId(systemURI);
        system.setSystemType(DiscoveredDataObject.Type.vmax.name());
        system.setFirmwareVersion(VMAX3_SYSTEM_FW_VERSION);
        newObjectsToBeCreated.add(system);
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        URI cgURI = URIUtil.createId(BlockConsistencyGroup.class);
        cg.setId(cgURI);
        newObjectsToBeCreated.add(cg);
        for (int i = 0; i < SNAPVX_SNAPSHOT_COUNT; i++) {
            BlockSnapshot snapshot = new BlockSnapshot();
            URI snapshotURI = URIUtil.createId(BlockSnapshot.class);
            snapshot.setId(snapshotURI);
            snapshot.setLabel(BASE_GRP_SNAPVX_SNAPSHOT_NAME + i);
            snapshot.setSnapsetLabel(BASE_GRP_SNAPVX_SNAPSHOT_NAME);
            URI projectURI = URIUtil.createId(Project.class);
            snapshot.setProject(new NamedURI(projectURI, PROJECT_NAME));
            URI parentURI = URIUtil.createId(Volume.class);
            snapshot.setParent(new NamedURI(parentURI, GRP_PARENT_NAME + i));
            snapshot.setConsistencyGroup(cgURI);
            snapshot.setSettingsInstance(BASE_GRP_SETTINGS_INSTANCE);
            snapshot.setStorageController(systemURI);
            newObjectsToBeCreated.add(snapshot);
        }

        // Create the database objects.
        _dbClient.createObject(newObjectsToBeCreated);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyResults() throws Exception {
        s_logger.info("Verifying results for BlockSnapshotSession migration test.");
        verifySingleSnapshotResults();
        verifyGroupSnapshotResults();
    }

    /**
     * Verifies the migration results for single volume snapshots.
     */
    private void verifySingleSnapshotResults() {
        List<URI> snapSessionURIs = _dbClient.queryByType(BlockSnapshotSession.class, true);
        Iterator<BlockSnapshotSession> snapSessionsIter = _dbClient
                .queryIterativeObjects(BlockSnapshotSession.class, snapSessionURIs, true);
        Assert.assertTrue("Did not find any snapshot sessions after migration", snapSessionsIter.hasNext());
        int sessionCount = 0;
        while (snapSessionsIter.hasNext()) {
            BlockSnapshotSession snapSession = snapSessionsIter.next();
            // Process single volume snapshot sessions.
            if (NullColumnValueGetter.isNullURI(snapSession.getConsistencyGroup())) {
                sessionCount++;
                Assert.assertNotNull("Snapshot session is null", snapSession);
                StringSet linkedTargets = snapSession.getLinkedTargets();
                Assert.assertNotNull("Snapshot session linked targets list is null", snapSession);
                Assert.assertFalse("Snapshot session linked targets list is empty", linkedTargets.isEmpty());
                Assert.assertEquals("Snapshot session does not have a singled linked target", linkedTargets.size(), 1);
                String linkedTargetId = linkedTargets.iterator().next();
                Assert.assertTrue("Snapshot session linked target not in linked targets map",
                        _linkedTargetsMap.containsKey(linkedTargetId));
                BlockSnapshot linkedTarget = _linkedTargetsMap.remove(linkedTargetId);
                Assert.assertEquals("Label is not correct", linkedTarget.getLabel(), snapSession.getLabel());
                Assert.assertEquals("Session label is not correct", linkedTarget.getSnapsetLabel(), snapSession.getSessionLabel());
                Assert.assertEquals("Session instance is not correct", linkedTarget.getSettingsInstance(),
                        snapSession.getSessionInstance());
                Assert.assertEquals("Project is not correct", linkedTarget.getProject(), snapSession.getProject());
                Assert.assertEquals("Parent is not correct", linkedTarget.getParent(), snapSession.getParent());
            }
        }

        // Get the single volume snapshots in the database.
        // Note: Don't use List#size() as it is not supported by the derived
        // List class returned by the DB client.
        int svSnapshotCount = 0;
        List<URI> snapshotURIs = _dbClient.queryByType(BlockSnapshot.class, true);
        Iterator<BlockSnapshot> snapshotsIter = _dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
        while (snapshotsIter.hasNext()) {
            BlockSnapshot snapshot = snapshotsIter.next();
            if (NullColumnValueGetter.isNullURI(snapshot.getConsistencyGroup())) {
                svSnapshotCount++;
            }
        }
        Assert.assertEquals("Snapshot count is not correct", svSnapshotCount, SNAPVX_SNAPSHOT_COUNT + SNAPSHOT_COUNT);
        Assert.assertEquals("Snapshot session count is not correct", sessionCount, SNAPVX_SNAPSHOT_COUNT);
    }

    /**
     * Verifies group snapshot migration results.
     */
    private void verifyGroupSnapshotResults() {
        List<URI> snapSessionURIs = _dbClient.queryByType(BlockSnapshotSession.class, true);
        Iterator<BlockSnapshotSession> snapSessionsIter = _dbClient
                .queryIterativeObjects(BlockSnapshotSession.class, snapSessionURIs, true);
        Assert.assertTrue("Did not find any snapshot sessions after migration", snapSessionsIter.hasNext());
        int sessionCount = 0;
        while (snapSessionsIter.hasNext()) {
            BlockSnapshotSession snapSession = snapSessionsIter.next();
            // Process group snapshot sessions.
            if (!NullColumnValueGetter.isNullURI(snapSession.getConsistencyGroup())) {
                sessionCount++;
                Assert.assertNotNull("Snapshot session is null", snapSession);
                Assert.assertNull("Parent is not null", snapSession.getParent());
                StringSet linkedTargets = snapSession.getLinkedTargets();
                Assert.assertNotNull("Snapshot session linked targets list is null", snapSession);
                Assert.assertFalse("Snapshot session linked targets list is empty", linkedTargets.isEmpty());
                Assert.assertEquals("Snapshot session does not have the correct number fo linked targets", linkedTargets.size(),
                        SNAPVX_SNAPSHOT_COUNT);
                String linkedTargetId = linkedTargets.iterator().next();
                BlockSnapshot linkedTarget = _dbClient.queryObject(BlockSnapshot.class, URI.create(linkedTargetId));
                Assert.assertNotNull("Linked target is null", linkedTarget);
                Assert.assertEquals("Label is not correct", linkedTarget.getSnapsetLabel(), snapSession.getLabel());
                Assert.assertEquals("Session label is not correct", linkedTarget.getSnapsetLabel(), snapSession.getSessionLabel());
                Assert.assertEquals("Session instance is not correct", linkedTarget.getSettingsInstance(),
                        snapSession.getSessionInstance());
                Assert.assertEquals("Project is not correct", linkedTarget.getProject(), snapSession.getProject());
            }
        }

        // Get the groups snapshots in the database.
        // Note: Don't use List#size() as it is not supported by the derived
        // List class returned by the DB client.
        int grpSnapshotCount = 0;
        List<URI> snapshotURIs = _dbClient.queryByType(BlockSnapshot.class, true);
        Iterator<BlockSnapshot> snapshotsIter = _dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
        while (snapshotsIter.hasNext()) {
            BlockSnapshot snapshot = snapshotsIter.next();
            if (!NullColumnValueGetter.isNullURI(snapshot.getConsistencyGroup())) {
                grpSnapshotCount++;
            }
        }
        Assert.assertEquals("Snapshot count is not correct", grpSnapshotCount, SNAPVX_SNAPSHOT_COUNT);
        Assert.assertEquals("Snapshot session count is not correct", sessionCount, 1);
    }
}
