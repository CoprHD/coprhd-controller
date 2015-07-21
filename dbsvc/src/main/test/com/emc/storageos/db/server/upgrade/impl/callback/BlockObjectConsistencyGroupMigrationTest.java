/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.BlockObjectConsistencyGroupMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

/**
 * Test proper population of the new DataObject.internalFlags field
 * 
 * Here's the basic execution flow for the test case:
 * - setup() runs, bringing up a "pre-migration" version
 *   of the database, using the DbSchemaScannerInterceptor
 *   you supply to hide your new field or column family
 *   when generating the "before" schema. 
 * - Your implementation of prepareData() is called, allowing
 *   you to use the internal _dbClient reference to create any 
 *   needed pre-migration test data.
 * - The database is then shutdown and restarted (without using
 *   the interceptor this time), so the full "after" schema
 *   is available.
 * - The dbsvc detects the diffs in the schema and executes the
 *   migration callbacks as part of the startup process.
 * - Your implementation of verifyResults() is called to
 *   allow you to confirm that the migration of your prepared
 *   data went as expected.
 * 
 * This class tests the following migration callback classes:
 * - BlockObjectConsistencyGroupMigration
 */
public class BlockObjectConsistencyGroupMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(BlockObjectConsistencyGroupMigrationTest.class);
    
    // Used for migrations tests related to BlockSnapshots.
    private static List<URI> testBlockSnapshotURIs = new ArrayList<URI>();

    // Used for migrations tests related to RP ProtectionSets.
    private static List<URI> testBlockMirrorURIs = new ArrayList<URI>();
    
    // Used for migrations tests related to Volumes.
    private static List<URI> testVolumeURIs = new ArrayList<URI>();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new BlockObjectConsistencyGroupMigration());
        }});

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "1.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.0";
    }

    @Override
    protected void prepareData() throws Exception { 
        createVolumeData("migrationVolume", 10);
        createBlockSnapshotData("migrationBlockSnapshot", 10);
        createBlockMirrorData("migrationBlockMirror", 10);
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyBlockObjectResults();
    }
    
    /**
     * Creates the consistency group used by the BlockObjects.
     * @param name
     * @return
     */
    private URI createBlockConsistencyGroup(String name) {
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        URI cgURI = URIUtil.createId(BlockConsistencyGroup.class);
        cg.setId(cgURI);
        cg.setLabel(name);
        _dbClient.createObject(cg);
        return cg.getId();
    }
    
    /**
     * Creates the BlockObject Volume data.
     * 
     * @param volumeName
     * @param numTargets
     */
    private List<Volume> createVolumeData(String name, int numVolumes) {
        List<Volume> volumes = new ArrayList<Volume>();
        URI cgUri = createBlockConsistencyGroup(name + "-cg");
        for (int i = 1; i <= numVolumes; i++) {
            Volume volume = new Volume();
            URI volumeURI = URIUtil.createId(Volume.class);
            testVolumeURIs.add(volumeURI);
            volume.setId(volumeURI);        
            volume.setLabel(name + i);
            volume.setConsistencyGroup(cgUri);
            _dbClient.createObject(volume);
        }
        
        return volumes;
    }
    
    /**
     * Creates the BlockObject BlockSnapshot data.
     * @param name
     * @param numSnapshots
     * @throws Exception
     */
    private void createBlockSnapshotData(String name, int numSnapshots) throws Exception {
        // Create the volume for the snapshots
        Volume volume = new Volume();
        URI volumeURI = URIUtil.createId(Volume.class);
        testVolumeURIs.add(volumeURI);
        volume.setId(volumeURI);       
        String volName = "snapVolume";
        volume.setLabel(volName);
        URI cgUri = createBlockConsistencyGroup(volName + "-cg");
        volume.setConsistencyGroup(cgUri);
        _dbClient.createObject(volume);

        for (int i = 1; i <= numSnapshots; i++) {
            BlockSnapshot blockSnapshot = new BlockSnapshot();
            URI blockSnapshotURI = URIUtil.createId(BlockSnapshot.class);
            testBlockSnapshotURIs.add(blockSnapshotURI);
            blockSnapshot.setId(blockSnapshotURI);
            blockSnapshot.setLabel(name + i);
            blockSnapshot.setSnapsetLabel(name + i);
            blockSnapshot.setParent(new NamedURI(volume.getId(), name + i));
            blockSnapshot.setConsistencyGroup(cgUri);
            _dbClient.createObject(blockSnapshot);
            
            BlockSnapshot querySnap = _dbClient.queryObject(BlockSnapshot.class, blockSnapshotURI);
        }
    }
    
    /**
     * Creates the BlockObject BlockMirror data.
     * @param name
     * @param numSnapshots
     * @throws Exception
     */
    private void createBlockMirrorData(String name, int numBlockMirrors) throws Exception {
        // Create the volume for the snapshots
        Volume volume = new Volume();
        URI volumeURI = URIUtil.createId(Volume.class);
        testVolumeURIs.add(volumeURI);
        volume.setId(volumeURI);        
        volume.setLabel("blockMirrorVolume");
        URI cgUri = createBlockConsistencyGroup("blockMirrorVolume-cg");
        volume.setConsistencyGroup(cgUri);
        _dbClient.createObject(volume);

        for (int i = 1; i <= numBlockMirrors; i++) {
            BlockMirror blockMirror = new BlockMirror();
            URI blockMirrorURI = URIUtil.createId(BlockMirror.class);
            testBlockMirrorURIs.add(blockMirrorURI);
            blockMirror.setId(blockMirrorURI);
            blockMirror.setLabel(name + i);
            blockMirror.setConsistencyGroup(cgUri);
            _dbClient.createObject(blockMirror);
        }
    }    

    /**
     * Verifies that the migration has worked properly.  Checks all of the Volume, BlockSnapshot,
     * and BlockMirror objects to ensure:
     * 1) The old consistencyGroup field is null
     * 2) The new consistencyGroups field is not null
     * 3) The new consistencyGruops field is not empty
     * @throws Exception
     */
    private void verifyBlockObjectResults() throws Exception {
        log.info("Verifying migration of BlockObject.consistencyGroup to BlockObject.consistencyGroups.");
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        
        // get the volumes
        Iterator<Volume> volumeItr =
                _dbClient.queryIterativeObjects(Volume.class, testVolumeURIs);
        // Get the block snapshots
        Iterator<BlockSnapshot> blockSnapshotItr =
                _dbClient.queryIterativeObjects(BlockSnapshot.class, testBlockSnapshotURIs);
        // Get the block snapshots
        Iterator<BlockMirror> blockMirrorItr =
                _dbClient.queryIterativeObjects(BlockMirror.class, testBlockMirrorURIs);
        
        while (volumeItr.hasNext()) {
            blockObjects.add(volumeItr.next());
        }
        while (blockSnapshotItr.hasNext()) {
            blockObjects.add(blockSnapshotItr.next());
        }
        while (blockMirrorItr.hasNext()) {
            blockObjects.add(blockMirrorItr.next());
        }
        
        for (BlockObject blockObject : blockObjects) {
            Assert.assertTrue("Volume.consistencyGroup field should be null.", 
                    blockObject.getConsistencyGroup().equals(NullColumnValueGetter.getNullURI()));
            Assert.assertNotNull("Volume.consistencyGroups field should contain at least 1 consistency group.", 
                    blockObject.getConsistencyGroups());
            Assert.assertTrue("Volume.consistencyGroups field should contain at least 1 consistency group.", 
                    !blockObject.getConsistencyGroups().isEmpty());
        }   
    }    
}
