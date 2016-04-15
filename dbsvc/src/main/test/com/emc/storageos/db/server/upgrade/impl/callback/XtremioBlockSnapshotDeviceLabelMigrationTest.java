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
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.XtremioBlockSnapshotDeviceLabelMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class XtremioBlockSnapshotDeviceLabelMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(XtremioBlockSnapshotDeviceLabelMigrationTest.class);
    private static final String SNAPSHOT_LABEL = "snapshotLabel";

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.3", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new XtremioBlockSnapshotDeviceLabelMigration());
            }
        });

        DbsvcTestBase.setup();
        log.info("completed setup");
    }

    @Override
    protected String getSourceVersion() {
        return "2.3";
    }

    @Override
    protected String getTargetVersion() {
        return "2.4";
    }

    @Override
    protected void prepareData() throws Exception {
        createXIOSnapshots();
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> storageSystemURIList = _dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> storageSystems = _dbClient.queryIterativeObjects(StorageSystem.class, storageSystemURIList);
        while (storageSystems.hasNext()) {
            StorageSystem storageSystem = storageSystems.next();
            if (DiscoveredDataObject.Type.xtremio.name().equalsIgnoreCase(storageSystem.getSystemType())) {
                URIQueryResultList snapshotURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceSnapshotConstraint(storageSystem.getId()),
                        snapshotURIs);
                Iterator<BlockSnapshot> xioSnapshots = _dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
                while (xioSnapshots.hasNext()) {
                    BlockSnapshot xioSnapshot = xioSnapshots.next();
                    Assert.assertNotNull("XtremIO snapshot should have deviceLabel set", xioSnapshot.getDeviceLabel());
                    Assert.assertTrue("deviceLabel should be set to " + SNAPSHOT_LABEL,
                            xioSnapshot.getDeviceLabel().equals(SNAPSHOT_LABEL));
                }
            }
        }
    }

    private void createXIOSnapshots() throws Exception {
        log.info("Preparing BlockSnapshot for XtremioBlockSnapshotDeviceLabelMigrationTest");
        StorageSystem xioStorageSystem = new StorageSystem();
        xioStorageSystem.setId(URIUtil.createId(StorageSystem.class));
        xioStorageSystem.setSystemType(DiscoveredDataObject.Type.xtremio.name());
        _dbClient.createObject(xioStorageSystem);

        Volume parentVolume = new Volume();
        URI volumeUri = URIUtil.createId(Volume.class);
        parentVolume.setId(volumeUri);
        parentVolume.setLabel("parentVolume");
        _dbClient.createObject(parentVolume);

        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.setId(URIUtil.createId(BlockSnapshot.class));
        snapshot.setStorageController(xioStorageSystem.getId());
        snapshot.setLabel(SNAPSHOT_LABEL);
        NamedURI parentVolNamedUri = new NamedURI(parentVolume.getId(), parentVolume.getLabel());
        snapshot.setParent(parentVolNamedUri);
        _dbClient.createObject(snapshot);
    }

}
