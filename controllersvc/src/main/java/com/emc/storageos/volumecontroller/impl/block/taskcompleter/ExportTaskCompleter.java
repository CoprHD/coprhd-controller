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
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;

public abstract class ExportTaskCompleter extends TaskCompleter {

    private static final long serialVersionUID = 1L;
    private static final Logger _logger = LoggerFactory.getLogger(ExportTaskCompleter.class);

    private URI _mask;
    private Map<String, String> _altVarrayMap;
    private Map<URI, StringSetMap> _exportMaskToOldZoningMapMap;
    private Set<URI> _exportMasksCreated;
    private Set<URI> _exportMasksToBeAdded;
    private Set<URI> _exportMasksToBeRemoved;
    private Set<URI> _exportMasksToBeDeleted;
    private Map<URI, List<URI>> _exportMaskToAddedInitiatorMap;
    private Map<URI, List<URI>> _exportMaskToRemovedVolumeMap;

    public ExportTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public ExportTaskCompleter(Class clazz, URI id, URI emURI, String opId) {
        super(clazz, id, opId);
        setMask(emURI);
    }

    public URI getMask() {
        return _mask;
    }

    public void setMask(URI mask) {
        _mask = mask;
    }

    /**
     * @param dbClient
     * @param evtType
     * @param status
     * @param desc
     * @throws Exception
     */
    public void recordBlockExportEvent(DbClient dbClient, URI uri, String evtType,
            Operation.Status status, String desc) throws Exception {
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, uri);
        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(exportGroup,
                evtType, desc, "", dbClient, ControllerUtils.BLOCK_EVENT_SERVICE,
                RecordType.Event.name(), ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            eventManager.recordEvents(event);
            _logger.info("Bourne {} event recorded", evtType);
        } catch (Exception ex) {
            _logger.error("Failed to record event. Event description: {}. Error: ", evtType, ex);
        }
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        updateWorkflowStatus(status, coded);

        ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
        Map<URI, ExportMask> exportMaskCache = null;
        switch (status) {
            case ready:
                if (_altVarrayMap != null && !_altVarrayMap.isEmpty()) {
                    for (Entry<String, String> entry : _altVarrayMap.entrySet()) {
                        exportGroup.putAltVirtualArray(entry.getKey(), entry.getValue());
                    }
                }

                if (null != _exportMasksToBeAdded) {
                    for (URI exportMaskUri : _exportMasksToBeAdded) {
                        exportGroup.addExportMask(exportMaskUri);
                    }
                }

                if (null != _exportMasksToBeRemoved) {
                    for (URI exportMaskUri : _exportMasksToBeRemoved) {
                        exportGroup.removeExportMask(exportMaskUri);
                    }
                }

                if (null != _exportMasksToBeDeleted) {
                    for (URI exportMaskUri : _exportMasksToBeDeleted) {
                        exportGroup.removeExportMask(exportMaskUri);
                        ExportMask exportMask = ExportUtils.getExportMaskWithCache(exportMaskCache, exportMaskUri, dbClient);
                        dbClient.removeObject(exportMask);
                    }
                }

                if (null != _exportMaskToAddedInitiatorMap && !_exportMaskToAddedInitiatorMap.isEmpty()) {
                    for (Entry<URI, List<URI>> entry : _exportMaskToAddedInitiatorMap.entrySet()) {
                        ExportMask exportMask = ExportUtils.getExportMaskWithCache(exportMaskCache, entry.getKey(), dbClient);
                        if (exportMask != null) {
                            List<Initiator> inits = dbClient.queryObject(Initiator.class, entry.getValue());
                            for (Initiator init : inits) {
                                // add all the the initiators the user has requested to add
                                // to the exportMask initiators list
                                exportMask.addInitiator(init);
                                if (!exportMask.hasExistingInitiator(init)) {
                                    // add only those initiator to the user added list
                                    // which do not exist on the the device already.
                                    exportMask.addToUserCreatedInitiators(init);
                                }
                            }
                        }
                    }
                }
 
                ExportUtils.handleExportMaskVolumeRemoval(dbClient, _exportMaskToRemovedVolumeMap, getId());

                break;
            case error:
                if (null != _exportMasksCreated && !_exportMasksCreated.isEmpty()) {
                    // remove and delete any export masks created by this export task
                    List<ExportMask> exportMasks = dbClient.queryObject(ExportMask.class, _exportMasksCreated);
                    for (ExportMask exportMask : exportMasks) {
                        exportGroup.removeExportMask(exportMask.getId());
                        dbClient.removeObject(exportMask);
                    }
                }

                if (_exportMaskToOldZoningMapMap != null && !_exportMaskToOldZoningMapMap.isEmpty()) {
                    // revert any zoningMaps that were updated as part of this export task
                    for (Entry<URI, StringSetMap> entry : _exportMaskToOldZoningMapMap.entrySet()) {
                        ExportMask exportMask = ExportUtils.getExportMaskWithCache(exportMaskCache, entry.getKey(), dbClient);
                        if (exportMask != null) {
                            exportMask.setZoningMap(entry.getValue());
                        }
                    }
                }

            default:
                _logger.warn("Unhandled status: " + status);
                break;
        }
        dbClient.updateObject(exportGroup);
        ExportUtils.persistExportMaskCache(exportMaskCache, dbClient);
    }

    /**
     * Record block export group related event and audit
     * 
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param evDesc event description
     * @param extParam parameters array from which we could generate detail
     *            audit message
     */
    public void recordBlockExportOperation(DbClient dbClient, OperationTypeEnum opType,
            Operation.Status status, String evDesc, Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status);
            String evType;
            evType = opType.getEvType(opStatus);
            String opStage = AuditLogManager.AUDITOP_END;
            _logger.info("opType: {} detail: {}", opType.toString(), evType + ':' + evDesc);

            ExportGroup exportGroup = (ExportGroup) extParam[0];

            recordBlockExportEvent(dbClient, exportGroup.getId(), evType, status, evDesc);

            switch (opType) {
                case CREATE_EXPORT_GROUP:
                case UPDATE_EXPORT_GROUP:
                case DELETE_EXPORT_GROUP:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, exportGroup.getLabel(), 
                            exportGroup.getId().toString(), exportGroup.getVirtualArray().toString(),
                            exportGroup.getProject().toString());
                    break;
                case ADD_EXPORT_INITIATOR:
                case DELETE_EXPORT_INITIATOR:
                    Initiator initiator = (Initiator) extParam[1];
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage,
                            initiator.getProtocol(), initiator.getInitiatorNode(),
                            initiator.getInitiatorPort(), initiator.getHostName(),
                            exportGroup.getLabel(), exportGroup.getId().toString());
                    break;
                case ADD_EXPORT_VOLUME:
                case DELETE_EXPORT_VOLUME:
                    BlockObject bo = (BlockObject) extParam[1];
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, bo.getId()
                            .toString(), exportGroup.getLabel(), exportGroup.getId().toString());
                    break;
                default:
                    _logger.error("unrecognized block export operation type");
            }
            _logger.info(String.format("ExportGroup after %s Operation%n%s", opType, exportGroup.toString()));
        } catch (Exception e) {
            _logger.error("Failed to record block export operation {}, err: {}", opType.toString(),
                    e);
        }
    }

    /**
     * Checks if the given ExportGroup has remaining active masks.
     * 
     * @param dbClient
     * @param exportGroup
     * @return true if the given ExportGroup has any remaining active masks.
     */
    protected boolean hasActiveMasks(DbClient dbClient, ExportGroup exportGroup) {

        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(dbClient, exportGroup);
        for (ExportMask exportMask : exportMasks) {
            if (exportMask != null && !exportMask.getInactive()) {
                _logger.info("this ExportGroup has active masks: " + exportGroup.getGeneratedName());
                return true;
            }
        }

        _logger.info("this ExportGroup does not have any remaining active masks: "
                + exportGroup.getGeneratedName());
        return false;
    }

    /**
     * Add a StorageSystem URI to alternate VirtualArray URI mapping to be applied to the
     * ExportGroup on successful completion.
     * 
     * @param storageSystemUri storage system URI for the key
     * @param varrayUri varray URI for the value
     */
    public void addAltVarrayMapping(String storageSystemUri, String varrayUri) {
        if (_altVarrayMap == null) {
            _altVarrayMap = new HashMap<String, String>();
        }

        _altVarrayMap.put(storageSystemUri, varrayUri);
    }

    /**
     * Add an ExportMask URI to zoning map entry to applied on successful completion.
     * 
     * @param exportMaskUri URI of the ExportMask to update
     * @param zoningMapEntries a zoning map StringSetMap to set on the ExportMask
     */
    public void addExportMaskToOldZoningMapMapping(URI exportMaskUri, StringSetMap zoningMapEntries) {
        if (_exportMaskToOldZoningMapMap == null) {
            _exportMaskToOldZoningMapMap = new HashMap<URI, StringSetMap>();
        }

        _exportMaskToOldZoningMapMap.put(exportMaskUri, zoningMapEntries);
    }

    /**
     * Add to the list of URIs for ExportMasks that were created as
     * part of this export task and should be deleted in the event of
     * failure.
     * 
     * @param exportMaskUri the URI of the ExportMask created
     */
    public void addToExportMasksCreated(URI exportMaskUri) {
        if (_exportMasksCreated == null) {
            _exportMasksCreated = new HashSet<URI>();
        }

        _exportMasksCreated.add(exportMaskUri);
    }

    /**
     * Add to the list of URIs for ExportMasks that should be added to
     * the ExportGroup at the end of the task.
     * 
     * @param exportMaskUri the URI of the ExportMask to add
     */
    public void addToExportMasksToBeAdded(URI exportMaskUri) {
        if (_exportMasksToBeAdded == null) {
            _exportMasksToBeAdded = new HashSet<URI>();
        }

        _exportMasksToBeAdded.add(exportMaskUri);
    }

    /**
     * Add an ExportMask URI that should be removed from this completer's ExportGroup at the
     * end of the workflow.
     * 
     * @param exportMaskUri the URI of the export mask to be removed.
     */
    public void addExportMaskToRemove(URI exportMaskUri) {
        if (null == _exportMasksToBeRemoved) {
            _exportMasksToBeRemoved = new HashSet<URI>();
        }

        _exportMasksToBeRemoved.add(exportMaskUri);
    }

    /**
     * Add an ExportMask URI that should be removed from this completer's ExportGroup at the
     * end of the workflow and also deleted from the database.
     * 
     * @param exportMaskUri the URI of the export mask to be deleted.
     */
    public void addExportMaskToDelete(URI exportMaskUri) {
        if (null == _exportMasksToBeDeleted) {
            _exportMasksToBeDeleted = new HashSet<URI>();
        }

        _exportMasksToBeDeleted.add(exportMaskUri);
    }

    /**
     * Add a mapping for Initiator URIs that should be added to an ExportMask at the end of the workflow.
     * 
     * @param exportMaskUri the ExportMask URI to update
     * @param initiatorUrisToBeAdded the list of Initiator URIs to add to the ExportMask
     */
    public void addExportMaskToAddedInitiatorMapping(URI exportMaskUri, List<URI> initiatorUrisToBeAdded) {
        if (null == _exportMaskToAddedInitiatorMap) {
            _exportMaskToAddedInitiatorMap = new HashMap<URI, List<URI>>();
        }

        _exportMaskToAddedInitiatorMap.put(exportMaskUri, initiatorUrisToBeAdded);
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
