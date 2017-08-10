/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.workflow.WorkflowService;

public class ExportMaskRemoveVolumeCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportMaskRemoveVolumeCompleter.class);

    private Collection<URI> _volumes;

    /**
     * Constructor for ExportMaskRemoveVolumeCompleter.
     * 
     * @param egUri -- ExportGroup URI
     * @param emUri -- ExportMask URI
     * @param volumes -- List<URI> of volumes being removed.
     * @param task -- API task id.
     */
    public ExportMaskRemoveVolumeCompleter(URI egUri, URI emUri, Collection<URI> volumes,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _volumes = new ArrayList<URI>();
        _volumes.addAll(volumes);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(getOpId());
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            if (exportMask != null) {
                if ((status == Operation.Status.error) && (isRollback) && (coded instanceof ServiceError)) {
                    ServiceError error = (ServiceError) coded;
                    String originalMessage = error.getMessage();
                    StorageSystem storageSystem = exportMask != null
                            ? dbClient.queryObject(StorageSystem.class, exportMask.getStorageDevice())
                            : null;
                    List<Volume> volumes = dbClient.queryObject(Volume.class, _volumes);
                    StringBuffer volumesJoined = new StringBuffer();
                    if (volumes != null && !volumes.isEmpty()) {
                        Iterator<Volume> initIter = volumes.iterator();
                        while (initIter.hasNext()) {
                            Volume volume = initIter.next();
                            volumesJoined.append(volume.forDisplay());
                            if (initIter.hasNext()) {
                                volumesJoined.append(",");
                            }
                        }
                    }
                    String additionMessage = String.format(
                            "Rollback encountered problems removing volume(s) %s from export mask %s on storage system %s and may require manual clean up",
                            volumesJoined.toString(), exportMask.getMaskName(),
                            storageSystem != null ? storageSystem.forDisplay() : "Unknown");
                    String updatedMessage = String.format("%s\n%s", originalMessage, additionMessage);
                    error.setMessage(updatedMessage);
                }

                if (status == Operation.Status.ready || (status == Operation.Status.error && isRollback)) {
                    ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
                    for (URI volumeURI : _volumes) {
                        BlockObject volume = BlockObject.fetch(dbClient, volumeURI);
                        exportMask.removeFromUserCreatedVolumes(volume);
                        exportMask.removeVolume(volume.getId());
                    }

                    if (exportMask != null) {
                        URI pgURI = exportMask.getPortGroup();
                        if (exportMask.getVolumes() == null ||
                                exportMask.getVolumes().isEmpty()) {
                            List<URI> impactedExportGroups = getExportGroups();
                            if (impactedExportGroups != null && !impactedExportGroups.isEmpty()) {
                                List<ExportGroup> egs = dbClient.queryObject(ExportGroup.class, impactedExportGroups);
                                for (ExportGroup eg : egs) {
                                    eg.removeExportMask(exportMask.getId());
                                }
                                dbClient.updateObject(egs);
                            } else {
                                exportGroup.removeExportMask(exportMask.getId());
                                dbClient.updateObject(exportGroup);
                            }
                            dbClient.markForDeletion(exportMask);
                        } else {
                            dbClient.updateObject(exportMask);
                        }
                        updatePortGroupVolumeCount(pgURI, dbClient);
                    }
                        
                    _log.info(String.format(
                            "Done ExportMaskRemoveVolume - Id: %s, OpId: %s, status: %s",
                            getId().toString(), getOpId(), status.name()));
                }
            }

        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskRemoveVolume - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}
