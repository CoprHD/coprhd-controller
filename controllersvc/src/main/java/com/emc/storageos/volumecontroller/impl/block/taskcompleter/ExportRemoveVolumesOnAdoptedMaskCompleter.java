/*
 * Copyright (c) 2013 EMC Corporation
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

public class ExportRemoveVolumesOnAdoptedMaskCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportRemoveVolumesOnAdoptedMaskCompleter.class);
    private final List<URI> _volumes;

    public ExportRemoveVolumesOnAdoptedMaskCompleter(URI egUri, URI emUri, List<URI> volumes,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _volumes = new ArrayList<URI>();
        _volumes.addAll(volumes);
    }


    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded coded) throws DeviceControllerException {
        try {
            URI exportMaskUri = getMask();
            ExportMask exportMask = (exportMaskUri != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            for (URI volumeURI : _volumes) {
                BlockObject volume = BlockObject.fetch(dbClient, volumeURI);
                if (exportMask != null && status == Operation.Status.ready) {
                    exportMask.removeFromUserCreatedVolumes(volume);
                    exportMask.removeVolume(volume.getId());
                }
            }

            if (exportMask != null) {
                URI pgURI = exportMask.getPortGroup();
                if (exportMask.getVolumes() == null ||
                        exportMask.getVolumes().isEmpty()) {
                    exportGroup.removeExportMask(exportMask.getId());
                    dbClient.markForDeletion(exportMask);
                    dbClient.updateObject(exportGroup);
                } else {
                    dbClient.updateObject(exportMask);
                }
                updatePortGroupVolumeCount(pgURI, dbClient);
            }

            _log.info(String.format(
                    "Done ExportMaskRemoveVolume - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskRemoveVolume - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

}
