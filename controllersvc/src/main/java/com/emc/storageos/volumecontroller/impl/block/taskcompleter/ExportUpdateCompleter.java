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
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.BlockExportController;

/**
 * Completer for {@link BlockExportController#exportGroupUpdate(URI, Map, List, List, List, String)}.
 * In order for this call to be repeatable, the export group is updated only when the
 * call. completes successfully. It is the completer task to update the export group
 * when the job status is ready.
 * 
 */
public class ExportUpdateCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportUpdateCompleter.class);
    private static final String EXPORT_UPDATED_MSG = "ExportGroup %s updated successfully";
    private static final String EXPORT_UPDATE_FAILED_MSG = "Failed to update ExportGroup %s";
    private Map<URI, Integer> _addedBlockObjects = new HashMap<URI, Integer>();
    private Map<URI, Integer> _removedBlockObjects = new HashMap<URI, Integer>();
    private List<URI> _addedInitiators = new ArrayList<URI>();
    private List<URI> _removedInitiators = new ArrayList<URI>();
    private List<URI> _addedHosts = new ArrayList<URI>();
    private List<URI> _removedHosts = new ArrayList<URI>();
    private List<URI> _addedClusters = new ArrayList<URI>();
    private List<URI> _removedClusters = new ArrayList<URI>();

    /**
     * Constructor for export updates.
     * 
     * @param egUri export group ID
     * @param addedBlockObjects block objects to add
     * @param removedBlockObjects block objects to remove
     * @param addedInitiators initiators to add
     * @param removedInitiators initiators to remove
     * @param addedHosts hosts to add
     * @param removedHosts hosts to remove
     * @param addedClusters clusters to add
     * @param removedClusters clusters to remove
     * @param task task id
     */
    public ExportUpdateCompleter(
            URI egUri,
            Map<URI, Integer> addedBlockObjects,
            Map<URI, Integer> removedBlockObjects,
            List<URI> addedInitiators, List<URI> removedInitiators,
            List<URI> addedHosts, List<URI> removedHosts,
            List<URI> addedClusters, List<URI> removedClusters,
            String task) {
        super(ExportGroup.class, egUri, task);
        _addedBlockObjects = addedBlockObjects;
        _removedBlockObjects = removedBlockObjects;
        _addedInitiators = addedInitiators;
        _removedInitiators = removedInitiators;
        _addedHosts = addedHosts;
        _removedHosts = removedHosts;
        _addedClusters = addedClusters;
        _removedClusters = removedClusters;
    }

    public ExportUpdateCompleter(URI egUri, String task) {
        super(ExportGroup.class, egUri, task);
    }

    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            Operation operation = new Operation();
            switch (status) {
                case error:
                    operation.error(coded);
                    break;
                case ready:
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
            // update the export group data if the job completes successfully
            if (status.equals(Operation.Status.ready)) {
                updateExportGroup(exportGroup);
            }
            dbClient.updateObject(exportGroup);
            _log.info("export_update completer: done");
            _log.info(String.format("Done ExportMaskUpdate - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            recordBlockExportOperation(dbClient, OperationTypeEnum.UPDATE_EXPORT_GROUP, status, eventMessage(status, exportGroup),
                    exportGroup);
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ExportMaskUpdate - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    private String eventMessage(Operation.Status status, ExportGroup exportGroup) {
        return (status == Operation.Status.ready) ?
                String.format(EXPORT_UPDATED_MSG, exportGroup.getLabel()) :
                String.format(EXPORT_UPDATE_FAILED_MSG, exportGroup.getLabel());
    }

    /**
     * Update the export group data.
     * 
     * @param exportGroup the export group to be updated.
     */
    private void updateExportGroup(ExportGroup exportGroup) {
        // TODO
        // Consider removing clusters when all their hosts are removed
        // and removing hosts when all their initiators are removed.
        if (_addedInitiators != null) {
            exportGroup.addInitiators(_addedInitiators);
        }

        if (_removedInitiators != null) {
            exportGroup.removeInitiators(_removedInitiators);
        }

        if (_addedHosts != null) {
            exportGroup.addHosts(_addedHosts);
        }

        if (_removedHosts != null) {
            exportGroup.removeHosts(_removedHosts);
        }

        if (_addedClusters != null) {
            exportGroup.addClusters(_addedClusters);
        }

        if (_removedClusters != null) {
            exportGroup.removeClusters(_removedClusters);
        }

        if (_addedBlockObjects != null) {
            exportGroup.addVolumes(_addedBlockObjects);
        }

        if (_removedBlockObjects != null) {
            exportGroup.removeVolumes(_removedBlockObjects);
        }
    }
}
