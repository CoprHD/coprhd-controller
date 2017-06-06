/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * If we are upgrading from any version before 3.5, the thinlyProvisioned 
 * property should be set to false on any ViPR-managed volumes.
 * 
 * Before 3.5, the flag on VPLEX virtual volumes would just have been set
 * however the owning VirtualPool's thin provisioning property was set, 
 * even though VPLEX didn't use this flag.  Support for thin provisioning
 * was added to VPLEX version supported at the same time as ViPR 3.5.  So,
 * any volumes created before that would need to be thinlyProvisioned=false. 
 * 
 * @author beachn
 * @since 3.5
 */
public class VplexVolumeThinlyProvisionedMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(VplexVolumeThinlyProvisionedMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        int volumeUpdatedCount = 0;

        // cache a list of vplex URIs for performance reasons
        List<URI> vplexUris = new ArrayList<URI>();
        List<StorageSystem> vplexes = getAllVplexStorageSystems(dbClient);
        for (StorageSystem vplex : vplexes) {
            if (null != vplex) {
                vplexUris.add(vplex.getId());
            }
        }
        logger.info("found {} vplex storage systems in the database", vplexUris.size());

        for (URI vplexUri : vplexUris) {
            URIQueryResultList result = new URIQueryResultList();
            dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getStorageDeviceVolumeConstraint(vplexUri), result);
            Iterator<Volume> volumesIter = dbClient.queryIterativeObjects(Volume.class, result);

            while (volumesIter.hasNext()) {
                Volume volume = volumesIter.next();
                URI systemURI = volume.getStorageController();
                if (!NullColumnValueGetter.isNullURI(systemURI)) {
                    if (vplexUris.contains(systemURI)) {
                        // This is a VPLEX volume. If we are upgrading from any version
                        // before 3.5, if the thinlyProvisioned property is true, it should 
                        // be set to false on any ViPR-managed volumes.
                        if (volume.getThinlyProvisioned()) {
                            logger.info("updating thinlyProvisioned property on volume {} to false", volume.forDisplay());
                            volume.setThinlyProvisioned(false);
                            dbClient.updateObject(volume);
                            volumeUpdatedCount++;
                        }
                    }
                }
            }
        }

        logger.info("VplexVolumeThinlyProvisionedMigration completed, updated thinlyProvisioned to false on {} volumes", 
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
