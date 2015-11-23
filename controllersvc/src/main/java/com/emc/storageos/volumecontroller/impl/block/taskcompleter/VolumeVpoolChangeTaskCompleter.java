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
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.VPlexUtil;
import com.google.common.base.Joiner;

public class VolumeVpoolChangeTaskCompleter extends VolumeWorkflowCompleter {

    private static final Logger _logger = LoggerFactory
            .getLogger(VolumeVpoolChangeTaskCompleter.class);

    private URI oldVpool;
    private Map<URI, URI> oldVpools;
    private final List<URI> migrationURIs = new ArrayList<URI>();

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
        try {
            switch (status) {
                case error:
                    _log.error("An error occurred during virtual pool change " + "- restore the old virtual pool to the volume(s): {}",
                            serviceCoded.getMessage());
                    List<Volume> volumesToUpdate = new ArrayList<Volume>();
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

                        if (volume.checkForRp()) {
                            // Special rollback for RP, RP+VPLEX, and MetroPoint
                            VirtualPool oldVpool = dbClient.queryObject(VirtualPool.class, oldVpoolURI);
                            RPHelper.rollbackProtectionOnVolume(volume, oldVpool, dbClient);
                        } else if (RPHelper.isVPlexVolume(volume)) {
                            // Special rollback for just VPLEX
                            rollBackVpoolOnVplexBackendVolume(volume, volumesToUpdate, dbClient);
                            volumesToUpdate.add(volume);
                        }
                    }
                    dbClient.updateObject(volumesToUpdate);

                    // Handle any VPlex errors
                    handleVplexVolumeErrors(dbClient);

                    break;
                case ready:
                    // The new Vpool has already been stored in the volume in BlockDeviceExportController.

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
                            recordBourneVolumeEvent(dbClient, id, evType, status, evDesc);
                        }
                    } catch (Exception ex) {
                        _logger.error("Failed to record block volume operation {}, err: {}", opType.toString(), ex);
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
                    break;
                case ready:
                default:
                    for (URI migrationURI : migrationURIs) {
                        dbClient.ready(Migration.class, migrationURI, getOpId());
                    }
            }
            super.complete(dbClient, status, serviceCoded);
        }
    }

    /**
     * Roll back vPool on vplex backend volumes.
     */
    private void rollBackVpoolOnVplexBackendVolume(Volume volume, List<Volume> volumesToUpdate, DbClient dbClient) {
        // Check if it is a VPlex volume, and get backend volumes
        Volume backendSrc = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient, false);
        if (backendSrc != null) {
            _log.info("Rolling back virtual pool on VPLEX backend Source volume {}({})", backendSrc.getId(), backendSrc.getLabel());

            backendSrc.setVirtualPool(oldVpool);
            _log.info("Set volume's virtual pool back to {}", oldVpool);
            volumesToUpdate.add(backendSrc);

            // VPlex volume, check if it is distributed
            Volume backendHa = VPlexUtil.getVPLEXBackendVolume(volume, false, dbClient, false);
            if (backendHa != null) {
                _log.info("Rolling back virtual pool on VPLEX backend Distributed volume {}({})", backendHa.getId(), backendHa.getLabel());

                VirtualPool oldVpoolObj = dbClient.queryObject(VirtualPool.class, oldVpool);
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
     * @param dbClient the DB client.
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
                        _log.warn("VPLEX virtual volume {} has active associated volume {}", volume.getLabel(), associatedVolume.getLabel());
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
}
