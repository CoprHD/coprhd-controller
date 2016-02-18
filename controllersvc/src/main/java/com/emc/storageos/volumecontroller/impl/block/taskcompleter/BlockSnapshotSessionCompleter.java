/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.util.VPlexUtil;
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

    protected List<URI> _snapshotURIs;

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

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        updateConsistencyGroupTasks(dbClient, status, coded);
        if (isNotifyWorkflow()) {
            updateWorkflowStatus(status, coded);
        }
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
    protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return null;
    }

    /**
     * Returns all appropriate sources for a given BlockSnapshotSession. That is, VPLEX volumes if
     * they exist or native backend volumes.
     *
     * For volumes that are not in any consistency group, the returned list shall contain only one element.
     *
     * @param snapSession   BlockSnapshotSession.
     * @param dbClient      Database client.
     * @return              List of one or more BlockObject instances.
     */
    protected List<BlockObject> getAllSources(BlockSnapshotSession snapSession, DbClient dbClient) {
        if (snapSession.hasConsistencyGroup()) {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getConsistencyGroup());
            // return only those volumes belonging to session's RG
            List<BlockObject> cgSources = BlockConsistencyGroupUtils.getAllSources(cg, dbClient);
            List<BlockObject> cgSourcesInRG = new ArrayList<BlockObject>();
            String rgName = snapSession.getReplicationGroupInstance();
            if (NullColumnValueGetter.isNotNullValue(rgName)) {
                for (BlockObject bo : cgSources) {
                    String boRGName = bo.getReplicationGroupInstance();
                    if (bo instanceof Volume && ((Volume) bo).isVPlexVolume(dbClient)) {
                        Volume srcBEVolume = VPlexUtil.getVPLEXBackendVolume((Volume) bo, true, dbClient);
                        boRGName = srcBEVolume.getReplicationGroupInstance();
                    }
                    if (rgName.equals(boRGName)) {
                        cgSourcesInRG.add(bo);
                    }
                }
            }
            return cgSourcesInRG;
        }
        return Lists.newArrayList(getSource(snapSession, dbClient));
    }

    /**
     * Returns the appropriate source for a BlockSnapshotSession.  That is, a VPLEX volume if
     * one exists or a native backend volume.
     *
     * @param snapshotSession   BlockSnapshotSession with a valid parent (no consistency group).
     * @param dbClient          Database client.
     * @return                  BlockObject representing the snapshot session source.
     */
    protected BlockObject getSource(BlockSnapshotSession snapshotSession, DbClient dbClient) {
        URI parentURI = snapshotSession.getParent().getURI();
        if (URIUtil.isNull(parentURI)) {
            throw new IllegalArgumentException("Expected a BlockSnapshotSession with a non-null parent");
        }

        BlockObject object = BlockObject.fetch(dbClient, parentURI);
        if (Volume.checkForVplexBackEndVolume(dbClient, (Volume) object)) {
            return Volume.fetchVplexVolume(dbClient, (Volume) object);
        }

        return object;
    }

    /**
     * For non-CG snapshots, the returned list contains only the passed in snapshot.
     * For CG snapshots, the returned list contains all snapshot members of the passed in snapshot's
     * replication group.
     *
     * @param snapshot
     * @param dbClient
     * @return
     */
    public List<BlockSnapshot> getRelatedSnapshots(BlockSnapshot snapshot, DbClient dbClient) {
        List<BlockSnapshot> result = new ArrayList<>();
        if (snapshot.hasConsistencyGroup()) {
            result.addAll(ControllerUtils.getSnapshotsPartOfReplicationGroup(
                    snapshot, dbClient));
        } else {
            result.add(snapshot);
        }
        return result;
    }

    /**
     * When this completer is handling multiple snapshots from different replication groups,
     * this method gathers all related snapshots for each snapshot and returns them in a list.
     *
     * @param dbClient  Database client.
     * @return          List of all snapshots, including each of their related snapshots.
     */
    public List<BlockSnapshot> getAllSnapshots(DbClient dbClient) {
        List<BlockSnapshot> result = new ArrayList<>();
        Iterator<BlockSnapshot> iterator = dbClient.queryIterativeObjects(BlockSnapshot.class, _snapshotURIs);
        while (iterator.hasNext()) {
            BlockSnapshot snapshot = iterator.next();
            result.addAll(getRelatedSnapshots(snapshot, dbClient));
        }
        return result;
    }
}
