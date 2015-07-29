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
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * Migration process sets the relationship between VPLEX volume full copies and
 * their source VPLEX volume.
 */
public class VplexVolumeFullCopyMigration extends BaseCustomMigrationCallback {

    private static final Logger s_logger = LoggerFactory.getLogger(VplexVolumeFullCopyMigration.class);

    @Override
    public void process() {

        s_logger.info("Excecuting VPLEX full copy migration");
        DbClient dbClient = getDbClient();
        try {
            List<URI> volumeUris = dbClient.queryByType(Volume.class, true);
            Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeUris, true);
            while (volumes.hasNext()) {
                StringSet vplexFullCopyVolumeIds = new StringSet();
                Volume volume = volumes.next();
                StringSet associatedVolumeIds = volume.getAssociatedVolumes();

                // VPLEX volumes, except those ingested but not migrated, have associated volumes.
                // Those that are ingested and have not been migrated will not have full copies
                // so we don;t need to worry about those.
                if ((associatedVolumeIds != null) && (!associatedVolumeIds.isEmpty())) {
                    s_logger.info("Checking VPLEX volume {}", volume.getLabel());
                    for (String associatedVolumeId : associatedVolumeIds) {
                        URI associatedVolumeURI = URI.create(associatedVolumeId);
                        Volume associatedVolume = dbClient.queryObject(Volume.class, associatedVolumeURI);

                        // Log an error and continue if there is no such volume.
                        if (associatedVolume == null) {
                            s_logger.info("Associated volume {} for volume {} not found.",
                                    associatedVolumeURI, volume.getId());
                            continue;
                        }

                        // We want the "source" associated volume. This is the backend volume
                        // in the same varray as the VPLEX volume. This is the volume used
                        // when full copies of the VPLEX volume are created. Since we do a
                        // native full copy of this backend volume, the full copy relationships
                        // are established for the native copy. We can use this information
                        // to set the relationships for the VPLEX volume itself.
                        if (!associatedVolume.getVirtualArray().equals(volume.getVirtualArray())) {
                            continue;
                        }
                        s_logger.info("Found source side backend volume {}", associatedVolume.getLabel());

                        // See if the backend volume has any full copies.
                        StringSet fullCopyVolumeIds = associatedVolume.getFullCopies();
                        if (fullCopyVolumeIds == null || fullCopyVolumeIds.isEmpty()) {
                            continue;
                        }
                        s_logger.info("Source side backend volume has {} full copies", fullCopyVolumeIds.size());

                        // If so, then this VPLEX volume is a source for one or
                        // more VPLEX fully copy volumes.
                        for (String fullCopyVolumeId : fullCopyVolumeIds) {
                            // For each full copy of the backend volume, determine the
                            // VPLEX volume using that copy. Those VPLEX volumes are
                            // full copies of the VPLEX volume currently being processed.
                            URIQueryResultList queryResults = new URIQueryResultList();
                            dbClient.queryByConstraint(
                                    AlternateIdConstraint.Factory.getVolumeByAssociatedVolumesConstraint(fullCopyVolumeId), queryResults);
                            Iterator<URI> queryResultsIter = queryResults.iterator();
                            while (queryResultsIter.hasNext()) {
                                // Set the source for this VPLEX full copy volume
                                // to the VPLEX volume being processed and persist.
                                Volume vplexFullCopyVolume = dbClient.queryObject(Volume.class, queryResultsIter.next());
                                s_logger.info("Found VPLEX volume {} for backend full copy", vplexFullCopyVolume.getLabel());

                                vplexFullCopyVolume.setAssociatedSourceVolume(volume.getId());
                                dbClient.persistObject(vplexFullCopyVolume);
                                s_logger.info("Set full copy source");

                                // Also add this VPLEX full volume copy to the full copies list
                                // for the source volume.
                                vplexFullCopyVolumeIds.add(vplexFullCopyVolume.getId().toString());
                            }
                        }
                    }
                }

                // Now if we did find full copies for the volume being processed
                // set them and persist.
                if (!vplexFullCopyVolumeIds.isEmpty()) {
                    volume.setFullCopies(vplexFullCopyVolumeIds);
                    dbClient.persistObject(volume);
                    s_logger.info("Set {} full copies for VPLEX volume {}", vplexFullCopyVolumeIds.size(), volume.getLabel());
                }
            }
        } catch (Exception ex) {
            s_logger.error("Exception occured while migrating VPLEX volume full copy relationshsips.");
            s_logger.error(ex.getMessage(), ex);
        }
    }
}
