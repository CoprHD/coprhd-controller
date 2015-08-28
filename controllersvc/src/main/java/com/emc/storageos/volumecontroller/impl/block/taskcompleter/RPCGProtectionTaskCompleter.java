/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

@SuppressWarnings("serial")
public class RPCGProtectionTaskCompleter extends RPCGTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(RPCGCreateCompleter.class);
    private OperationTypeEnum opTypeEnum = null;

    public RPCGProtectionTaskCompleter(URI uri, String task) {
        super(Volume.class, uri, task);
    }

    public void setOperationTypeEnum(OperationTypeEnum opTypeEnum) {
        this.opTypeEnum = opTypeEnum;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            // Tell the workflow we're done.
            _log.info("protection task completer: done");
            _log.info(String.format("Done protection task - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            // Record audit event(s)
            if (opTypeEnum == null) {
                setOperationTypeEnum(OperationTypeEnum.PERFORM_PROTECTION_OPERATION);
            }

            recordRPOperation(dbClient, opTypeEnum, status, getId().toString());

            // Tell the individual objects we're done.
            switch (status) {
                case error:
                    dbClient.error(Volume.class, getId(), getOpId(), coded);
                    break;
                default:
                    dbClient.ready(Volume.class, getId(), getOpId());
            }
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for protection task - Volume ID: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        }
    }

    /**
     * Record block volume related event and audit
     * 
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param extParam parameters array from which we could generate detail audit message
     */
    private void recordRPOperation(DbClient dbClient, OperationTypeEnum opType, Operation.Status status, Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String evType;
            evType = opType.getEvType(opStatus);
            String evDesc = opType.getDescription();
            String opStage = AuditLogManager.AUDITOP_END;
            _log.info("opType: {} detail: {}", opType.toString(), evType.toString() + ':' + evDesc);

            recordBourneRPEvent(dbClient, getId(), evType, status, evDesc);

            switch (opType) {
                case START_RP_LINK:
                case STOP_RP_LINK:
                case PAUSE_RP_LINK:
                case RESUME_RP_LINK:
                case FAILOVER_RP_LINK:
                case SWAP_RP_VOLUME:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                default:
                    _log.error("Unrecognized RP operation type");
            }
        } catch (Exception e) {
            _log.error("Failed to record RP operation {}, err: {}", opType.toString(), e);
        }
    }

    /**
     * Record RP event
     * 
     * @param dbClient db client
     * @param volumeUri volume operation performed on
     * @param evtType event type
     * @param status status of operation
     * @param desc description
     * @throws Exception
     */
    private void recordBourneRPEvent(DbClient dbClient, URI volumeUri,
            String evtType,
            Operation.Status status, String desc) {
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        Volume volObj = dbClient.queryObject(Volume.class, volumeUri);
        RecordableBourneEvent event = ControllerUtils
                .convertToRecordableBourneEvent(volObj, evtType,
                        desc, "", dbClient,
                        ControllerUtils.BLOCK_EVENT_SERVICE,
                        RecordType.Event.name(),
                        ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            eventManager.recordEvents(event);
            _log.info("Bourne {} event recorded", evtType);
        } catch (Exception e) {
            _log.error(
                    "Failed to record event. Event description: {}. Error: ",
                    evtType, e);
        }
    }
}
