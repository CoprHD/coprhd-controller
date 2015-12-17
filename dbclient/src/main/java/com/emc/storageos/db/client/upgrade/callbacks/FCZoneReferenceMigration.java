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
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

public class FCZoneReferenceMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(FCZoneReferenceMigration.class);

    @Override
    public void process() {
        initializeField();
    }

    /**
     * For all FC Zone Reference objects
     */
    private void initializeField() {
        log.info("Updating FC Zone reference label object to be more searchable");
        DbClient dbClient = this.getDbClient();
        List<URI> fcZoneRefs = dbClient.queryByType(FCZoneReference.class, false);

        Iterator<FCZoneReference> refs = dbClient.queryIterativeObjects(FCZoneReference.class, fcZoneRefs);
        while (refs.hasNext()) {
            FCZoneReference ref = refs.next();

            log.info("Examining block ref (id={}) for upgrade", ref.getId().toString());
            String label = ref.getLabel();

            // Criteria to switch over the label that is that it does not contain a second underscore
            if (label == null || !label.matches(".*_.*_.*")) {
                log.info("Resetting label:", label);
                ref.setLabel(FCZoneReference.makeLabel(label, ref.getVolumeUri().toString()));
                dbClient.updateObject(ref);
            }

        }
    }
}