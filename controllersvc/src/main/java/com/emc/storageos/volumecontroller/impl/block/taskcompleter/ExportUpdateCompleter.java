/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ExportUtils;
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
    private Set<URI> _addedInitiators = new HashSet<>();
    private Set<URI> _removedInitiators = new HashSet<>();
    private Set<URI> _addedHosts = new HashSet<>();
    private Set<URI> _removedHosts = new HashSet<>();
    private Set<URI> _addedClusters = new HashSet<>();
    private Set<URI> _removedClusters = new HashSet<>();

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
            Set<URI> addedInitiators, Set<URI> removedInitiators,
            Set<URI> addedHosts, Set<URI> removedHosts,
            Set<URI> addedClusters, Set<URI> removedClusters,
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
            // Update the export group data.
            if (status.equals(Operation.Status.ready)) {
                updateExportGroup(exportGroup, dbClient);
            } else {
                dbClient.updateObject(exportGroup);
            }

            List<URI> exportMaskUriList = getMasksToGetDeleted();
            

            if (Operation.isTerminalState(status) && needToRunExportGroupCleanup(dbClient)) {
                // Clean stale references from EG if the status is either ready or error.
                ExportUtils.cleanStaleReferences(exportGroup, dbClient);
            }
            _log.info("export_update completer: done");
            _log.info(String.format("Done ExportMaskUpdate - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            recordBlockExportOperation(dbClient, OperationTypeEnum.UPDATE_EXPORT_GROUP, status, eventMessage(status, exportGroup),
                    exportGroup);
            
            // Check to see if Export Group needs to be cleaned up
            ExportUtils.checkExportGroupForCleanup(exportGroup, dbClient);
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
     * @param dbClient {@link DbClient}
     */
    private void updateExportGroup(ExportGroup exportGroup, DbClient dbClient) {
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
        dbClient.updateObject(exportGroup);
    }

    /**
     * If the initiators and hosts are added, but there are no volumes in the export Group, then no need to run.
     * If the initiators and hosts and clusters are added, but there are no volumes in the export Group, then no need to run.
     * If volumes are added, but there are no initiators, no need to cleanup
     *
     * This is needed to handle CLI scenarios like Create Empty Export Group, Add host to Export Group, Add Volume to Export Group.
     * the 1st 2 cases doesn't need to run Cleanup, but the 3rd needs to, as real export happens.
     *
     * Other case : Create Empty Export Group, Add Volume to Export Group, Add Host to Export Group.
     * the 1st 2 cases doesn't need to run Cleanup, but the 3rd needs to, as real export happens.
     * 
     * @return
     */
    private boolean needToRunExportGroupCleanup(DbClient dbClient) {
        boolean needtoRunExportCleanupTask = true;
        ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
        if (null == exportGroup.getVolumes() || exportGroup.getVolumes().isEmpty()) {
            if ((_addedInitiators != null && !_addedInitiators.isEmpty()) ||
                    (_addedHosts != null && !_addedHosts.isEmpty())
                    || (_addedClusters != null && !_addedClusters.isEmpty())) {
                _log.info(
                        "No need to run Export Clean up, as export Group contains no volumes and the request includes to add compute resource.");
                needtoRunExportCleanupTask = false;
            }
        } else if (null == exportGroup.getInitiators() || exportGroup.getInitiators().isEmpty()) {
            if (_addedBlockObjects != null && !_addedBlockObjects.isEmpty()) {
                needtoRunExportCleanupTask = false;
                _log.info(
                        "No need to run Export Clean up, as export Group contains no initiators and the request includes to add volumes.");
            }
        }
        return needtoRunExportCleanupTask;
    }
}
