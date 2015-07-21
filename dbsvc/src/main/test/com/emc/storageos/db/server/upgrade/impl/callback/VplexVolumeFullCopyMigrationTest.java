/*
 * Copyright (c) 2014 EMC Corporation
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VplexVolumeFullCopyMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class VplexVolumeFullCopyMigrationTest extends DbSimpleMigrationTestBase{

    private static final Logger s_logger = LoggerFactory.getLogger(VplexVolumeFullCopyMigrationTest.class);

    private static final String VPLEX_VOLUME_LABEL = "VPlexVolume";
    private static final String VPLEX_FULL_COPY_VOLUME_LABEL = "VPlexVolumeFullCopy";
    
    private URI _srcVplexVolumeURI;
    private URI _vplexVolumeFullCopyURI;

    @BeforeClass
    public static void setup() throws IOException {
        
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
        {
            // Add your implementation of migration callback below.
            add(new VplexVolumeFullCopyMigration());
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
        s_logger.info("Preparing data for VPLEX volume full copy migration test.");
        
        // Create the virtual array for the source side.
        VirtualArray srcVarray = new VirtualArray();
        URI srcVarrayURI = URIUtil.createId(VirtualArray.class);
        srcVarray.setId(srcVarrayURI);
        _dbClient.createObject(srcVarray);
        s_logger.info("Created source side virtual array.");
        
        // Create the virtual array for the HA side.
        VirtualArray haVarray = new VirtualArray();
        URI haVarrayURI = URIUtil.createId(VirtualArray.class);
        haVarray.setId(haVarrayURI);
        _dbClient.createObject(haVarray);
        s_logger.info("Created HA side virtual array.");
        
        // Create a backend volume in the source varray.
        Volume srcBackendVolume = new Volume();
        URI srcBackendVolumeURI = URIUtil.createId(Volume.class);
        srcBackendVolume.setId(srcBackendVolumeURI);
        srcBackendVolume.setVirtualArray(srcVarrayURI);
        _dbClient.createObject(srcBackendVolume);
        s_logger.info("Created source backend volume for source VPLEX volume.");

        // Create a backend volume in the HA varray.
        Volume haBackendVolume = new Volume();
        URI haBackendVolumeURI = URIUtil.createId(Volume.class);
        haBackendVolume.setId(haBackendVolumeURI);
        haBackendVolume.setVirtualArray(haVarrayURI);        
        _dbClient.createObject(haBackendVolume);     
        s_logger.info("Created HA backend volume for source VPLEX volume.");
        
        // Create the VPLEX volume in the source varray using the 
        // source and HA backend volumes. This volume will be the 
        // source of a VPLEX full copy volume.
        Volume srcVplexVolume = new Volume();
        _srcVplexVolumeURI = URIUtil.createId(Volume.class);
        srcVplexVolume.setId(_srcVplexVolumeURI);
        srcVplexVolume.setVirtualArray(srcVarrayURI);
        srcVplexVolume.setLabel(VPLEX_VOLUME_LABEL);
        StringSet associatedVolumes = new StringSet();
        associatedVolumes.add(srcBackendVolumeURI.toString());
        associatedVolumes.add(haBackendVolumeURI.toString());
        srcVplexVolume.setAssociatedVolumes(associatedVolumes);
        _dbClient.createObject(srcVplexVolume);
        s_logger.info("Created source VPLEX volume.");

        // Create another backend volume in the source varray.
        Volume srcBackendVolumeForCopy = new Volume();
        URI srcBackendVolumeForCopyURI = URIUtil.createId(Volume.class);
        srcBackendVolumeForCopy.setId(srcBackendVolumeForCopyURI);
        srcBackendVolumeForCopy.setVirtualArray(srcVarrayURI);
        _dbClient.createObject(srcBackendVolumeForCopy);
        s_logger.info("Created source backend volume for full copy VPLEX volume.");

        // Create another backend volume in the HA varray.
        Volume haBackendVolumeForCopy = new Volume();
        URI haBackendVolumeForCopyURI = URIUtil.createId(Volume.class);
        haBackendVolumeForCopy.setId(haBackendVolumeForCopyURI);
        haBackendVolumeForCopy.setVirtualArray(haVarrayURI);
        _dbClient.createObject(haBackendVolumeForCopy);
        s_logger.info("Created HA backend volume for full copy VPLEX volume.");
        
        // Create another VPLEX volume in the source varray.
        // This volume will be the VPLEX full copy volume.
        Volume vplexVolumeFullCopy = new Volume();
        _vplexVolumeFullCopyURI = URIUtil.createId(Volume.class);
        vplexVolumeFullCopy.setId(_vplexVolumeFullCopyURI);
        vplexVolumeFullCopy.setVirtualArray(srcVarrayURI);
        vplexVolumeFullCopy.setLabel(VPLEX_FULL_COPY_VOLUME_LABEL);
        associatedVolumes = new StringSet();
        associatedVolumes.add(srcBackendVolumeForCopyURI.toString());
        associatedVolumes.add(haBackendVolumeForCopyURI.toString());
        vplexVolumeFullCopy.setAssociatedVolumes(associatedVolumes);
        _dbClient.createObject(vplexVolumeFullCopy);
        s_logger.info("Created full copy VPLEX volume.");

        // Setup the full copy relationship between the source
        // backend volumes of the VPLEX volume and VPLEX full copy
        // volumes.
        StringSet fullCopyVolumes = new StringSet();
        fullCopyVolumes.add(srcBackendVolumeForCopyURI.toString());
        srcBackendVolume.setFullCopies(fullCopyVolumes);
        srcBackendVolumeForCopy.setAssociatedSourceVolume(srcBackendVolumeURI);
        _dbClient.persistObject(srcBackendVolume);
        _dbClient.persistObject(srcBackendVolumeForCopy);
        s_logger.info("Establish full copy relationships betwen the backend source volumes.");
    }
    
    @Override
    protected void verifyResults() throws Exception {
        s_logger.info("Verifying results for VPLEX volume full copy migration test.");
        List<URI> volumeUris = _dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeUris, true);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            String label = volume.getLabel();
            if (label.equals(VPLEX_VOLUME_LABEL)) {
                StringSet fullCopies = volume.getFullCopies();
                Assert.assertEquals("VPLEX volume should have 1 full copy", fullCopies.size(), 1);
                Assert.assertEquals("VPLEX volume copy has wrong id", fullCopies.iterator().next(), _vplexVolumeFullCopyURI.toString());
            } else if (label.equals(VPLEX_FULL_COPY_VOLUME_LABEL)) {
                URI copySourceURI = volume.getAssociatedSourceVolume();
                Assert.assertNotNull("VPLEX copy has null source", copySourceURI);
                Assert.assertEquals("VPLEX copy has incorrect source", copySourceURI, _srcVplexVolumeURI);
            }
        }
    }    
}
