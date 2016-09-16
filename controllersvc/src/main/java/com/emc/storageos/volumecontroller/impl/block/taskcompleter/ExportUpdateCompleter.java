/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

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
                updateExportGroup(exportGroup, dbClient);
            }
            if (exportGroup != null && exportGroup.checkInternalFlags(DataObject.Flag.TASK_IN_PROGRESS)) {
                _log.info("Clearing the TASK_IN_PROGRESS flag from export group {}", exportGroup.getId());
                exportGroup.clearInternalFlags(DataObject.Flag.TASK_IN_PROGRESS);
            }
            dbClient.updateObject(exportGroup);
            
            _log.info("export_update completer: done");
            _log.info(String.format("Done ExportMaskUpdate - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            recordBlockExportOperation(dbClient, OperationTypeEnum.UPDATE_EXPORT_GROUP, status, eventMessage(status, exportGroup),
                    exportGroup);
            
            // If there are no masks or volumes associated with this export group, and it's an internal (VPLEX/RP)
            // export group, delete the export group automatically.
            if (CollectionUtils.isEmpty(exportGroup.getVolumes())
                    || CollectionUtils.isEmpty(ExportMaskUtils.getExportMasks(dbClient, exportGroup))) {
                _log.info(String.format("Marking export group [%s %s] for deletion.", 
                        (exportGroup != null ? exportGroup.getLabel() : ""), getId()));
                dbClient.markForDeletion(exportGroup);
            } 
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

        cleanStaleMaskReferences(exportGroup, dbClient);

        cleanStaleInitiatorReferences(exportGroup, dbClient);

        cleanStaleHostReferences(exportGroup, dbClient);

        cleanStaleClusterReferences(exportGroup, dbClient);
    }

    /**
     * Cleans stale mask references from export group instance
     * 
     * @param exportGroup {@link ExportGroup}
     * @param dbClient {@link DbClient}
     */
    private void cleanStaleMaskReferences(ExportGroup exportGroup, DbClient dbClient) {
        // Clean stale export mask references from ExportGroup.
        StringSet exportMasks = exportGroup.getExportMasks();
        if (!CollectionUtils.isEmpty(exportMasks)) {
            List<URI> staleMasks = new ArrayList<>();
            StringSet exportGroupInitiators = exportGroup.getInitiators();
            for (String mask : exportMasks) {
                boolean isStaleMask = false;
                URI maskURI = null;
                try {
                    maskURI = URI.create(mask);
                } catch (Exception e) {
                    _log.error(e.getMessage(), e);
                    isStaleMask = true;
                }
                if (maskURI != null) {
                    ExportMask maskObj = dbClient.queryObject(ExportMask.class, maskURI);
                    if (maskObj != null && !CollectionUtils.isEmpty(maskObj.getInitiators())) {
                        isStaleMask = Sets.intersection(exportGroupInitiators, maskObj.getInitiators()).isEmpty();
                    } else {
                        isStaleMask = true;
                    }
                }
                if (isStaleMask) {
                    staleMasks.add(maskURI);
                    _log.info("Stale mask {} will be removed from Export Group {}", maskURI, exportGroup.getId());
                }
            }
            if (!CollectionUtils.isEmpty(staleMasks)) {
                exportGroup.removeExportMasks(staleMasks);
            }
        }
    }

    /**
     * Cleans stale initiator references from export group instance
     * 
     * @param exportGroup {@link ExportGroup}
     * @param dbClient {@link DbClient}
     */
    private void cleanStaleInitiatorReferences(ExportGroup exportGroup, DbClient dbClient) {
        StringSet exportGroupInitiators = exportGroup.getInitiators();
        if (!CollectionUtils.isEmpty(exportGroupInitiators) && !CollectionUtils.isEmpty(exportGroup.getExportMasks())) {
            Set<String> allMaskInitiators = new HashSet<>();
            for (String mask : exportGroup.getExportMasks()) {
                ExportMask maskObj = dbClient.queryObject(ExportMask.class, URI.create(mask));
                if (maskObj != null && !CollectionUtils.isEmpty(maskObj.getInitiators())) {
                    allMaskInitiators.addAll(maskObj.getInitiators());
                }
            }
            // Stale initiators = EG intiators - all initiators available in all the eg.masks
            Set<String> staleInitiators = Sets.difference(exportGroupInitiators, allMaskInitiators);
            if (!CollectionUtils.isEmpty(staleInitiators)) {
                Collection<URI> staleInitiatorURIS = Collections2.transform(staleInitiators,
                        CommonTransformerFunctions.FCTN_STRING_TO_URI);
                exportGroup.removeInitiators(new ArrayList<>(staleInitiatorURIS));
                _log.info("Stale initiators {} will be removed from Export Group {}", staleInitiatorURIS, exportGroup.getId());
            }
        }
    }

    /**
     * Cleans stale host references from export group instance
     * 
     * @param exportGroup {@link ExportGroup}
     * @param dbClient {@link DbClient}
     */
    private void cleanStaleHostReferences(ExportGroup exportGroup, DbClient dbClient) {
        StringSet exportGroupInitiators = exportGroup.getInitiators();
        if (!CollectionUtils.isEmpty(exportGroup.getHosts()) && !CollectionUtils.isEmpty(exportGroupInitiators)) {
            Set<String> egHosts = new HashSet<>();
            Collection<Initiator> initiators = Collections2.transform(exportGroupInitiators,
                    CommonTransformerFunctions.fctnStringToInitiator(dbClient));
            for (Initiator initiator : initiators) {
                if (initiator.getHost() != null) {
                    egHosts.add(initiator.getHost().toString());
                }
            }
            Set<String> staleHosts = Sets.difference(exportGroup.getHosts(), egHosts);
            if (!CollectionUtils.isEmpty(staleHosts)) {
                Collection<URI> staleHostURIs = Collections2.transform(staleHosts,
                        CommonTransformerFunctions.FCTN_STRING_TO_URI);
                exportGroup.removeHosts(new ArrayList<>(staleHostURIs));
                _log.info("Stale host references {} will be removed from Export Group {}", staleHostURIs, exportGroup.getId());
            }
        }
    }

    /**
     * Cleans stale cluster references from export group instance
     * 
     * @param exportGroup {@link ExportGroup}
     * @param dbClient {@link DbClient}
     */
    private void cleanStaleClusterReferences(ExportGroup exportGroup, DbClient dbClient) {
        StringSet exportGroupInitiators = exportGroup.getInitiators();
        if (!CollectionUtils.isEmpty(exportGroup.getClusters()) && !CollectionUtils.isEmpty(exportGroupInitiators)) {
            Set<String> egClusterURIs = new HashSet<>();
            Collection<Host> hosts = Collections2.transform(exportGroup.getHosts(),
                    CommonTransformerFunctions.fctnStringToHost(dbClient));
            for (Host host : hosts) {
                if (host.getCluster() != null) {
                    egClusterURIs.add(host.getCluster().toString());
                }
            }
            Set<String> staleClusters = Sets.difference(exportGroup.getClusters(), egClusterURIs);
            if (!CollectionUtils.isEmpty(staleClusters)) {
                Collection<URI> staleClusterURIs = Collections2.transform(staleClusters,
                        CommonTransformerFunctions.FCTN_STRING_TO_URI);
                exportGroup.removeClusters(new ArrayList<>(staleClusterURIs));
                _log.info("Stale cluster references {} will be removed from Export Group {}", staleClusterURIs, exportGroup.getId());
            }
        }
    }

}
