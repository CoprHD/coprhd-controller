/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
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
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

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
    public void process() {

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
