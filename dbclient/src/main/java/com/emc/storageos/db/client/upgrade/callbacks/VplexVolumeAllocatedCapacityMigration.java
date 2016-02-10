/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
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

                StringSet associatedVolumes = volume.getAssociatedVolumes();
                if ((associatedVolumes != null) && (!associatedVolumes.isEmpty())) {

                    // associated volumes indicate that this is a vplex volume
                    Long allocatedCapacity = volume.getAllocatedCapacity();
                    Long provisionedCapacity = volume.getProvisionedCapacity();

                    if (allocatedCapacity != null && provisionedCapacity != null &&
                            !allocatedCapacity.equals(provisionedCapacity)) {
                        log.info("migrating allocated capacity from {} to {} on VPLEX volume {}",
                                new Object[] { allocatedCapacity, provisionedCapacity, volume.getLabel() });
                        volume.setAllocatedCapacity(provisionedCapacity);
                        dbClient.persistObject(volume);
                    }
                }
            }

        } catch (Exception ex) {
            log.error("Exception occured while migrating VPLEX Volume Allocated Capacities.");
            log.error(ex.getMessage(), ex);
        }

    }

}
