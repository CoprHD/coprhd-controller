/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * 
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionCreateWorkflowCompleter extends TaskCompleter {

    // Message constants.
    public static final String SNAPSHOT_SESSION_CREATE_SUCCESS_MSG = "Block Snapshot Session %s created for source %s";
    public static final String SNAPSHOT_SESSION_CREATE_FAIL_MSG = "Failed to create Block Snapshot Session %s for source %s";

    // A map of the BlockSnapshot instances linked to the session for the request.
    private final Map<URI, List<URI>> _sessionSnapshotMap;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionCreateWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURIs The URIs of the BlockSnapshotSession instances created in the request.
     * @param sessionSnapshotsMap A map of the BlockSnapshot instances linked to the session for the request.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionCreateWorkflowCompleter(List<URI> snapSessionURIs, Map<URI, List<URI>> sessionSnapshotsMap, String taskId) {
        super(BlockSnapshotSession.class, snapSessionURIs, taskId);
        _sessionSnapshotMap = sessionSnapshotsMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            List<URI> sessionURIs = getIds();
            for (URI sessionURI : sessionURIs) {
                BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, sessionURI);
                List<URI> sessionSnapshotURIs = _sessionSnapshotMap.get(sessionURI);
                BlockObject sourceObj = BlockObject.fetch(dbClient, snapSession.getParent().getURI());

                // Record the results.
                recordBlockSnapshotSessionOperation(dbClient, OperationTypeEnum.CREATE_SNAPSHOT_SESSION, status,
                        getDescriptionOfResults(status, sourceObj, snapSession), snapSession, sourceObj);

                // Update the status map of the snapshot session.
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockSnapshotSession.class, sessionURI, coded);

                        // Mark ViPR object inactive on error.
                        snapSession.setInactive(true);
                        dbClient.persistObject(snapSession);
                        for (URI sessionSnapshotURI : sessionSnapshotURIs) {
                            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, sessionSnapshotURI);
                            snapshot.setInactive(true);
                            dbClient.persistObject(snapshot);
                        }
                        break;
                    default:
                        setReadyOnDataObject(dbClient, BlockSnapshotSession.class, sessionURI);
                }

            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done Snapshot Session Create {}, with Status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status. Snapshot Session Create {}, for task " + getOpId(), getId(), e);
        }
    }

    /**
     * Records a ViPR event and creates an audit log entry to capture the results of the
     * snapshot session creation operation.
     * 
     * @param dbClient A reference to a database client.
     * @param opType The snapshot session operation type.
     * @param status The result of the request.
     * @param description A description of the results.
     * @param params Parameters to be recorded for the operation.
     */
    public void recordBlockSnapshotSessionOperation(DbClient dbClient, OperationTypeEnum opType, Operation.Status status,
            String description, Object... params) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String eventType = opType.getEvType(opStatus);
            s_logger.info("opType: {} detail: {}", opType.toString(), eventType + ':' + description);
            BlockSnapshotSession snapSession = (BlockSnapshotSession) params[0];
            String snapSessionId = snapSession.getId().toString();
            String snapSessionLabel = snapSession.getLabel();
            BlockObject sourceObj = (BlockObject) params[1];
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
                default:
                    s_logger.error("Unrecognized block snapshot sesion operation type");
            }
        } catch (Exception e) {
            s_logger.error("Failed to record block snapshot session operation {}, err: ", opType.toString(), e);
        }
    }

    /**
     * Records a ViPR event for a the snapshot session operation.
     * 
     * @param dbClient A reference to a database client.
     * @param snapSession A reference to the snap shot session.
     * @param evtType The event type.
     * @param status The results of the request.
     * @param description A description of the results.
     */
    public void recordBlockSnapshotSessionEvent(DbClient dbClient, BlockSnapshotSession snapSession, String evtType,
            Operation.Status status,
            String description) {
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
    private String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_SESSION_CREATE_SUCCESS_MSG, snapSession.getLabel(), sourceObj.getLabel()) :
                String.format(SNAPSHOT_SESSION_CREATE_FAIL_MSG, snapSession.getLabel(), sourceObj.getLabel());
    }
}
