/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

public abstract class BlockSnapshotTaskCompleter extends TaskLockingCompleter {
    private static final Logger _logger = LoggerFactory.getLogger(BlockSnapshotTaskCompleter.class);

    public BlockSnapshotTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public BlockSnapshotTaskCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
    }

    /**
     *
     * @param dbClient
     * @param evtType
     * @param status
     * @param desc
     * @throws Exception
     */
    public void recordBourneBlockSnapshotEvent(DbClient dbClient, URI snapUri,
            String evtType,
            Operation.Status status, String desc)
            throws Exception {
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        BlockSnapshot snapObj = dbClient.queryObject(BlockSnapshot.class, snapUri);
        RecordableBourneEvent event = ControllerUtils
                .convertToRecordableBourneEvent(snapObj, evtType,
                        desc, "", dbClient,
                        ControllerUtils.BLOCK_EVENT_SERVICE,
                        RecordType.Event.name(),
                        ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            eventManager.recordEvents(event);
            _logger.info("Bourne {} event recorded", evtType);
        } catch (Throwable th) {
            _logger.error(
                    "Failed to record event. Event description: {}. Error: ",
                    evtType, th);
        }
    }

    /**
     * Record block snapshot related event and audit
     * 
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param evDesc event description
     * @param extParam parameters array from which we could generate detail audit message
     */
    public void recordBlockSnapshotOperation(DbClient dbClient, OperationTypeEnum opType, Operation.Status status, String evDesc,
            Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String evType;
            evType = opType.getEvType(opStatus);
            String opStage = AuditLogManager.AUDITOP_END;
            _logger.info("opType: {} detail: {}", opType.toString(), evType + ':' + evDesc);

            BlockSnapshot snapshot = (BlockSnapshot) extParam[0];

            recordBourneBlockSnapshotEvent(dbClient, snapshot.getId(), evType, status, evDesc);

            Volume volume;
            switch (opType) {
                case CREATE_VOLUME_SNAPSHOT:
                    volume = (Volume) extParam[1];
                    if (opStatus) {
                        AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, snapshot.getId().toString(), snapshot.getLabel(),
                                volume.getId().toString());
                    } else {
                        AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, snapshot.getLabel(), volume.getId().toString());
                    }
                    break;
                case RESTORE_VOLUME_SNAPSHOT:
                    volume = (Volume) extParam[1];
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, snapshot.getId().toString(), volume.getId().toString());
                    break;
                case ACTIVATE_VOLUME_SNAPSHOT:
                    volume = (Volume) extParam[1];
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, snapshot.getId().toString(), snapshot.getLabel(), volume
                            .getId().toString());
                    break;
                case DEACTIVATE_VOLUME_SNAPSHOT:
                case DELETE_VOLUME_SNAPSHOT:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, snapshot.getId().toString(), snapshot.getLabel(),
                            snapshot.getParent().getName());
                    break;
                default:
                    _logger.error("unrecognized block snapshot operation type");
            }
        } catch (Exception e) {
            _logger.error("Failed to record block snapshot operation {}, err: ", opType.toString(), e);
        }
    }
}
