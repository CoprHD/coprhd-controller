/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Upgrade callback class when upgrading from 2.3 to a later
 * release that makes sure any detached full copies are no
 * longer associated with a source volume and are removed
 * from the full copies list of the source volume.
 */
public class FullCopyVolumeDetachedStateMigration extends BaseCustomMigrationCallback {
    private static final Logger s_logger = LoggerFactory.getLogger(FullCopyVolumeDetachedStateMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        initializeVolumeFields();
    }

    /**
     * For all full copy volume that is in the detached state, make sure
     * that it's associated source volume field is null and that it is
     * removed from the full copies list of the source.
     */
    private void initializeVolumeFields() {
        s_logger.info("Updating detached full copy volumes.");
        DbClient dbClient = this.getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, true);

        Iterator<Volume> volumes =
                dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            boolean volumeUpdated = false;

            s_logger.info("Examining Volume (id={}) for upgrade", volume.getId().toString());
            String replicaState = volume.getReplicaState();
            // Check if the replicate state is detached.
            if ((NullColumnValueGetter.isNotNullValue(replicaState)) &&
                    (ReplicationState.DETACHED.name().equals(replicaState))) {
                URI sourceURI = volume.getAssociatedSourceVolume();
                if (!NullColumnValueGetter.isNullURI(sourceURI)) {
                    // We make sure the associated source volume is null.
                    // This change was made in ViPR 2.3 for Jira 12659, but
                    // the 2.3 upgrade callback never marked the associated
                    // source volume null for existing, detached full copies
                    // in the database, which all full copies were prior to 2.3.
                    // See class FullCopyVolumeReplicaStateMigration.
                    s_logger.info("Setting associated source volume to null");
                    volume.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                    volumeUpdated = true;
                }
            }

            // For any volume that has full copies, make sure none of those full copies
            // are in a detached state.
            HashSet<String> fullCopiesToRemove = new HashSet<String>();
            StringSet fullCopies = volume.getFullCopies();
            if (fullCopies != null) {
                for (String fullCopyId : fullCopies) {
                    Volume fullCopy = dbClient.queryObject(Volume.class, URI.create(fullCopyId));
                    if (fullCopy != null) {
                        replicaState = fullCopy.getReplicaState();
                        // Check if the replicate state is detached.
                        if ((NullColumnValueGetter.isNotNullValue(replicaState)) &&
                                (ReplicationState.DETACHED.name().equals(replicaState))) {
                            fullCopiesToRemove.add(fullCopyId);
                        }
                    } else {
                        fullCopiesToRemove.add(fullCopyId);
                    }
                }

                // Existing, detached full copies in the database should be
                // removed from the full copies list of their source volume.
                // This is the change for Jira 12766 (COP-13552) which is
                // made in the Darth (2.4) ViPR release.
                s_logger.info("Removing {} from full copies list of source volume {}:{}", fullCopiesToRemove, volume.getId());
                fullCopies.removeAll(fullCopiesToRemove);
                volumeUpdated = true;
            }

            // Persist the changes if necessary.
            if (volumeUpdated) {
                dbClient.persistObject(volume);
            }
        }
    }
}