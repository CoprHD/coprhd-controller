/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VPlexVolumeProtocolMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Tests the migration class, VPlexVolumeProtocolMigration, which ensures
 * the protocols field for VPLEX volumes is set to FC.
 */
public class VPlexVolumeProtocolMigrationTest extends DbSimpleMigrationTestBase {
    
    private static final Logger s_logger = LoggerFactory.getLogger(VPlexVolumeProtocolMigrationTest.class);
    
    private static final String DUMMY_PROTOCOL = "dummy";
    private static final String VPLEX_SYSTEM_LABEL = "VPlexSystem";
    private static final String VMAX_SYSTEM_LABEL = "VMAXSystem";
    private static final String VOLUME_WITHOUT_PROTOCOLS_LABEL = "VPlexVolumeNoProtocols";
    private static final String VOLUME_WITH_PROTOCOLS_LABEL = "VPlexVolumeWithProtocols";
    private static final String VOLUME_WITH_NO_SYSTEM_LABEL = "VPlexVolumeWithNoSystem";
    private static final String VOLUME_WITH_NON_VPLEX_SYSTEM_LABEL = "VPlexVolumeWithNonVPlexSystem";
    
    @BeforeClass
    public static void setup() throws IOException {
        
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
        {
            // Add your implementation of migration callback below.
            add(new VPlexVolumeProtocolMigration());
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
        s_logger.info("Preparing data for VPLEX volume protocol migration test.");
        
        // Prepare a VPLEX storage system.
        StorageSystem storageSystem = new StorageSystem();
        URI vplexSystemURI = URIUtil.createId(StorageSystem.class);
        storageSystem.setId(vplexSystemURI);
        storageSystem.setSystemType(DiscoveredDataObject.Type.vplex.name());
        storageSystem.setLabel(VPLEX_SYSTEM_LABEL);
        _dbClient.createObject(storageSystem);
        s_logger.info("Created VPLEX storage system {}", vplexSystemURI);
        
        // Prepare a non-VPLEX storage system.
        storageSystem = new StorageSystem();
        URI nonVplexSystemURI = URIUtil.createId(StorageSystem.class);
        storageSystem.setId(nonVplexSystemURI);
        storageSystem.setSystemType(DiscoveredDataObject.Type.vmax.name());
        storageSystem.setLabel(VMAX_SYSTEM_LABEL);
        _dbClient.createObject(storageSystem);
        s_logger.info("Created non-VPLEX storage system {}", nonVplexSystemURI);
        
        // Prepare a VPLEX volume with no protocols set.
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel(VOLUME_WITHOUT_PROTOCOLS_LABEL);
        volume.setStorageController(vplexSystemURI);
        _dbClient.createObject(volume);
        s_logger.info("Created VPLEX volume {} with no protocols set", volume.getId());
        
        // Prepare a VPLEX volume with protocols set.
        volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel(VOLUME_WITH_PROTOCOLS_LABEL);
        volume.setStorageController(vplexSystemURI);
        StringSet protocols = new StringSet();
        protocols.add(DUMMY_PROTOCOL);
        volume.setProtocol(protocols);
        _dbClient.createObject(volume);
        s_logger.info("Created VPLEX volume {} with protocols set", volume.getId());
        
        // Prepare a volume with no storage system.
        volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel(VOLUME_WITH_NO_SYSTEM_LABEL);
        protocols = new StringSet();
        protocols.add(DUMMY_PROTOCOL);
        volume.setProtocol(protocols);
        _dbClient.createObject(volume);
        s_logger.info("Created VPLEX volume {} with no system set", volume.getId());
        
        // Prepare a volume with a non-vplex storage system.
        volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel(VOLUME_WITH_NON_VPLEX_SYSTEM_LABEL);
        volume.setStorageController(nonVplexSystemURI);
        protocols = new StringSet();
        protocols.add(DUMMY_PROTOCOL);
        volume.setProtocol(protocols);
        _dbClient.createObject(volume);
        s_logger.info("Created VPLEX volume {} on non-VPLEX system", volume.getId());
    }

    @Override
    protected void verifyResults() throws Exception {
        s_logger.info("Verifying results for VPLEX volume protocol migration test.");
        
        List<URI> volumeURIs = _dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeURIs, true);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            StringSet protocols = volume.getProtocol();
            if (volume.getLabel().equals(VOLUME_WITHOUT_PROTOCOLS_LABEL)) {
                Assert.assertNotNull("Protocols should not be null.", protocols);
                Assert.assertEquals("The should be a single protocol.", protocols.size(), 1);
                Assert.assertEquals("The protocol should be FC", protocols.iterator().next(), StorageProtocol.Block.FC.name());
            } else {
                Assert.assertNotNull("Protocols should not be null.", protocols);
                Assert.assertEquals("The should be a single protocol.", protocols.size(), 1);
                Assert.assertEquals("The protocol should be the dummy test protocol", protocols.iterator().next(), DUMMY_PROTOCOL);                
            }
        }
    }
}
