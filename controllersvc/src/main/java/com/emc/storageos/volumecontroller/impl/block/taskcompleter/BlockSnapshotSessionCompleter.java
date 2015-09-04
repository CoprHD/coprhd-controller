/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * Abstract base task completer for operations on BlockSnapshotSession instances.
 */
@SuppressWarnings("serial")
public abstract class BlockSnapshotSessionCompleter extends TaskCompleter {

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURIs The URIs of the BlockSnapshotSession instances.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionCompleter(List<URI> snapSessionURIs, String taskId) {
        super(BlockSnapshotSession.class, snapSessionURIs, taskId);
    }

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionCompleter(URI snapSessionURI, String taskId) {
        super(BlockSnapshotSession.class, snapSessionURI, taskId);
    }

    /**
     * 
     * Records a ViPR event and creates an audit log entry to capture the results of the
     * BlockSnapshotSession operation.
     * 
     * @param dbClient A reference to a database client.
     * @param opType The snapshot session operation type.
     * @param status The result of the request.
     * @param snapSession A reference to the BlockSnapshotSession instance.
     * @param sourceObj A reference to the source object.
     */
    protected void recordBlockSnapshotSessionOperation(DbClient dbClient, OperationTypeEnum opType,
            Operation.Status status, BlockSnapshotSession snapSession, BlockObject sourceObj) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String eventType = opType.getEvType(opStatus);
            String description = getDescriptionOfResults(status, sourceObj, snapSession);
            s_logger.info("opType: {} detail: {}", opType.toString(), eventType + ':' + description);
            String snapSessionId = snapSession.getId().toString();
            String snapSessionLabel = snapSession.getLabel();
            String sourceObjId = sourceObj.getId().toString();
            String opStage = AuditLogManager.AUDITOP_END;

            // Record the ViPR event.
            recordBlockSnapshotSessionEvent(dbClient, snapSession, eventType, status, description);

            switch (opType) {
                case CREATE_SNAPSHOT_SESSION:
                    if (opStatus) {
                        AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, snapSessionId, snapSessionLabel, sourceObjId);
                    } else {
                        AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, snapSessionLabel, sourceObjId);
                    }
                    break;
                case RESTORE_SNAPSHOT_SESSION:
                case DELETE_SNAPSHOT_SESSION:
                case LINK_SNAPSHOT_SESSION_TARGET:
                case UNLINK_SNAPSHOT_SESSION_TARGET:
                case RELINK_SNAPSHOT_SESSION_TARGET:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, snapSessionId, snapSessionLabel, sourceObjId);
                    break;
                default:
                    s_logger.error("Unrecognized block snapshot sesion operation type");
            }
        } catch (Exception e) {
            s_logger.error("Failed to record block snapshot session operation {}, err: ", opType.toString(), e);
        }
    }

    /**
     * Records a ViPR event for a the BlockSnapshotSession operation.
     * 
     * @param dbClient A reference to a database client.
     * @param snapSession A reference to the snap shot session.
     * @param evtType The event type.
     * @param status The results of the request.
     * @param description A description of the results.
     */
    protected void recordBlockSnapshotSessionEvent(DbClient dbClient, BlockSnapshotSession snapSession, String evtType,
            Operation.Status status, String description) {
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);
        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(snapSession, evtType, description, "", dbClient,
                ControllerUtils.BLOCK_EVENT_SERVICE, RecordType.Event.name(), ControllerUtils.BLOCK_EVENT_SOURCE);
        try {
            eventManager.recordEvents(event);
            s_logger.info("Bourne {} event recorded", evtType);
        } catch (Exception ex) {
            s_logger.error("Failed to record event. Event description: {}. Error: ", evtType, ex);
        }
    }

    /**
     * Gets a description of the operation results.
     * 
     * @param status The results of the request.
     * @param sourceObj The source object for the snapshot session
     * @param snapSession The snapshot session
     * 
     * @return The operation description.
     */
    abstract protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession);
}
