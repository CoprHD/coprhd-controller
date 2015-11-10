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
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.VPlexUtil;

/**
 * Migration handler to upgrade the vplex backend volumes field replicationGroupInstance 
 * if they are in the array CGs.
 *
 */
public class VplexVolumesInCGMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VplexVolumesInCGMigration.class);

    @Override
    public void process() {
        updateVplexVolumesInConsistencyGroup();
    }
    
    /**
     * Migrate the backend volumes of the Vplex Volumes in CG
     */
    private void updateVplexVolumesInConsistencyGroup() {
        log.info("Migrating vplex volumes in CG" );
        DbClient dbClient = getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, false);
        Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            URI cgUri = volume.getConsistencyGroup();
            URI storageUri = volume.getStorageController();
            if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
                if (cg == null) {
                    continue;
                }
                StorageSystem system = dbClient.queryObject(StorageSystem.class, storageUri);
                if (system.getSystemType().equals(DiscoveredDataObject.Type.vplex.name()) &&
                        cg.checkForType(Types.LOCAL)) {
                    Volume backendVol = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient, false);
                    Volume backendHaVol = VPlexUtil.getVPLEXBackendVolume(volume, false, dbClient, false);
                    updateBackendVolume(cg, backendVol, dbClient);
                    updateBackendVolume(cg, backendHaVol, dbClient);
                }
            }
        }
    }
    
    /**
     * Update the backend volume with the backend replication group name
     * @param cg The ViPR BlockConsistencyGroup
     * @param backendVolume The backend volume
     * @param dbClient
     */
    private void updateBackendVolume(BlockConsistencyGroup cg, Volume backendVolume, DbClient dbClient) {
        if (backendVolume != null) {
            String backendCG = cg.getCgNameOnStorageSystem(backendVolume.getStorageController());
            if (backendCG != null && !backendCG.isEmpty()) {
                log.info("updating the volume {} replicationgroup {}", backendVolume.getLabel(), backendCG);
                backendVolume.setReplicationGroupInstance(backendCG);
                dbClient.updateObject(backendVolume);
            }
        }
    }

}
