/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
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

import static java.util.Arrays.asList;

public class MirrorFileTaskCompleter extends TaskCompleter {
    /**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(MirrorFileTaskCompleter.class);

	public MirrorFileTaskCompleter(Class clazz, List<URI> ids, String opId) {
		super(clazz, ids, opId);
	}
    private static final String EVENT_SERVICE_TYPE = "file";
    private static final String EVENT_SERVICE_SOURCE = "FileController";

	public MirrorFileTaskCompleter(Class clazz, URI id, String opId) {
		super(clazz, id, opId);
	}
	
	public MirrorFileTaskCompleter(URI sourceURI, URI targetURI, String opId) {
        super(FileShare.class, asList(sourceURI, targetURI), opId);
    }

    protected List<FileShare> fileShareCache;

    private DbClient dbClient;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }



    protected List<FileShare> fileshareCache;

	
	@Override
	protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
			throws DeviceControllerException {
        setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
        updateFileSystemStatus(dbClient, status);
    }

    protected void updateFileSystemStatus(DbClient dbClient, Operation.Status status) {
        try {
            if (Operation.Status.ready.equals(status)) {
                List<FileShare> fileshares = dbClient.queryObject(FileShare.class, getIds());
                for (FileShare fileshare : fileshares) {
                }
                dbClient.updateObject(fileshares);
                _logger.info("Updated Mirror link status for fileshares: {}", getIds());
            }
        } catch (Exception e) {
            _logger.info("Not updating file Mirror link status for fileshares: {}", getIds(), e);
        }
    }


    /**
     * Record  FileShare related event and audit
     *
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param extParam parameters array from which we could generate detail audit message
     */
    public void recordMirrorOperation(DbClient dbClient, OperationTypeEnum opType, Operation.Status status, Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String evType;
            evType = opType.getEvType(opStatus);
            String evDesc = opType.getDescription();
            String opStage = AuditLogManager.AUDITOP_END;
            _logger.info("opType: {} detail: {}", opType.toString(), evType.toString() + ':' + evDesc);

            recordBourneMirrorEvent(dbClient, getId(), evType, status, evDesc);

            String id = (String) extParam[0];
            switch (opType) {
//                case :
//                    auditFile(dbClient, opType, opStatus, opStage, extParam);
//                    break;
//                case "suspend":
//                    auditFile(dbClient, opType, opStatus, opStage, extParam);
//                    break;
//                case "detach":
//                    auditFile(dbClient, opType, opStatus, opStage, extParam);
//                    break;
//                case "pause":
//                    auditFile(dbClient, opType, opStatus, opStage, extParam);
//                    break;
//                case RESUME_FILE_MIRROR:
//                    auditFile(dbClient, opType, opStatus, opStage, extParam);
//                    break;
//                case FAILOVER_FILE_MIRROR:
//                    auditFile(dbClient, opType, opStatus, opStage, extParam);
//                    break;
//                case STOP_FILE_MIRROR:
//                    auditFile(dbClient, opType, opStatus, opStage, extParam);
//                    break;
                default:
                    _logger.error("unrecognized Mirror operation type");
            }
        } catch (Exception e) {
            _logger.error("Failed to record Mirror operation {}, err: {}", opType.toString(), e);
        }
    }

    /**
     * Record audit log for file service
     *
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param description Description for the AuditLog
     * @param descparams Description paramters
     */
    public static void auditFile(DbClient dbClient, OperationTypeEnum auditType,
                                 boolean operationalStatus,
                                 String description,
                                 Object... descparams) {
        AuditLogManager auditMgr = new AuditLogManager();
        auditMgr.setDbClient(dbClient);
        auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus ? AuditLogManager.AUDITLOG_SUCCESS : AuditLogManager.AUDITLOG_FAILURE,
                description,
                descparams);
    }



    /**
     *
     * @param dbClient
     * @param evtType
     * @param status
     * @param desc
     * @throws Exception
     */
    public void recordBourneMirrorEvent(DbClient dbClient, URI fileUri,
                                        String evtType,
                                        Operation.Status status, String desc)
            throws Exception {

        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        FileShare fsObj = dbClient.queryObject(FileShare.class, fileUri);
        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(fsObj, evtType, desc,
                "", dbClient, EVENT_SERVICE_TYPE, RecordType.Event.name(), EVENT_SERVICE_SOURCE);
        try {
            eventManager.recordEvents(event);
            _logger.info("Bourne {} event recorded", evtType);
        } catch (Exception ex) {
            _logger.error(
                    "Failed to record event. Event description: {}. Error: ",
                    evtType, ex);
        }
    }

    protected List<FileShare> getFileShares() {
        if (fileShareCache == null) {
            fileShareCache = getDbClient().queryObject(FileShare.class, getIds());
        }
        return fileShareCache;
    }

    protected Set<String> getFileShareIds() {
        Set<String> fileShareIds = new HashSet<String>();
        for (FileShare fileshare : fileShareCache) {
            fileShareIds.add(fileshare.getNativeGuid());
        }
        return fileShareIds;
    }


    protected FileShare getTargetFileShare() {
        for (FileShare fs : getFileShares()) {
            if (!NullColumnValueGetter.isNullNamedURI(fs.getParentFileShare()) && !fs.getParentFileShare().getURI().toString().equalsIgnoreCase("null")) {
                return fs;
            }
        }
        throw new IllegalStateException("Expected a target FileShare with an non-null Replication parent");
    }

    protected FileShare getSourceFileShare() {
        for (FileShare fs : getFileShares()) {
            if (fs.getMirrorfsTargets() != null) {
                return fs;
            }
        }
        throw new IllegalStateException("Expected a source FileShare with an non-null Replication parent");
    }



}
