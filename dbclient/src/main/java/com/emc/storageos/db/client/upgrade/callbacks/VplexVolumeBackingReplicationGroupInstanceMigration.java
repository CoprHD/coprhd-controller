/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.emc.storageos.util.VPlexUtil;

/**
 * If we are upgrading from any version 3.5 or before, the Volume.backingReplicationGroupInstance
 * column should be set on VPLEX virtual volumes to match the source side backend volume's
 * replicationGroupInstance column.
 * 
 * @author beachn
 * @since 3.5+
 */
public class VplexVolumeBackingReplicationGroupInstanceMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(VplexVolumeBackingReplicationGroupInstanceMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        int volumeUpdatedCount = 0;

        List<URI> vplexUris = new ArrayList<URI>();
        List<StorageSystem> vplexes = getAllVplexStorageSystems(dbClient);
        for (StorageSystem vplex : vplexes) {
            if (null != vplex) {
                vplexUris.add(vplex.getId());
            }
        }
        logger.info("found {} vplex storage systems in the database", vplexUris.size());

        for (URI vplexUri : vplexUris) {
            // fetch all Volumes for this VPLEX URI
            URIQueryResultList result = new URIQueryResultList();
            dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getStorageDeviceVolumeConstraint(vplexUri), result);
            Iterator<Volume> volumesIter = dbClient.queryIterativeObjects(Volume.class, result);

            while (volumesIter.hasNext()) {
                Volume volume = volumesIter.next();

                // Must skip VPLEX vols with no backend volumes (ingested case)
                if (null == volume.getAssociatedVolumes() && volume.getAssociatedVolumes().isEmpty()) {
                    logger.warn("VPLEX volume {} has no backend volumes, so cannot update backingReplicationGroupInstance, skipping...",
                            volume.forDisplay());
                    continue;
                }

                // This is a VPLEX volume. If we are upgrading from any version
                // 3.5 or before, we should set the new backingReplicationGroupInstance field
                // to be the same as the source side backend volume (both sides should be the same,
                // but we will default to the source side in an HA situation for consistency).
                if (NullColumnValueGetter.isNullValue(volume.getBackingReplicationGroupInstance())) {
                    Volume sourceSideBackingVolume = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient);
                    if (sourceSideBackingVolume != null) {
                        String instance = sourceSideBackingVolume.getReplicationGroupInstance();
                        if (NullColumnValueGetter.isNotNullValue(instance)) {
                            logger.info("updating backingReplicationGroupInstance property on volume {} to {}", 
                                    volume.forDisplay(), instance);
                            volume.setBackingReplicationGroupInstance(instance);
                            dbClient.updateObject(volume);
                            volumeUpdatedCount++;
                        }
                    }
                }
            }
        }

        logger.info("VplexVolumeBackingReplicationGroupInstanceMigration completed, updated backingReplicationGroupInstance on {} volumes",
                volumeUpdatedCount);
    }

    /**
     * Returns all VPLEX storage systems in ViPR.
     * 
     * @param dbClient a database client reference
     * @return a List of StorageSystems that are "vplex" type
     */
    private List<StorageSystem> getAllVplexStorageSystems(DbClient dbClient) {
        List<StorageSystem> vplexStorageSystems = new ArrayList<StorageSystem>();
        List<URI> allStorageSystemUris = dbClient.queryByType(StorageSystem.class, true);
        List<StorageSystem> allStorageSystems = dbClient.queryObject(StorageSystem.class, allStorageSystemUris);
        for (StorageSystem storageSystem : allStorageSystems) {
            if ((storageSystem != null)
                    && (DiscoveredDataObject.Type.vplex.name().equals(storageSystem.getSystemType()))) {
                vplexStorageSystems.add(storageSystem);
            }
        }
        return vplexStorageSystems;
    }
}
