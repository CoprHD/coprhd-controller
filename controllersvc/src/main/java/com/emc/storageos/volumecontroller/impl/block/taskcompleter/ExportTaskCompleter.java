/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;

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
                case EXPORT_PATH_ADJUSTMENT:
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
     * Update volume count for the port group
     * 
     * @param pgURI - Port group URI
     * @param dbClient - DbClient
     */
    protected void updatePortGroupVolumeCount(URI pgURI, DbClient dbClient) {
        if (!NullColumnValueGetter.isNullURI(pgURI)) {
            StoragePortGroup portGroup = dbClient.queryObject(StoragePortGroup.class, pgURI);
            if (portGroup != null && !portGroup.getInactive()) {
                PortMetricsProcessor.computePortGroupVolumeCounts(portGroup, portGroup.getMetrics(), dbClient);
                dbClient.updateObject(portGroup);
                _logger.info(String.format("Updated the port group %s volume count", portGroup.getNativeGuid()));
            }
        }
    }
}
