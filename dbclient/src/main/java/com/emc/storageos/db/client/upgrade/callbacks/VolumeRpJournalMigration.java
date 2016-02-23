/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to initialize RecoverPoint BlockConsistencyGroups,
 * RecoverPoint VirtualPools, and RecoverPoint source/target Volume journal
 * references.
 * 
 */
public class VolumeRpJournalMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VolumeRpJournalMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateVolumeRpJournalRefs();
    }

    /**
     * For all RP source/target volumes, identify the associated journal volumes
     * and add the reference.
     */
    private void updateVolumeRpJournalRefs() {
        log.info("updateVolumeRpJournalRefs - Deprecated.");
    }
}
