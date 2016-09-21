/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

public abstract class ExportTaskCompleter extends TaskCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(ExportTaskCompleter.class);

    private URI _mask;

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
        ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
        cleanStaleReferences(exportGroup, dbClient);
        updateWorkflowStatus(status, coded);
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
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, exportGroup
                            .getLabel(), exportGroup.getId().toString(), exportGroup.getVirtualArray()
                            .toString(), exportGroup.getProject().toString());
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
     * Cleans ExportGroup's stale references
     * 
     * @param exportGroup
     * @param dbClient
     */
    private void cleanStaleReferences(ExportGroup exportGroup, DbClient dbClient) {

        cleanStaleMaskReferences(exportGroup, dbClient);

        cleanStaleInitiatorReferences(exportGroup, dbClient);

        cleanStaleHostReferences(exportGroup, dbClient);

        cleanStaleClusterReferences(exportGroup, dbClient);

        dbClient.updateObject(exportGroup);
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
                    _logger.error(e.getMessage(), e);
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
                    _logger.info("Stale mask {} will be removed from Export Group {}", maskURI, exportGroup.getId());
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
                _logger.info("Stale initiators {} will be removed from Export Group {}", staleInitiatorURIS, exportGroup.getId());
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
                _logger.info("Stale host references {} will be removed from Export Group {}", staleHostURIs, exportGroup.getId());
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
                _logger.info("Stale cluster references {} will be removed from Export Group {}", staleClusterURIs, exportGroup.getId());
            }
        }
    }

}
