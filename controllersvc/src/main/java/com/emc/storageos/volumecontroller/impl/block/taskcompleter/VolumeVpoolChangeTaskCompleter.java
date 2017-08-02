/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.vplex.api.VPlexMigrationInfo;
import com.google.common.base.Joiner;

@SuppressWarnings("serial")
public class VolumeVpoolChangeTaskCompleter extends VolumeWorkflowCompleter {

    private static final Logger _logger = LoggerFactory
            .getLogger(VolumeVpoolChangeTaskCompleter.class);

    private URI oldVpool;
    private Map<URI, URI> oldVpools;
    private Map<URI, URI> newVpools;
    private Map<URI, StringSetMap> maskToZoningMap;
    private final List<URI> migrationURIs = new ArrayList<URI>();

    public VolumeVpoolChangeTaskCompleter(URI volume, URI oldVpool, String task) {
        super(volume, task);
        this.oldVpool = oldVpool;
    }

    public VolumeVpoolChangeTaskCompleter(List<URI> volumeURIs, URI oldVpool, String task) {
        super(volumeURIs, task);
        this.oldVpool = oldVpool;
    }

    public VolumeVpoolChangeTaskCompleter(List<URI> volumeURIs, List<URI> migrationURIs, Map<URI, URI> oldVpools, List<URI> cgIds,
            String task) {
        super(volumeURIs, task);
        this.oldVpools = oldVpools;
        this.migrationURIs.addAll(migrationURIs);
        if (cgIds != null) {
            for (URI cgId : cgIds) {
                this.addConsistencyGroupId(cgId);
            }
        }
    }

    public VolumeVpoolChangeTaskCompleter(List<URI> volumeURIs, Map<URI, URI> oldVpools, String task) {
        super(volumeURIs, task);
        this.oldVpools = oldVpools;
    }

    public VolumeVpoolChangeTaskCompleter(List<URI> volumeURIs, List<URI> migrationURIs, Map<URI, URI> oldVpools, Map<URI, URI> newVpools,
            String task) {        
        super(volumeURIs, task);
        this.oldVpools = oldVpools;
        this.migrationURIs.addAll(migrationURIs);
        this.newVpools = newVpools;
    }

