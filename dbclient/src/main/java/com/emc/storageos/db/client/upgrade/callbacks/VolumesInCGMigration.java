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
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * Migration handler to upgrade the vplex backend volumes field replicationGroupInstance 
 * if they are in the array CGs.
 *
 */
public class VolumesInCGMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VolumesInCGMigration.class);

    @Override
    public void process() {
        updateConsistencyGroup();
        updateVolumesInConsistencyGroup();
    }
    
    /**
     * Migrate the volumes in CG
     */
    private void updateVolumesInConsistencyGroup() {
        log.info("Migrating volumes in CG" );
        DbClient dbClient = getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            URI cgUri = volume.getConsistencyGroup();
            URI storageUri = volume.getStorageController();
            if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
                StorageSystem system = dbClient.queryObject(StorageSystem.class, storageUri);
                if (cg == null || system == null) {
                    continue;
                }
                if (volume.getSrdfParent() != null || volume.getSrdfTargets() != null) {
                    continue;
                }
                if (system.getSystemType().equals(DiscoveredDataObject.Type.vplex.name()) &&
                        cg.checkForType(Types.LOCAL)) {
                    // For VPLEX or VPLEX+RP source volume, update the backend volumes
                    String personality = volume.getPersonality();
                    if (NullColumnValueGetter.isNullValue(personality) || 
                            personality.equals(PersonalityTypes.SOURCE.name())) {
                        StringSet associatedVolumeIds = volume.getAssociatedVolumes();
                        if(associatedVolumeIds != null) {
                            for (String associatedVolumeId : associatedVolumeIds) {
                                Volume backendVol = dbClient.queryObject(Volume.class,
                                        URI.create(associatedVolumeId));
                                updateBackendVolume(cg, backendVol, dbClient);
                            }
                        }
                    }
                 
                } else if (!cg.isProtectedCG()){
                    String rpName = cg.getCgNameOnStorageSystem(storageUri);
                    if (rpName != null && !rpName.isEmpty()) {
                        volume.setReplicationGroupInstance(rpName);
                        dbClient.updateObject(volume);
                    }
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
    
    /**
     * Migrate consistency group instances to set arrayConsistency to true
     */
    private void updateConsistencyGroup() {
        log.info("Migrating consistency group" );
        DbClient dbClient = getDbClient();
        List<URI> cgURIs = dbClient.queryByType(BlockConsistencyGroup.class, true);
        Iterator<BlockConsistencyGroup> cgs = dbClient.queryIterativeObjects(BlockConsistencyGroup.class, cgURIs);
        while (cgs.hasNext()) {
            BlockConsistencyGroup cg = cgs.next();
            cg.setArrayConsistency(true);
            dbClient.updateObject(cg);
        }
    }

}
