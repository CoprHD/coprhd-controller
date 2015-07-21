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
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 *
 */
public class BlockSnapshotSessionMigration extends BaseCustomMigrationCallback {

    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionMigration.class);

    @Override
    public void process() {
        // Create a BlockSnapshotSession for each BlockSnapshot
        // in the database, and add the BlockSnapshot to the
        // linked targets list for the session.
        createBlockSnapshotSessions();
    }
    
    /**
     * 
     */
    @SuppressWarnings("deprecation")
    private void createBlockSnapshotSessions() {
        s_logger.info("Creating BlockSnapshotSession instances for active BlockSnapshot instances in the database.");
        DbClient dbClient = getDbClient();        
        List<URI> snapshotURIs = dbClient.queryByType(BlockSnapshot.class, true);
        Iterator<BlockSnapshot> snapshotsIter = dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
        while (snapshotsIter.hasNext()) {
            BlockSnapshot snapshot = snapshotsIter.next();
            s_logger.info("Creating BlockSnapshotSession for snapshot {}:{}", snapshot.getId(), snapshot.getLabel());
            BlockSnapshotSession snapshotSession = new BlockSnapshotSession();
            snapshotSession.setId(URIUtil.createId(BlockSnapshotSession.class));
            snapshotSession.setParent(snapshot.getParent());
            snapshotSession.setProject(snapshot.getProject());
            snapshotSession.setLabel(snapshot.getLabel());
            snapshotSession.setSessionLabel(snapshot.getSnapsetLabel());
            snapshotSession.setIsSyncActive(snapshot.getIsSyncActive());
            StringSet linkedTargets = new StringSet();
            linkedTargets.add(snapshot.getId().toString());
            snapshotSession.setLinkedTargets(linkedTargets);
            String sessionInstance = snapshot.getSettingsInstance() != null ? snapshot.getSettingsInstance() : snapshot.getSettingsGroupInstance();
            snapshotSession.setSessionInstance(sessionInstance);
            dbClient.createObject(snapshotSession);
        }
    }
}