    public void setMaskToZoningMap(Map<URI, StringSetMap> maskToZoningMap) {
        this.maskToZoningMap = maskToZoningMap;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        boolean useOldVpoolMap = (oldVpool == null);
        List<Volume> volumesToUpdate = new ArrayList<Volume>();
        try {
            switch (status) {
                case error:
                    _log.error("An error occurred during virtual pool change " + "- restore the old virtual pool to the volume(s): {}",
                            serviceCoded.getMessage());                    
                    // We either are using a single old Vpool URI or a map of Volume URI to old Vpool URI
                    for (URI id : getIds()) {
                        Volume volume = dbClient.queryObject(Volume.class, id);
                        
                        // Vpool changes prepare ViPR volumes for new volumes that will
                        // be created on the hardware as a result of a vpool change. For
                        // example, a vpool change may migrate the backend volumes for a 
                        // VPLEX volume. If this fails, then we need to be sure that the 
                        // target volumes prepared for the migration are marked for deletion.
                        // however, we only want to do this if the actual volume has yet to
                        // be created on the array. We check for a native GUID for
                        // the volume. If not set, the volume has not yet been created.
                        if ((volume != null) && (!volume.isVPlexVolume(dbClient))
                                && (volume.checkInternalFlags(DataObject.Flag.INTERNAL_OBJECT))
                                && (NullColumnValueGetter.isNullValue(volume.getNativeGuid()))
                                && (!volume.getInactive())) {
                            volume.setInactive(true);
                            volumesToUpdate.add(volume);
                        }
                        
                        URI oldVpoolURI = oldVpool;
                        if ((useOldVpoolMap) && (!oldVpools.containsKey(id))) {
                            continue;
                        } else if (useOldVpoolMap) {
                            oldVpoolURI = oldVpools.get(id);
                        }
                                                
                        _log.info("Rolling back virtual pool on volume {}({})", id, volume.getLabel());
                        
                        URI newVpoolURI = volume.getVirtualPool();
                        if (newVpools != null && !newVpools.isEmpty()) {
                            newVpoolURI = newVpools.get(id);
                            if (newVpoolURI != null) {     
                                newVpoolURI = volume.getVirtualPool();
                            }
                        }
                                                                        
                        VirtualPool oldVpool = dbClient.queryObject(VirtualPool.class, oldVpoolURI);
                        VirtualPool newVpool = dbClient.queryObject(VirtualPool.class, newVpoolURI);
                        
                        if (isMigrationCommitted(dbClient)) {
                            _log.info("Migration already commited, leaving virtual pool for volume: " + volume.forDisplay());
                        }  else {
                            volume.setVirtualPool(oldVpoolURI);
                            _log.info("Set volume's virtual pool back to {}", oldVpoolURI); 
                        }
                        
                        // Only rollback protection on the volume if the volume specifies RP and the 
                        // old vpool did not have protection and the new one does (so we were trying to add
                        // RP protection but it failed for some reason so we need to rollback).
                        boolean rollbackProtection = volume.checkForRp() 
                                && !VirtualPool.vPoolSpecifiesProtection(oldVpool)
                                && VirtualPool.vPoolSpecifiesProtection(newVpool);

                        if (rollbackProtection) {
                            // Special rollback for RP, RP+VPLEX, and MetroPoint in the case
                            // where the operation tried to apply RP Protection to the volume 
                            // and now it needs to be reverted.
                            RPHelper.rollbackProtectionOnVolume(volume, oldVpool, dbClient);
                        } 
                        
                        if (RPHelper.isVPlexVolume(volume, dbClient)) {
                            if (!isMigrationCommitted(dbClient)) {
                                // Special rollback for VPLEX to update the backend vpools to the old vpools
                                rollBackVpoolOnVplexBackendVolume(volume, volumesToUpdate, dbClient, oldVpoolURI);
                            }
                        }
                        
                        // Add the volume to the list of volumes to be updated in the DB so that the
                        // old vpool reference can be restored.
                        volumesToUpdate.add(volume);                        
                    }
                    dbClient.updateObject(volumesToUpdate);

                    handleVplexVolumeErrors(dbClient);

                    rollbackMaskZoningMap(dbClient);

                    // If there's a task associated with the CG, update that as well
                    if (this.getConsistencyGroupIds() != null) {
                        for (URI cgId : this.getConsistencyGroupIds()) {
                            dbClient.error(BlockConsistencyGroup.class, cgId, this.getOpId(), serviceCoded);
                        }
                    }
                    break;
                case ready:
                    // record event.
                    OperationTypeEnum opType = OperationTypeEnum.CHANGE_VOLUME_VPOOL;
                    try {
                        boolean opStatus = (Operation.Status.ready == status) ? true : false;
                        String evType = opType.getEvType(opStatus);
                        String evDesc = opType.getDescription();
                        for (URI id : getIds()) {
                            if ((useOldVpoolMap) && (!oldVpools.containsKey(id))) {
                                continue;
                            }
                            
                            // Regardless if this has already been done, if we are in the 
                            // "ready" or "success" state then one of the last
                            // steps we need to take for the volume is to update the
                            // vpool reference to the new vpool.
                            if (newVpools != null && !newVpools.isEmpty()) {
                                URI newVpoolId = newVpools.get(id);
                                if (newVpoolId != null) {                                    
                                    Volume volume = dbClient.queryObject(Volume.class, id);
                                    _log.info("Change vpool task complete, updating vpool references for " + volume.getLabel());
                                    volume.setVirtualPool(newVpoolId);
                                    // No effect if this not a VPLEX volume
                                    VPlexUtil.updateVPlexBackingVolumeVpools(volume, newVpoolId, dbClient);
                                    volumesToUpdate.add(volume);
                                }
                            }                            
                            recordBourneVolumeEvent(dbClient, id, evType, status, evDesc);
                        }                        
                        dbClient.updateObject(volumesToUpdate);
                        
                    } catch (Exception ex) {
                        _logger.error("Failed to record block volume operation {}, err: {}", opType.toString(), ex);
                    }

                    // If there's a task associated with the CG, update that as well
                    if (this.getConsistencyGroupIds() != null) {
                        for (URI cgId : this.getConsistencyGroupIds()) {
                            dbClient.ready(BlockConsistencyGroup.class, cgId, this.getOpId());
                        }
                    }
                    break;
                case suspended_error:
                    if (this.getConsistencyGroupIds() != null) {
                        for (URI cgId : this.getConsistencyGroupIds()) {
                            dbClient.suspended_error(BlockConsistencyGroup.class, cgId, this.getOpId(), serviceCoded);
                        }
                    }
                    break;
                case suspended_no_error:
                    if (this.getConsistencyGroupIds() != null) {
                        for (URI cgId : this.getConsistencyGroupIds()) {
                            dbClient.suspended_no_error(BlockConsistencyGroup.class, cgId, this.getOpId());
                        }
                    }
                    break;
                default:
                    break;
            }
        } finally {
            switch (status) {
                case error:
                    for (URI migrationURI : migrationURIs) {
                        dbClient.error(Migration.class, migrationURI, getOpId(), serviceCoded);
                    }
                    if (this.getConsistencyGroupIds() != null) {
                        for (URI cgId : this.getConsistencyGroupIds()) {
                            dbClient.error(BlockConsistencyGroup.class, cgId, this.getOpId(), serviceCoded);
                        }
                    }
                    break;
                case suspended_error:
                    if (this.getConsistencyGroupIds() != null) {
                        for (URI cgId : this.getConsistencyGroupIds()) {
                            dbClient.suspended_error(BlockConsistencyGroup.class, cgId, this.getOpId(), serviceCoded);
                        }
                    }
                    break;
                case suspended_no_error:
                    if (this.getConsistencyGroupIds() != null) {
                        for (URI cgId : this.getConsistencyGroupIds()) {
                            dbClient.suspended_no_error(BlockConsistencyGroup.class, cgId, this.getOpId());
                        }
                    }
                    break;
                case ready:
                default:
                    for (URI migrationURI : migrationURIs) {
                        dbClient.ready(Migration.class, migrationURI, getOpId());
                    }
                    if (this.getConsistencyGroupIds() != null) {
                        for (URI cgId : this.getConsistencyGroupIds()) {
                            dbClient.ready(BlockConsistencyGroup.class, cgId, this.getOpId());
                        }
                    }
            }
            super.complete(dbClient, status, serviceCoded);
        }
    }

