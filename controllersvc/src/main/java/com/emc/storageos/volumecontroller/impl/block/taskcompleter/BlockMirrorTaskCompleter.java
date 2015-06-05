/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
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
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

public class BlockMirrorTaskCompleter extends TaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockMirrorTaskCompleter.class);

    private URI _mirrorURI;

    public BlockMirrorTaskCompleter(Class clazz, URI mirror, String opId) {
        super(clazz, mirror, opId);
        _mirrorURI = mirror;
    }

    public BlockMirrorTaskCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
    }

    public void recordBourneBlockMirrorEvent(DbClient dbClient, URI mirrorUri, String evtType,
            Operation.Status status, String desc) throws Exception {
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        BlockMirror mirror = dbClient.queryObject(BlockMirror.class, mirrorUri);
        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(mirror,
                evtType, desc, "", dbClient, ControllerUtils.BLOCK_EVENT_SERVICE,
                RecordType.Event.name(), ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            eventManager.recordEvents(event);
            _log.info("Bourne {} event recorded", evtType);
        } catch (Throwable t) {
            _log.error("Failed to record event. Event description: {}. Error: ", evtType, t);
        }
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        super.setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
    }

    public URI getMirrorURI() {
        return _mirrorURI;
    }

    /**
     * Record block mirror related event and audit
     *
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param evDesc event description
     * @param extParam parameters array from which we could generate detail
     *            audit message
     */
    public void recordBlockMirrorOperation(DbClient dbClient, OperationTypeEnum opType,
            Operation.Status status, String evDesc, Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String evType = opType.getEvType(opStatus);
            String opStage = AuditLogManager.AUDITOP_END;
            _log.info("opType: {} detail: {}", opType.toString(), evType.toString() + ':' + evDesc);

            BlockMirror mirror = (BlockMirror) extParam[0];
            recordBourneBlockMirrorEvent(dbClient, mirror.getId(), evType, status, evDesc);

            Volume volume = (Volume) extParam[1];
            switch (opType) {
            case CREATE_VOLUME_MIRROR:
                if (opStatus) {
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, mirror.getId()
                            .toString(), mirror.getLabel(), volume.getId().toString());
                } else {
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage,
                            mirror.getLabel(), volume.getId().toString());
                }
                break;
            case DEACTIVATE_VOLUME_MIRROR:
            case DELETE_VOLUME_MIRROR:
            case DETACH_VOLUME_MIRROR:
            case FRACTURE_VOLUME_MIRROR:
                AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, mirror.getId()
                        .toString(), mirror.getLabel(), volume.getId().toString());
                break;
            default:
                _log.error("unrecognized volume mirror operation type");
            }
        } catch (Exception e) {
            _log.error("Failed to record volume mirror operation {}, err: ", opType.toString(), e);
        }
    }

}
