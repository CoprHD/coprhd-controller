/**
 *  Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to migrate BlockObject consistencyGroup to the new
 * consistencyGroups list field.
 * 
 */
public class RecoverPointBlockSnapshotMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(RecoverPointBlockSnapshotMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        migrateRpBlockSnapshots();
    }

    /**
     * Migrates the RP BlockSnapshots. The migration consists of setting the deviceLabel
     * field based on the associated target volume's deviceLabel.
     */
    private void migrateRpBlockSnapshots() {
        log.info("Migrating RecoverPoint BlockSnapshot objects.");
        DbClient dbClient = getDbClient();
        List<URI> blockSnapshotURIs = dbClient.queryByType(BlockSnapshot.class, false);
        Iterator<BlockSnapshot> snapshots = dbClient.queryIterativeObjects(BlockSnapshot.class, blockSnapshotURIs);

        int migrationCount = 0;

        while (snapshots.hasNext()) {
            BlockSnapshot snapshot = snapshots.next();
            if (snapshot != null && snapshot.getParent() != null &&
                    BlockSnapshot.checkForRP(dbClient, snapshot.getId())) {
                // If this is an RP BlockSnapshot we need to migrate it.
                Volume parent = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
                if (parent.getRpTargets() != null) {
                    for (String targetIdStr : parent.getRpTargets()) {
                        Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(targetIdStr));
                        if (targetVolume != null && targetVolume.getVirtualArray().equals(snapshot.getVirtualArray())) {
                            log.info("Migrating RP BlockSnapshot {} - Setting deviceLabel field.", snapshot.getId().toString());
                            snapshot.setDeviceLabel(targetVolume.getDeviceLabel());
                            dbClient.persistObject(snapshot);
                            migrationCount++;
                        }
                    }
                }
            }
        }

        log.info("RecoverPoint BlockSnapshot migration complete.  A total of {} BlockSnapshots were migrated.", migrationCount);
    }
}