    /**
     * Roll back vPool on vplex backend volumes.
     *
     * @param volume
     *            VPLEX Volume to rollback backend vpool on
     * @param volumesToUpdate
     *            List of all volumes to update
     * @param dbClient
     *            DBClient
     * @param oldVpoolURI
     *            The old vpool URI
     */
    private void rollBackVpoolOnVplexBackendVolume(Volume volume, List<Volume> volumesToUpdate, DbClient dbClient, URI oldVpoolURI) {
        // Check if it is a VPlex volume, and get backend volumes
        Volume backendSrc = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient, false);
        if (backendSrc != null) {
            _log.info("Rolling back virtual pool on VPLEX backend Source volume {}({})", backendSrc.getId(), backendSrc.getLabel());

            backendSrc.setVirtualPool(oldVpoolURI);
            _log.info("Set volume's virtual pool back to {}", oldVpoolURI);
            volumesToUpdate.add(backendSrc);

            // VPlex volume, check if it is distributed
            Volume backendHa = VPlexUtil.getVPLEXBackendVolume(volume, false, dbClient, false);
            if (backendHa != null) {
                _log.info("Rolling back virtual pool on VPLEX backend Distributed volume {}({})", backendHa.getId(), backendHa.getLabel());

                VirtualPool oldVpoolObj = dbClient.queryObject(VirtualPool.class, oldVpoolURI);
                VirtualPool oldHAVpool = VirtualPool.getHAVPool(oldVpoolObj, dbClient);
                if (oldHAVpool == null) { // it may not be set
                    oldHAVpool = oldVpoolObj;
                }
                backendHa.setVirtualPool(oldHAVpool.getId());
                _log.info("Set volume's virtual pool back to {}", oldHAVpool.getId());
                volumesToUpdate.add(backendHa);
            }
        }
    }

    /**
     * Handles the cleanup of VPlex volumes when an error occurs during a change
     * virtual pool operation. The VPlex controller does not mark volumes inactive
     * during rollback in order to allow delete operations if backing volumes are
     * not removed properly. Marking the volume inactive will be taken care of here.
     *
     * @param dbClient
     *            the DB client.
     */
    private void handleVplexVolumeErrors(DbClient dbClient) {

        List<String> finalMessages = new ArrayList<String>();

        for (URI id : getIds()) {
            Volume volume = dbClient.queryObject(Volume.class, id);

            if (volume.getAssociatedVolumes() != null && !volume.getAssociatedVolumes().isEmpty()) {
                _log.info("Looking at VPLEX virtual volume {}", volume.getLabel());

                boolean deactivateVirtualVolume = true;
                List<String> livingVolumeNames = new ArrayList<String>();

                _log.info("Its associated volumes are: " + volume.getAssociatedVolumes());
                for (String associatedVolumeUri : volume.getAssociatedVolumes()) {
                    Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolumeUri));
                    if (associatedVolume != null && !associatedVolume.getInactive()) {
                        _log.warn("VPLEX virtual volume {} has active associated volume {}", volume.getLabel(),
                                associatedVolume.getLabel());
                        livingVolumeNames.add(associatedVolume.getLabel());
                        deactivateVirtualVolume = false;
                    }
                }

                if (deactivateVirtualVolume) {
                    _log.info("VPLEX virtual volume {} has no active associated volumes, marking for deletion", volume.getLabel());
                    dbClient.markForDeletion(volume);
                } else {
                    String message = "VPLEX virtual volume " + volume.getLabel() + " will not be marked for deletion "
                            + "because it still has active associated volumes (";
                    message += Joiner.on(",").join(livingVolumeNames) + ")";
                    finalMessages.add(message);
                    _log.warn(message);
                }
            }
        }

        if (!finalMessages.isEmpty()) {
            String finalMessage = Joiner.on("; ").join(finalMessages) + ".";
            _log.error(finalMessage);
        }
    }

    /**
     * Restores the zonemaps of the export masks impacted by change vpool operation
     * 
     * @param dbClient the DB client
     */
    private void rollbackMaskZoningMap(DbClient dbClient) {
        if (maskToZoningMap == null || maskToZoningMap.isEmpty()) {
            _log.info("There are no masks' zonemaps to be restore.");
            return;
        }

        List<ExportMask> masks = dbClient.queryObject(ExportMask.class, maskToZoningMap.keySet());
        for (ExportMask mask : masks) {
            StringSetMap zoningMap = maskToZoningMap.get(mask.getId());
            mask.getZoningMap().clear();
            mask.addZoningMap(zoningMap);
        }

        dbClient.updateObject(masks);
    }
    
    /**
     * Determines if a migration associated with the virtual pool change was successfully committed.
     * 
     * @param dbClient A reference to a database client.
     * 
     * @return true if a migration was committed, false otherwise.
     */
    private boolean isMigrationCommitted(DbClient dbClient) {
        boolean migrationCommitted = false;
        if (migrationURIs != null && !migrationURIs.isEmpty()) {
            for (URI migrationURI : migrationURIs) {
                Migration migration = dbClient.queryObject(Migration.class, migrationURI);
                if (migration != null) {
                    if (VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue().equals(
                            migration.getMigrationStatus())) {
                        migrationCommitted = true;
                        break;
                    }
                }
            }
        }
        return migrationCommitted;
    }
}
