/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

public abstract class VolumeTaskCompleter extends TaskLockingCompleter {

    private static final String VOLUME_TASK_MSG_SUCCESS = "Volume operation completed successfully for volume %s";
    private static final String VOLUME_TASK_MSG_FAILURE = "Volume operation failed for volume %s";

    /**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(VolumeTaskCompleter.class);

    /**
     * @param clazz
     * @param id
     * @param opId
     */
    public VolumeTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    /**
     * @param clazz
     * @param volUris
     * @param task
     */
    public VolumeTaskCompleter(Class<Volume> clazz, List<URI> volUris, String task) {
        super(clazz, volUris, task);
    }

    /**
     * Generate and Record a Bourne volume specific event
     * 
     * @param dbClient
     * @param id
     * @param evtType
     * @param status
     * @param desc
     * @throws Exception
     */
    public static void recordBourneVolumeEvent(DbClient dbClient,
            URI id, String evtType, Operation.Status status, String desc)
            throws Exception {

        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        Volume volumeObj = dbClient.queryObject(Volume.class, id);
        RecordableBourneEvent event = ControllerUtils
                .convertToRecordableBourneEvent(volumeObj, evtType,
                        desc, "", dbClient,
                        ControllerUtils.BLOCK_EVENT_SERVICE,
                        RecordType.Event.name(),
                        ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            eventManager.recordEvents(event);
            _logger.info("Bourne {} event recorded for Volume {}", evtType, id);
        } catch (Exception ex) {
            _logger.error(
                    "Failed to record event. Event description: {}. Error: ",
                    evtType, ex);
        }
    }

    /**
     * Record block volume related event and audit
     * 
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param evDesc event description
     * @param extParam parameters array from which we could generate detail audit message
     */
    public void recordBlockVolumeOperation(DbClient dbClient, OperationTypeEnum opType, Operation.Status status, Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String evType;
            evType = opType.getEvType(opStatus);
            String evDesc = opType.getDescription();
            String opStage = AuditLogManager.AUDITOP_END;
            _logger.info("opType: {} detail: {}", opType.toString(), evType.toString() + ':' + evDesc);

            recordBourneVolumeEvent(dbClient, getId(), evType, status, evDesc);

            String id = (String) extParam[0];
            switch (opType) {
                case CREATE_BLOCK_VOLUME:
                case DELETE_BLOCK_VOLUME:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, id);
                    break;
                case EXPAND_BLOCK_VOLUME:
                    String size = (String) extParam[1];
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, id, size);
                    break;
                case CREATE_VOLUME_FULL_COPY:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, id);
                    break;
                case DETACH_VOLUME_FULL_COPY:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, id);
                    break;
                case RESTORE_VOLUME_FULL_COPY:
                case RESYNCHRONIZE_VOLUME_FULL_COPY:
                case ACTIVATE_VOLUME_FULL_COPY:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, id);
                    break;
                default:
                    _logger.error("unrecognized block volume operation type");
            }
        } catch (Exception e) {
            _logger.error("Failed to record block volume operation {}, err: {}", opType.toString(), e);
        }
    }

    protected String eventMessage(Operation.Status status, Volume volume) {
        return (status == Operation.Status.ready) ?
                String.format(VOLUME_TASK_MSG_SUCCESS, volume.getLabel()) :
                String.format(VOLUME_TASK_MSG_FAILURE, volume.getLabel());
    }
}
