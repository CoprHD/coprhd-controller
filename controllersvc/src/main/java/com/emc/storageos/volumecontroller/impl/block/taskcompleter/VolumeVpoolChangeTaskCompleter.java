/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class VolumeVpoolChangeTaskCompleter extends VolumeWorkflowCompleter {

    private static final Logger _logger = LoggerFactory
            .getLogger(VolumeVpoolChangeTaskCompleter.class);

    private URI oldVpool;
    private Map<URI, URI> oldVpools;
    private List<URI> migrationURIs = new ArrayList<URI>();

    public VolumeVpoolChangeTaskCompleter(URI volume, URI oldVpool, String task) {
        super(volume, task);
        this.oldVpool = oldVpool;
    }
    
    public VolumeVpoolChangeTaskCompleter(List<URI> volumeURIs, URI oldVpool, String task) {
        super(volumeURIs, task);
        this.oldVpool = oldVpool;
    }

    public VolumeVpoolChangeTaskCompleter(List<URI> volumeURIs, List<URI> migrationURIs, Map<URI, URI> oldVpools, String task) {
        super(volumeURIs, task);
        this.oldVpools = oldVpools;
        this.migrationURIs.addAll(migrationURIs);
    } 

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        boolean useOldVpoolMap = (oldVpool == null);
        switch (status) {
        case error:
            _log.error(
                    "An error occurred during virtual pool change "
                            + "- restore the old virtual pool to the volume(s): {}",
                            serviceCoded.getMessage());
            // We either are using a single old Vpool URI or a map of Volume URI to old Vpool URI
            for (URI id : getIds()) {
                URI oldVpoolURI = oldVpool;
                if ((useOldVpoolMap) && (!oldVpools.containsKey(id))) {
                    continue;
                } else if (useOldVpoolMap) {
                    oldVpoolURI = oldVpools.get(id);
                }
                
                Volume volume = dbClient.queryObject(Volume.class, id);
                _log.info("Rolling back virtual pool on volume {}({})", id, volume.getLabel());
                
                volume.setVirtualPool(oldVpoolURI);
                _log.info("Set volume's virtual pool back to {}", oldVpoolURI);
                
                VirtualPool oldVpool = dbClient.queryObject(VirtualPool.class, oldVpoolURI);
                
                // Rollback any RP specific changes to this volume
                if (volume.checkForRp()) {
                    if (!VirtualPool.vPoolSpecifiesProtection(oldVpool)) {
                        _log.info("Rollback the volume's changes for RP...");
                        
                        // Clean up the CG first for the change vpool volume. This CG would
                        // have been created automatically during a change vpool to add RP
                        // protection.
                        // TODO: This must change when we are able to re-use existing CGs for vpool change. 
                        // We would then remove the volume from the existing CG and make sure the volume's CG 
                        // reference is removed and we wouldn't delete the CG in this case.
                        if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
                            if (cg != null && !cg.getInactive()) {
                                cg.setInactive(true);
                                dbClient.persistObject(cg);
                            } 
                        }
                        
                        // Clear out the rest of the RP related fields that would not be needed during
                        // a rollback. This resets the volume back to it's pre-RP state so it can be
                        // used again.
                        volume.setPersonality(NullColumnValueGetter.getNullStr());                    
                        volume.setProtectionController(NullColumnValueGetter.getNullURI());
                        volume.setRSetName(NullColumnValueGetter.getNullStr());
                        volume.setInternalSiteName(NullColumnValueGetter.getNullStr());
                        volume.setRpCopyName(NullColumnValueGetter.getNullStr());
                        // Rollback the journal volume if it was created
                        if (!NullColumnValueGetter.isNullURI(volume.getRpJournalVolume())) {
                            rollbackVolume(volume.getRpJournalVolume(), dbClient);
                        }
                        // Rollback the secondary journal volume if it was created
                        volume.setRpJournalVolume(NullColumnValueGetter.getNullURI());
                        if (!NullColumnValueGetter.isNullURI(volume.getSecondaryRpJournalVolume())) {
                            rollbackVolume(volume.getSecondaryRpJournalVolume(), dbClient);
                        }
                        volume.setSecondaryRpJournalVolume(NullColumnValueGetter.getNullURI());
                        volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                        StringSet resetRpTargets = volume.getRpTargets();
                        if (resetRpTargets != null) {
                            // Rollback any target volumes that were created
                            for (String rpTargetId : resetRpTargets) {
                                Volume targetVol = rollbackVolume(URI.create(rpTargetId), dbClient);
                                // Rollback any target journal volumes that were created
                                if (!NullColumnValueGetter.isNullURI(targetVol.getRpJournalVolume())) {
                                    rollbackVolume(targetVol.getRpJournalVolume(), dbClient);
                                }
                            }                        
                            resetRpTargets.clear();
                            volume.setRpTargets(resetRpTargets);
                        }
                    }
                    else {
                        _log.info("Rollback the volume's changes for RP to RP...");
                        // Rollback of a volume that already had protection
                        int dummyAction = 1;
                        // For change vpool from protected to protected there are multiple actions that 
                        // could have been performed and each would require slightly different rollback 
                        // steps.
                        // We'll have to handle these as they present themselves. For now,
                        // we'll only rollback the secondary journal as the only supported case is (for now) 
                        // RP+VPLEX -> MetroPoint.
                        switch (dummyAction) {
                            case 1: 
                                    _log.info("Rollback the secondary journal");
                                    // Rollback the secondary journal volume if it was created
                                    volume.setRpJournalVolume(NullColumnValueGetter.getNullURI());
                                    if (!NullColumnValueGetter.isNullURI(volume.getSecondaryRpJournalVolume())) {
                                        rollbackVolume(volume.getSecondaryRpJournalVolume(), dbClient);
                                    }
                                    volume.setSecondaryRpJournalVolume(NullColumnValueGetter.getNullURI());
                                    break;
                            default:
                                    break;
                        }
                    }                  

                    _log.info("Rollback for RP complete.");
                }
               
                dbClient.persistObject(volume);
            }
            
            for (URI migrationURI : migrationURIs) {
                dbClient.error(Migration.class, migrationURI, getOpId(), serviceCoded);
            }
            break;
        case ready:
            // The new Vpool has already been stored in the volume in BlockDeviceExportController.
            
            //record event.
            OperationTypeEnum opType = OperationTypeEnum.CHANGE_VOLUME_VPOOL;
            try {
                boolean opStatus = (Operation.Status.ready == status)? true: false;
                String evType = opType.getEvType(opStatus);
                String evDesc = opType.getDescription();
                for (URI id : getIds()) {
                    if ((useOldVpoolMap) && (!oldVpools.containsKey(id))) {
                        continue;
                    }
                    recordBourneVolumeEvent(dbClient, id, evType, status, evDesc);
                }
            } catch (Exception ex) {
                _logger.error(
                        "Failed to record block volume operation {}, err: {}",
                        opType.toString(), ex);
            }
            for (URI migrationURI : migrationURIs) {
                dbClient.ready(Migration.class, migrationURI, getOpId());
            }
            break;
        default:
            break;
        }
        super.complete(dbClient,  status,  serviceCoded);
    }
    
    private Volume rollbackVolume(URI volumeURI, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, volumeURI);
        volume.setInactive(true);
        volume.setLabel(volume.getLabel() + "-ROLLBACK");
        dbClient.persistObject(volume); 
        
        // Rollback any VPLEX backing volumes too
        if (volume.getAssociatedVolumes() != null 
                && !volume.getAssociatedVolumes().isEmpty()) {
            for (String associatedVolId : volume.getAssociatedVolumes()) {                 
                Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolId));
                associatedVolume.setInactive(true);
                associatedVolume.setLabel(volume.getLabel() + "-ROLLBACK");
                dbClient.persistObject(associatedVolume); 
            }
        }        
        
        return volume;
    }
}
