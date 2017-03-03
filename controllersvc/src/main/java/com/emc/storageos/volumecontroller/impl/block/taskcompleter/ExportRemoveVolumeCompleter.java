/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ExportUtils;

public class ExportRemoveVolumeCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportRemoveVolumeCompleter.class);
    private static final String EXPORT_REMOVE_VOLUME_MSG = "Volume %s removed from ExportGroup %s";
    private static final String EXPORT_REMOVE_VOLUME_MSG_FAILED_MSG = "Failed to remove volume %s from ExportGroup %s";

    private List<URI> _volumes;
    private List<URI> _exportMasksToRemove;
    private Map<URI, List<URI>> _exportMaskToRemovedVolumeMap;

    public ExportRemoveVolumeCompleter(URI egUri, List<URI> volumes,
            String task) {
        super(ExportGroup.class, egUri, task);
        _volumes = new ArrayList<URI>();
        _volumes.addAll(volumes);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            for (URI volumeURI : _volumes) {
                BlockObject volume = BlockObject.fetch(dbClient, volumeURI);
                _log.info("export_volume_remove: completed");
                recordBlockExportOperation(dbClient, OperationTypeEnum.DELETE_EXPORT_VOLUME, status,
                        eventMessage(status, volume, exportGroup), exportGroup, volume);
            }

            Operation operation = new Operation();
            switch (status) {
                case error:
                    operation.error(coded);
                    break;
                case ready:
                    for (URI volumeURI : _volumes) {
                        exportGroup.removeVolume(volumeURI);
                    }

                    if (null != _exportMasksToRemove) {
                        for (URI exportMaskUri : _exportMasksToRemove) {
                            exportGroup.removeExportMask(exportMaskUri);
                        }
                    }

                    ExportUtils.handleExportMaskVolumeRemoval(dbClient, _exportMaskToRemovedVolumeMap, getId());

                    operation.ready();
                    break;
                case suspended_no_error:
                    operation.suspendedNoError();
                    break;
                case suspended_error:
                    operation.suspendedError(coded);
                    break;
                default:
                    break;
            }
            exportGroup.getOpStatus().updateTaskStatus(getOpId(), operation);
            dbClient.updateObject(exportGroup);

            // Check to see if Export Group needs to be cleaned up
            ExportUtils.checkExportGroupForCleanup(exportGroup, dbClient);

            _log.info(String.format("Done ExportMaskRemoveVolume - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ExportMaskRemoveVolume - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    private String eventMessage(Operation.Status status, BlockObject volume, ExportGroup exportGroup) {
        return (status == Operation.Status.ready) ?
                String.format(EXPORT_REMOVE_VOLUME_MSG, volume.getLabel(), exportGroup.getLabel()) :
                String.format(EXPORT_REMOVE_VOLUME_MSG_FAILED_MSG, volume.getLabel(), exportGroup.getLabel());
    }

    /**
     * Add an ExportMask URI that should be removed from this completer's ExportGroup at the
     * end of the workflow.
     * 
     * @param exportMaskUri the URI of the export mask to be removed.
     */
    public void addExportMaskToRemove(URI exportMaskUri) {
        if (null == _exportMasksToRemove) {
            _exportMasksToRemove = new ArrayList<URI>();
        }

        _exportMasksToRemove.add(exportMaskUri);
    }

    /**
     * Add a mapping for Volume URIs that should be removed from an ExportMask at the end of the workflow.
     * 
     * @param exportMaskUri the ExportMask URI to update
     * @param volumeUrisToBeRemoved the list of Volume URIs to remove from the ExportMask
     */
    public void addExportMaskToRemovedVolumeMapping(URI exportMaskUri, List<URI> volumeUrisToBeRemoved) {
        if (null == _exportMaskToRemovedVolumeMap) {
            _exportMaskToRemovedVolumeMap = new HashMap<URI, List<URI>>();
        }

        _exportMaskToRemovedVolumeMap.put(exportMaskUri, volumeUrisToBeRemoved);
    }

}
