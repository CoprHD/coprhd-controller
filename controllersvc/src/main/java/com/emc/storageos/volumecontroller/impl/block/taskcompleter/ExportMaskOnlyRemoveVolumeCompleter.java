/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * This is a very specialized completer for use only by the VPlex Backend Manager.
 * It deletes a volume out of the ExportMask/ExportGroup, but will not delete the ExportMask,
 * even if it has no volumes, unless it was externally created (i.e. createdBySystem == false)
 * This is because the ExportMask may be reusued at a later time
 * if volumes are subsequently created by the VPlex on that Storage Array.
 */
public class ExportMaskOnlyRemoveVolumeCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportMaskOnlyRemoveVolumeCompleter.class);

    private List<URI> _volumes;         // URI of volumes being removed

    /**
     * Constructor for ExportMaskRemoveVolumeCompleter.
     * 
     * @param egUri -- ExportGroup URI
     * @param emUri -- ExportMask URI
     * @param volumes -- List<URI> of volumes being removed.
     * @param task -- API task id.
     */
    public ExportMaskOnlyRemoveVolumeCompleter(URI egUri, URI emUri, List<URI> volumes,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _volumes = new ArrayList<URI>();
        _volumes.addAll(volumes);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            for (URI volumeURI : _volumes) {
                BlockObject volume = BlockObject.fetch(dbClient, volumeURI);
                if (exportMask != null && status == Operation.Status.ready) {
                    exportMask.removeFromUserCreatedVolumes(volume);
                    exportMask.removeVolume(volume.getId());
                    exportGroup.removeVolume(volume.getId());
                }
            }

            // If ViPR did not create the ExportMask, mark the ExportMask and
            // ExportGroup for deletion if and when they are empty.
            if (exportMask.getCreatedBySystem() == false
                    && (exportMask.getVolumes() == null || exportMask.getVolumes().isEmpty())) {
                dbClient.markForDeletion(exportMask);
                dbClient.markForDeletion(exportGroup);
            } else {
                // If the ExportGroup does not contain the exportMask, add it back.
                exportGroup.addExportMask(getMask());
                dbClient.updateObject(exportMask);
                dbClient.updateObject(exportGroup);
            }

            _log.info(String.format(
                    "Done ExportMaskOnlyRemoveVolume - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskOnlyRemoveVolume - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}
