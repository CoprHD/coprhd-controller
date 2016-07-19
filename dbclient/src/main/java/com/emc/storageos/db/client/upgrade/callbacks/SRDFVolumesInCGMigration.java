/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * Migration handler to upgrade the SRDF volumes field replicationGroupInstance 
 * 
 * This migration handler does the same as VolumesInCGMigration except for SRDF volumes
 * which were skipped in that migration handler
 * 
 * It fixes an issue with SRDF volumes where the replicationGroupInstance field was not
 * populated and caused issues with delete
 * 
 * It will be checked into the ViPR 3.0 patch 1 branch but will not be run as part of the 
 * upgrade to ViPR 3.0 or 3.0p1.
 * 
 * It will go in the ViPR 3.1 migration handler area so that it can be run as part
 * of the upgrade to 3.1 and beyond.
 * 
 * This migration callbacks will also be available to be run manually from a new feature
 * in dbutils which allows customers to run specified migration callbacks at any time. Customers
 * who have SRDF volumes and upgraded from ViPR 2.4.1 to ViPR 3.0 should run the new tool as 
 * follows:
 * 
 * "/opt/storageos/bin/dbutils run_migration_callback com.emc.storageos.db.client.upgrade.callbacks.SRDFVolumesInCGMigration"
 *
 */
public class SRDFVolumesInCGMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(SRDFVolumesInCGMigration.class);

    @Override
    public void process() {
        updateVolumesInConsistencyGroup();
    }
    
    /**
     * Migrate the SRDF volumes in CG
     */
    private void updateVolumesInConsistencyGroup() {
        log.info("Migrating SRDF volumes in CG" );
        DbClient dbClient = getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        int totalVolumes = 0;
        int volumesUpdated = 0;
        while (volumes.hasNext()) {
            totalVolumes++;
            Volume volume = volumes.next();
            URI cgUri = volume.getConsistencyGroup();
            URI storageUri = volume.getStorageController();
            if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
                StorageSystem system = dbClient.queryObject(StorageSystem.class, storageUri);
                if (cg == null || system == null) {
                    log.warn(String.format("Volume %s is being skipped because the refrenced CG or storage system is null; cgUri: %s; storageUri: %s", 
                            volume.getId().toString(), cgUri.toString(), storageUri.toString())); 
                    continue;
                }
                if (volume.getSrdfParent() != null || volume.getSrdfTargets() != null) {
                    String replicationGroupName = cg.getCgNameOnStorageSystem(volume.getStorageController());
                    if (replicationGroupName != null && !replicationGroupName.isEmpty() &&
                            NullColumnValueGetter.isNullValue(volume.getReplicationGroupInstance())) {
                        log.info("updating the SRDF volume {} replicationgroup {}", volume.getLabel(), replicationGroupName);
                        volume.setReplicationGroupInstance(replicationGroupName);
                        dbClient.updateObject(volume);
                        volumesUpdated++;
                    }
                }
            }
        }
        log.info(String.format("%d volumes updated out of a total of %d volumes", volumesUpdated, totalVolumes));
    }
}
