/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class FullCopyVolumeReplicaStateMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(FullCopyVolumeReplicaStateMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        initializeVolumeFields();
    }

    /**
     * For all full copy volume, set replicaState as DETACHED
     */
    private void initializeVolumeFields() {
        log.info("Updating full copy volume replica state.");
        DbClient dbClient = this.getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, false);

        Iterator<Volume> volumes =
                dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();

            log.info("Examining Volume (id={}) for upgrade", volume.getId().toString());

            if (!NullColumnValueGetter.isNullURI(volume.getAssociatedSourceVolume())) {

                volume.setReplicaState(ReplicationState.DETACHED.name());
            }

            dbClient.persistObject(volume);
        }
    }

}
