/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

import org.junit.BeforeClass;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.DataObjectInternalFlagsInitializer;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

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
 */
public class DbObjInternalFlagsMigrationTest extends DbSimpleMigrationTestBase {
    
    // Used for migrations tests related to VPLEX volumes.
    private static List<URI> vplexTestVolumeURIs = new ArrayList<URI>();
    
    // Used for migrations tests related to RP volumes.
    private static List<URI> rpTestVolumeURIs = new ArrayList<URI>();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.0", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new DataObjectInternalFlagsInitializer());
        }});

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "1.0";
    }

    @Override
    protected String getTargetVersion() {
        return "1.1";
    }

    @Override
    protected void prepareData() throws Exception {
        prepareFileShareData(); 
        prepareVolumeData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyFileShareResults();
        verifyVolumeResults();
    }

    private void prepareFileShareData() throws Exception {
        String currentLabel = "onePublic";
        
        // create a couple of public FileShares that should be ignored by the migration callback
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));        
        fs.setLabel(currentLabel);
        fs.setProject(new NamedURI(URI.create("urn:" + currentLabel), currentLabel));
        _dbClient.createObject(fs);
        
        currentLabel = "twoPublic";
        fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));        
        fs.setLabel(currentLabel);
        fs.setProject(new NamedURI(URI.create("urn:" + currentLabel), currentLabel));
        _dbClient.createObject(fs);
        
        // create a couple of internal (project==null) FileShares that should be migrated
        currentLabel = "oneInternal";
        fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));        
        fs.setLabel(currentLabel);
        fs.setProject(null);
        _dbClient.createObject(fs);

        currentLabel = "twoInternal";
        fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));        
        fs.setLabel(currentLabel);
        fs.setProject(null);
        _dbClient.createObject(fs);
        
        // make sure our test data made it into the database as expected
        List<URI> fileShareKeys = _dbClient.queryByType(FileShare.class, false);
        int count = 0;       
        for (@SuppressWarnings("unused") URI ignore :fileShareKeys) {
            count++;
        }
        Assert.assertTrue("expected 4 prepared FileShares, found only " + count, count == 4); 
    }

    private void verifyFileShareResults() throws Exception {
        
        List<URI> fileShareKeys = _dbClient.queryByType(FileShare.class, false);
        int count = 0;
        Iterator<FileShare> fileShareObjs =
                _dbClient.queryIterativeObjects(FileShare.class, fileShareKeys);
        while (fileShareObjs.hasNext()) {
            FileShare fs = fileShareObjs.next();
            count++;
            if (fs.getLabel().contains("Internal")) {
                Assert.assertNotNull("internal project should not be null after migration", fs.getProject());
                Assert.assertEquals("internal project should equal the internal project constant after migration", 
                        fs.getProject().getURI(), FileShare.INTERNAL_OBJECT_PROJECT_URN);
                Assert.assertTrue("INTERNAL_OBJECT should be set on internal fs", 
                        fs.checkInternalFlags(Flag.INTERNAL_OBJECT));
                Assert.assertTrue("NO_PUBLIC_ACCESS should be set on internal fs", 
                        fs.checkInternalFlags(Flag.NO_PUBLIC_ACCESS));
                Assert.assertTrue("NO_METERING should be set on internal fs", 
                        fs.checkInternalFlags(Flag.NO_METERING));                
            } else if (fs.getLabel().contains("Public")) {
                Assert.assertFalse("INTERNAL_OBJECT should not be set on public fs", fs.checkInternalFlags(Flag.INTERNAL_OBJECT));
                Assert.assertFalse("NO_PUBLIC_ACCESS should not be set on public fs", fs.checkInternalFlags(Flag.NO_PUBLIC_ACCESS));
                Assert.assertFalse("NO_METERING should not be set on public fs", fs.checkInternalFlags(Flag.NO_METERING));                                
            }
        }
        Assert.assertTrue("we should still have 4 files shares after migration, not " + count, count == 4);        
    }
    
    /**
     * Prepares the data for volume tests.
     * 
     * @throws Exception When an error occurs preparing the volume data.
     */
    private void prepareVolumeData() throws Exception {
       
        // Prepare the data for testing VPLEX volumes.
        prepareVPlexVolumeData();
        
        // Prepare the data for testing RP volumes.
        prepareRPVolumeData();
    }
    
    /**
     * Verifies the migration results for volumes.
     * 
     * @throws Exception When an error occurs verifying the volume migration
     *         results.
     */
    private void verifyVolumeResults() throws Exception {
        
        // Verify the results for VPLEX volumes
        verifyVPlexVolumeResults();
        
        // Verify the results for RP volumes
        verifyRPVolumeResults();
    }
    
    /**
     * Prepares the data for VPLEX volume tests.
     * 
     * @throws Exception When an error occurs preparing the VPLEX volume data.
     */
    private void prepareVPlexVolumeData() throws Exception {
        
        // Create two volumes to be used as backend volumes 
        // for a VPLEX volume.
        Volume vplexBackendVolume1 = new Volume();
        URI vplexBackendVolume1URI = URIUtil.createId(Volume.class);
        vplexTestVolumeURIs.add(vplexBackendVolume1URI);
        vplexBackendVolume1.setId(vplexBackendVolume1URI);        
        vplexBackendVolume1.setLabel("VPlexBackendVolume1");
        _dbClient.createObject(vplexBackendVolume1);
        Volume vplexBackendVolume2 = new Volume();
        URI vplexBackendVolume2URI = URIUtil.createId(Volume.class); 
        vplexTestVolumeURIs.add(vplexBackendVolume2URI);
        vplexBackendVolume2.setId(vplexBackendVolume2URI);        
        vplexBackendVolume2.setLabel("VPlexBackendVolume2");
        _dbClient.createObject(vplexBackendVolume2);
        
        // Get the ids of the VPLEX backend volumes.
        StringSet associatedVolumeIds = new StringSet();
        associatedVolumeIds.add(vplexBackendVolume1.getId().toString());
        associatedVolumeIds.add(vplexBackendVolume2.getId().toString());
        
        // Create the VPLEX volume, settings its associated
        // volumes to the ids of the two backend volumes.
        Volume vplexVolume = new Volume();
        URI vplexVolumeURI = URIUtil.createId(Volume.class);
        vplexTestVolumeURIs.add(vplexVolumeURI);
        vplexVolume.setId(vplexVolumeURI);        
        vplexVolume.setLabel("VPlexVolume");
        vplexVolume.setAssociatedVolumes(associatedVolumeIds);
        _dbClient.createObject(vplexVolume);
        
        // Verify the VPLEX volume data exists in the database.
        for (URI volumeURI : vplexTestVolumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            Assert.assertNotNull(String.format("VPLEX test volume %s not found", volumeURI), volume);
        }
    }

    /**
     * Verifies the migration results for volumes.
     * 
     * @throws Exception When an error occurs verifying the VPLEX volume
     *         migration results.
     */
    private void verifyVPlexVolumeResults() throws Exception {
        
        for (URI volumeURI : vplexTestVolumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            Assert.assertNotNull(String.format("VPLEX test volume %s not found", volumeURI), volume);
            StringSet associatedVolumes = volume.getAssociatedVolumes();
            if ((associatedVolumes != null) && (!associatedVolumes.isEmpty())) {
                // This is the VPLEX volume.
                Assert.assertFalse("INTERNAL_OBJECT should NOT be set for a VPLEX volume", 
                    volume.checkInternalFlags(Flag.INTERNAL_OBJECT));
            } else {
                // This is one of the backend volumes.
                Assert.assertTrue("INTERNAL_OBJECT should be set for a VPLEX backend volume", 
                    volume.checkInternalFlags(Flag.INTERNAL_OBJECT));
            }
        }
    }

    /**
     * Prepares the data for RP volume tests.
     * 
     * @throws Exception When an error occurs preparing the RP volume data.
     */
    private void prepareRPVolumeData() throws Exception {
        
        Volume rpSourceVolume = new Volume();
        URI rpSourceVolumeURI = URIUtil.createId(Volume.class); 
        rpTestVolumeURIs.add(rpSourceVolumeURI);
        rpSourceVolume.setId(rpSourceVolumeURI);        
        rpSourceVolume.setLabel("rpSourceVolume");
        rpSourceVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
        _dbClient.createObject(rpSourceVolume);
         
        Volume rpTargetVolume = new Volume();
        URI rpTargetVolumeURI = URIUtil.createId(Volume.class); 
        rpTestVolumeURIs.add(rpTargetVolumeURI);
        rpTargetVolume.setId(rpTargetVolumeURI);        
        rpTargetVolume.setLabel("rpTargetVolume");
        rpTargetVolume.setPersonality(Volume.PersonalityTypes.TARGET.toString());
        _dbClient.createObject(rpTargetVolume);
         
        Volume rpSourceJournalVolume = new Volume();
        URI rpSourceJournalVolumeURI = URIUtil.createId(Volume.class);
        rpTestVolumeURIs.add(rpSourceJournalVolumeURI);
        rpSourceJournalVolume.setId(rpSourceJournalVolumeURI);        
        rpSourceJournalVolume.setLabel("rpSourceJournalVolume");
        rpSourceJournalVolume.setPersonality(Volume.PersonalityTypes.METADATA.toString());
        _dbClient.createObject(rpSourceJournalVolume);
        
        Volume rpTargetJournalVolume = new Volume();
        URI rpTargetJournalVolumeURI = URIUtil.createId(Volume.class); 
        rpTestVolumeURIs.add(rpTargetJournalVolumeURI);
        rpTargetJournalVolume.setId(rpTargetJournalVolumeURI);        
        rpTargetJournalVolume.setLabel("rpTargetJournalVolume");
        rpTargetJournalVolume.setPersonality(Volume.PersonalityTypes.METADATA.toString());
        _dbClient.createObject(rpTargetJournalVolume);
        
        // Verify the rp volume data exists in the database.
        for (URI volumeURI : rpTestVolumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            Assert.assertNotNull(String.format("rp test volume %s not found", volumeURI), volume);
        }
    }

    /**
     * Verifies the migration results for volumes.
     * 
     * @throws Exception When an error occurs verifying the VPLEX volume
     *         migration results.
     */
    private void verifyRPVolumeResults() throws Exception {
        
        for (URI volumeURI : rpTestVolumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            Assert.assertNotNull(String.format("rp test volume %s not found", volumeURI), volume);
         
            if ((volume.getPersonality() != null) 
            		&& !volume.getPersonality().equals(Volume.PersonalityTypes.METADATA.toString())) {
                // This is not a RP Journal volume.
                Assert.assertFalse("INTERNAL_OBJECT should NOT be set for a RP volume", 
                    volume.checkInternalFlags(Flag.INTERNAL_OBJECT));
                Assert.assertFalse("SUPPORTS_FORCE should NOT be set for a RP volume", 
                        volume.checkInternalFlags(Flag.SUPPORTS_FORCE));
            } 
            
            if ((volume.getPersonality() != null) 
            		&& volume.getPersonality().equals(Volume.PersonalityTypes.METADATA.toString())) {
                // This is a RP Journal volume.
                Assert.assertTrue("INTERNAL_OBJECT should be set for a RP Journal volume", 
                    volume.checkInternalFlags(Flag.INTERNAL_OBJECT));
                Assert.assertTrue("SUPPORTS_FORCE should be set for a RP Journal volume", 
                        volume.checkInternalFlags(Flag.SUPPORTS_FORCE));
            }
        }
    }
}
