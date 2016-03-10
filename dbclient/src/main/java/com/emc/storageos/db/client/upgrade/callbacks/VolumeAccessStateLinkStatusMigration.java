/*
 * Copyright (c) 2013 EMC Corporation
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
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to initialize access_state and link_status. These fields will likely be
 * overwritten during discovery, however it's a good idea to put a base in there just in case.
 * 
 */
public class VolumeAccessStateLinkStatusMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VolumeAccessStateLinkStatusMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        initializeVolumeFields();
    }

    /**
     * For all volumes, fill in the right access states and link status
     */
    private void initializeVolumeFields() {
        log.info("Updating volume access state and link status.");
        DbClient dbClient = this.getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, false);

        Iterator<Volume> volumes =
                dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();

            log.info("Examining Volume (id={}) for upgrade", volume.getId().toString());

            if (volume.checkForRp() || volume.checkForSRDF()) {
                // If this volume is a source and exported to a host, the volume is write-disabled. Otherwise it is readwrite.
                volume.setLinkStatus(Volume.LinkStatus.IN_SYNC.toString());
                if (volume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
                    volume.setAccessState(Volume.VolumeAccessState.READWRITE.toString());
                } else if (volume.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString())) {
                    volume.setAccessState(Volume.VolumeAccessState.NOT_READY.toString());
                } else if (volume.getPersonality().equals(Volume.PersonalityTypes.METADATA.toString())) {
                    volume.setAccessState(Volume.VolumeAccessState.NOT_READY.toString());
                }
            } else {
                // No need to set link status for a non-protected volume.
                volume.setAccessState(Volume.VolumeAccessState.READWRITE.toString());
            }

            dbClient.persistObject(volume);
        }
    }
}
