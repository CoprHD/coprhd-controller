/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration process to handle setting allocated capacity to match
 * provisioned capacity on VPLEX volumes.
 * 
 * @author beachn
 * @since 2.0
 */
public class VplexVolumeAllocatedCapacityMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(VplexVolumeAllocatedCapacityMigration.class);

    @Override
    public void process() throws MigrationCallbackException {

        DbClient dbClient = getDbClient();

        try {

            List<URI> volumeUris = dbClient.queryByType(Volume.class, true);
            Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeUris, true);

            while (volumes.hasNext()) {
                Volume volume = volumes.next();

                URI storageURI = volume.getStorageController();
                if (!NullColumnValueGetter.isNullURI(storageURI)) {
                    StorageSystem storage = dbClient.queryObject(StorageSystem.class, storageURI);
                    if (DiscoveredDataObject.Type.vplex.name().equals(storage.getSystemType())) {
                        Long allocatedCapacity = volume.getAllocatedCapacity();
                        // For Vplex virtual volumes set allocated capacity to 0 (cop-18608)
                        if (allocatedCapacity != null && allocatedCapacity != 0) {
                            log.info("migrating allocated capacity from {} to 0 on VPLEX volume {}",
                                    allocatedCapacity, volume.getLabel());
                            volume.setAllocatedCapacity(0L);
                            dbClient.persistObject(volume);
                        }
                    }
                }
            }

        } catch (Exception ex) {
            log.error("Exception occured while migrating VPLEX Volume Allocated Capacities.");
            log.error(ex.getMessage(), ex);
        }

    }

}
