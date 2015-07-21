/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 *
 */
public class BlockSnapshotSessionMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionMigrationTest.class);
    
    private static final int SANPSHOT_COUNT = 3;
    private static final String BASE_PROJECT_LABEL = "project";
    private static final String BASE_PARENT_LABEL = "parent";
    private static final String BASE_LABEL = "snapshot";
    private static final String BASE_SNAPSET_LABEL = "snapset";
    private static final Boolean SYNC_ACTIVE = Boolean.TRUE;
    private static final String SETTINGS_INSTANCE = "settingsInstance";
    private static final String SETTINGS_GRP_INSTANCE = "groupInstance";

    
    private Map<URI, BlockSnapshot> _snapshotMap = new HashMap<URI, BlockSnapshot>();

    @Override
    protected String getSourceVersion() {
        // Jedi
        return "2.3";
    }

    @Override
    protected String getTargetVersion() {
        // Darth
        return "2.4";
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void prepareData() throws Exception {
        s_logger.info("Preparing data for BlockSnapshotSessionMigrationTest");
        DbClient dbClient = getDbClient();
        for (int i = 0; i < SANPSHOT_COUNT; i++) {
            BlockSnapshot snapshot = new BlockSnapshot();
            URI snapshotURI = URIUtil.createId(BlockSnapshot.class);
            snapshot.setId(snapshotURI);
            NamedURI projectURI = new NamedURI(URIUtil.createId(Project.class), BASE_PROJECT_LABEL + i);
            snapshot.setProject(projectURI);
            NamedURI parentURI = new NamedURI(URIUtil.createId(Volume.class), BASE_PARENT_LABEL + i);
            snapshot.setParent(parentURI);            
            snapshot.setLabel(BASE_LABEL + "i");
            snapshot.setSnapsetLabel(BASE_SNAPSET_LABEL);
            snapshot.setIsSyncActive(SYNC_ACTIVE);
            if (i % 2 == 0) {
                snapshot.setSettingsInstance(SETTINGS_INSTANCE);
            } else {
                snapshot.setSettingsGroupInstance(SETTINGS_GRP_INSTANCE);
            }
            _snapshotMap.put(snapshotURI, snapshot);
        }
        dbClient.createObject(_snapshotMap.values());
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void verifyResults() throws Exception {
        s_logger.info("Verifying results for BlockSnapshotSessionMigrationTest");
        DbClient dbClient = getDbClient();        
        List<URI> snapshotSessionURIs = dbClient.queryByType(BlockSnapshotSession.class, true);
        Iterator<BlockSnapshotSession> snapshotSessionsIter = dbClient.queryIterativeObjects(BlockSnapshotSession.class, snapshotSessionURIs);
        Assert.assertTrue("There are no BlockSnapshotSession instances in the database", snapshotSessionsIter.hasNext());
        while (snapshotSessionsIter.hasNext()) {
            BlockSnapshotSession snapshotSession = snapshotSessionsIter.next();
            StringSet linkedTargets = snapshotSession.getLinkedTargets();
            Assert.assertNotNull("No linked targets for snapshot session", linkedTargets);
            Assert.assertEquals("Snapshot session does not have a single linked target", linkedTargets.size(), 1);
            URI linkedTargetURI = URI.create(linkedTargets.iterator().next());
            Assert.assertTrue("Linked target not in bloakc snapshots map", _snapshotMap.containsKey(linkedTargetURI));
            BlockSnapshot linkedTarget = _snapshotMap.get(linkedTargetURI);
            Assert.assertEquals("Projects are not the same", linkedTarget.getProject().getURI(), snapshotSession.getProject().getURI());
            Assert.assertEquals("Parents are not the same", linkedTarget.getParent().getURI(), snapshotSession.getParent().getURI());
            Assert.assertEquals("Labels are not the same", linkedTarget.getLabel(), snapshotSession.getLabel());
            Assert.assertEquals("Session and snapset labels are not the same", linkedTarget.getSnapsetLabel(), snapshotSession.getSessionLabel());
            Assert.assertEquals("Is sync active is not the same", linkedTarget.getIsSyncActive(), snapshotSession.getIsSyncActive());
            String sessionInstance = snapshotSession.getSessionInstance();
            Assert.assertNotNull("Session instance is null", sessionInstance);
            if (linkedTarget.getSettingsInstance() != null) {
                Assert.assertEquals(String.format("Session instance is not %s", SETTINGS_INSTANCE), sessionInstance, SETTINGS_INSTANCE);                
            } else {
                Assert.assertEquals(String.format("Session instance is not %s", SETTINGS_GRP_INSTANCE), sessionInstance, SETTINGS_GRP_INSTANCE);
            }
        }
    }
}
