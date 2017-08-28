/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VolumeBootVolumeMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test upgrading a volume object to have a boot volume tag.
 * - Volume that does not have tags at all yet
 * - Volume that has other tags
 * - Volume that isn't a boot volume in the first place
 * - Volume not associated with a host in the first place
 */
public class VolumeBootVolumeMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(VolumeBootVolumeMigrationTest.class);

    // Used for migrations tests related to boot volume
    private static volatile HashMap<URI, URI> volumeToHostIds = new HashMap<URI, URI>();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new VolumeBootVolumeMigration());
            }
        });

        DbsvcTestBase.setup();
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

        Project proj = new Project();
        URI projectURI = URIUtil.createId(Project.class);
        String projectLabel = "project";
        proj.setId(projectURI);
        proj.setLabel(projectLabel);
        proj.setTenantOrg(new NamedURI(tenantOrgURI, projectLabel));
        _dbClient.createObject(proj);

        // Create a boot volume for a host with no tags
        Volume vol1 = new Volume();
        URI vol1URI = URIUtil.createId(Volume.class);
        vol1.setId(vol1URI);
        vol1.setLabel("VOL1-ABOOTVOLUME");
        vol1.setTenant(new NamedURI(tenantOrgURI, "provider"));
        _dbClient.createObject(vol1);

        // Create a host object with a boot volume
        Host host1 = new Host();
        URI host1URI = URIUtil.createId(Host.class);
        host1.setId(host1URI);
        host1.setHostName("Host1WithBootVol");
        host1.setBootVolumeId(vol1URI);
        volumeToHostIds.put(vol1URI, host1URI); 
        _dbClient.createObject(host1);

        // Create a boot volume for a host with existing tags
        Volume vol2 = new Volume();
        URI vol2URI = URIUtil.createId(Volume.class);
        vol2.setId(vol2URI);
        vol2.setLabel("VOL2-ABOOTVOLUME");
        vol2.setTenant(new NamedURI(tenantOrgURI, "provider"));
        ScopedLabel label = new ScopedLabel();
        label.setScope(tenantOrg.getId().toASCIIString());
        label.setLabel("vipr:someothertag="+vol2URI.toASCIIString());
        ScopedLabelSet labelSet = new ScopedLabelSet();
        labelSet.add(label);
        vol2.setTag(labelSet);
        _dbClient.createObject(vol2);

        // Create a host object with a boot volume
        Host host2 = new Host();
        URI host2URI = URIUtil.createId(Host.class);
        host2.setId(host2URI);
        host2.setHostName("Host2WithBootVol");
        host2.setBootVolumeId(vol2URI);
        volumeToHostIds.put(vol2URI, host2URI); 
        _dbClient.createObject(host2);

        // Create a volume with no host association
        Volume vol3 = new Volume();
        URI vol3URI = URIUtil.createId(Volume.class);
        vol3.setId(vol3URI);
        vol3.setLabel("VOL3-NOTABOOTVOLUME");
        vol3.setTenant(new NamedURI(tenantOrgURI, "provider"));
        volumeToHostIds.put(vol3URI, null); 
        _dbClient.createObject(vol3);
    }

    /**
     * Verifies the results for migrating volumes
     * 
     * @throws Exception When an error occurs verifying the Volume
     *             migration results.
     */
    private void verifyVolumeResults() throws Exception {
        log.info("Verifying updated Volume boot volume tag for VolumeBootVolumeMigration handler");
        for (Entry<URI, URI> volumeToHostId : volumeToHostIds.entrySet()) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeToHostId.getKey());

            if (volume.getLabel().equals("VOL1-ABOOTVOLUME")) {
                // Make sure volume1 has the new tag
                Assert.assertNotNull("Tag Set of volume 1 must be non-null", volume.getTag());
                Assert.assertTrue("Tag set of volume 1 must be non-empty", !volume.getTag().isEmpty());
                ScopedLabel sl = volume.getTag().iterator().next();
                Assert.assertEquals(sl.getScope(), volume.getTenant().getURI().toASCIIString());
                Assert.assertEquals(sl.getLabel(), "vipr:bootVolume=" + volumeToHostId.getValue().toASCIIString());
            }

            if (volume.getLabel().equals("VOL2-ABOOTVOLUME")) {
                // Make sure volume1 has the new tag
                Assert.assertNotNull("Tag Set of volume 1 must be non-null", volume.getTag());
                Assert.assertTrue("Tag set of volume 2 must be non-empty", !volume.getTag().isEmpty());
                boolean found = false;
                Iterator<ScopedLabel> slIter = volume.getTag().iterator();
                while (slIter.hasNext()) {
                    ScopedLabel sl = slIter.next();
                    if (sl.getScope().equals(volume.getTenant().getURI().toASCIIString()) &&
                        (sl.getLabel().equals("vipr:bootVolume=" + volumeToHostId.getValue().toASCIIString()))) {
                        found = true;
                    }
                }
                Assert.assertTrue("Tag not found in tag set", found);
            }
            
            if (volume.getLabel().equals("VOL3-NOTABOOTVOLUME")) {
                // Make sure volume1 has no tags
                Assert.assertNull("Tag Set of volume 3 must be null", volume.getTag());
            }
        }
    }
}
