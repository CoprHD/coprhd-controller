/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VolumeAccessStateLinkStatusMigration;
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
 * This class tests the following migration callback classes:
 * - BlockSnapshotConsistencyGroupMigration
 * - ProtectionSetToBlockConsistencyGroupMigration
 * - VolumeRpJournalMigration
 */
public class VolumeAccessStateLinkStatusMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(VolumeAccessStateLinkStatusMigrationTest.class);
    
    // Used for migrations tests related to access state and link status
    private static List<URI> volumeAccessStateLinkStatusURIs = new ArrayList<URI>();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new VolumeAccessStateLinkStatusMigration());
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
        prepareVolumeData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyVolumeResults();
    }
    
    /**
     * Prepares the data for RP volume tests.
     * 
     * @throws Exception When an error occurs preparing the RP volume data.
     */
    private void prepareVolumeData() throws Exception {
        log.info("Preparing Volumes for VolumeAccessStateLinkStatusMigration");
        TenantOrg tenantOrg = new TenantOrg();
        URI tenantOrgURI = URIUtil.createId(TenantOrg.class);
        tenantOrg.setId(tenantOrgURI);
        _dbClient.createObject(tenantOrg);
        volumeAccessStateLinkStatusURIs = new ArrayList<URI>();
        
        Project proj = new Project();
        URI projectURI = URIUtil.createId(Project.class);
        String projectLabel = "project";
        proj.setId(projectURI);
        proj.setLabel(projectLabel);        
        proj.setTenantOrg(new NamedURI(tenantOrgURI, projectLabel));
        _dbClient.createObject(proj);
        
        // Create RP source volume
        Volume sourceVolume = new Volume();
        URI sourceVolumeURI = URIUtil.createId(Volume.class); 
        volumeAccessStateLinkStatusURIs.add(sourceVolumeURI);
        sourceVolume.setId(sourceVolumeURI);        
        sourceVolume.setLabel("SOURCE");
        sourceVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
        sourceVolume.setRpCopyName("COPY");
        _dbClient.createObject(sourceVolume);

        // Create RP target volume
        Volume targetVolume = new Volume();
        URI targetVolumeURI = URIUtil.createId(Volume.class); 
        volumeAccessStateLinkStatusURIs.add(targetVolumeURI);
        targetVolume.setId(targetVolumeURI);        
        targetVolume.setLabel("TARGET");
        targetVolume.setPersonality(Volume.PersonalityTypes.TARGET.toString());
        targetVolume.setRpCopyName("COPY");
        _dbClient.createObject(targetVolume);

        // Create RP journal volume
        Volume journalVolume = new Volume();
        URI journalVolumeURI = URIUtil.createId(Volume.class); 
        volumeAccessStateLinkStatusURIs.add(journalVolumeURI);
        journalVolume.setId(journalVolumeURI);        
        journalVolume.setLabel("METADATA");
        journalVolume.setPersonality(Volume.PersonalityTypes.METADATA.toString());
        journalVolume.setRpCopyName("COPY");
        _dbClient.createObject(journalVolume);

        // Create SRDF source volume
        Volume srdfSourceVolume = new Volume();
        URI srdfSourceVolumeURI = URIUtil.createId(Volume.class); 
        volumeAccessStateLinkStatusURIs.add(srdfSourceVolumeURI);
        srdfSourceVolume.setId(srdfSourceVolumeURI);        
        srdfSourceVolume.setLabel("SOURCE");
        srdfSourceVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
        srdfSourceVolume.setSrdfParent(new NamedURI(sourceVolume.getId(), "source-srdf"));
        _dbClient.createObject(srdfSourceVolume);

        // Create SRDF target volume
        Volume srdfTargetVolume = new Volume();
        URI srdfTargetVolumeURI = URIUtil.createId(Volume.class); 
        volumeAccessStateLinkStatusURIs.add(srdfTargetVolumeURI);
        srdfTargetVolume.setId(srdfTargetVolumeURI);        
        srdfTargetVolume.setLabel("TARGET");
        srdfTargetVolume.setPersonality(Volume.PersonalityTypes.TARGET.toString());
        srdfTargetVolume.setSrdfParent(new NamedURI(targetVolume.getId(), "target-srdf"));
        _dbClient.createObject(srdfTargetVolume);
        
        // Create a "normal" volume
        Volume volume = new Volume();
        URI volumeURI = URIUtil.createId(Volume.class); 
        volumeAccessStateLinkStatusURIs.add(volumeURI);
        volume.setId(volumeURI);        
        volume.setLabel("NORMAL");
        _dbClient.createObject(volume);
    }

    /**
     * Verifies the results for migrating volumes
     * 
     * @throws Exception When an error occurs verifying the Volume
     *         migration results.
     */
    private void verifyVolumeResults() throws Exception {
    	log.info("Verifying updated Volume source/target Volume results for VolumeAccessStateLinkStatusMigration.");
    	for (URI volumeURI : volumeAccessStateLinkStatusURIs) {
    		Volume volume = _dbClient.queryObject(Volume.class, volumeURI);

    		// Ensure that the source and target volumes have been assigned journal volume reference
    		if (volume.getPersonality() != null) {
    			if (volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString())) {
    				// All source volumes must have access state of READWRITE
    				Assert.assertTrue("Source volume MUST be READWRITE", volume.getAccessState().equals(Volume.VolumeAccessState.READWRITE.toString()));
    				Assert.assertTrue("Source volume MUST be IN_SYNC", volume.getLinkStatus().equals(Volume.LinkStatus.IN_SYNC.toString()));
    			} else if (volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString())) {
    				Assert.assertTrue("Target volume MUST be NOT_READY", volume.getAccessState().equals(Volume.VolumeAccessState.NOT_READY.toString()));
    				Assert.assertTrue("Target volume MUST be IN_SYNC", volume.getLinkStatus().equals(Volume.LinkStatus.IN_SYNC.toString()));
    			} else if (volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.METADATA.toString())) {
    				Assert.assertTrue("Metadata volume MUST be NOT_READY", volume.getAccessState().equals(Volume.VolumeAccessState.NOT_READY.toString()));
    			} else {
    				Assert.assertTrue("Volume MUST be READWRITE", volume.getAccessState().equals(Volume.VolumeAccessState.READWRITE.toString()));
    			}
    		} else {
    			Assert.assertTrue("Volume MUST be READWRITE", volume.getAccessState().equals(Volume.VolumeAccessState.READWRITE.toString()));
    		}
    	}
    }
}
