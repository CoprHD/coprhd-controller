/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeImage.ComputeImageStatus;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogManagerFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

@SuppressWarnings("serial")
public class ComputeImageCompleter extends TaskCompleter {

    private static final Logger log = LoggerFactory.getLogger(ComputeImageCompleter.class);

    private OperationTypeEnum opType = null;
    private String serviceType = null;

    public ComputeImageCompleter(URI id, String opId, OperationTypeEnum opType, String serviceType) {
        super(ComputeImage.class, id, opId);
        this.opType = opType;
        this.serviceType = serviceType;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("ComputeImageCompleter.complete {}", status.name());
        ComputeImage ci = dbClient.queryObject(ComputeImage.class, getId());
        AuditLogManager auditMgr = AuditLogManagerFactory.getAuditLogManager();
        if (status == Status.error) {
            if (opType == OperationTypeEnum.CREATE_COMPUTE_IMAGE) {
                boolean available = false;
                List<URI> ids = dbClient.queryByType(ComputeImageServer.class,
                        true);
                for (URI imageServerId : ids) {
                    ComputeImageServer imageServer = dbClient.queryObject(
                            ComputeImageServer.class, imageServerId);
                    if (imageServer.getComputeImages() != null
                            && imageServer.getComputeImages().contains(
                                    ci.getId().toString())) {
                        available = true;
                        break;
                    }
                }
                if (available) {
                    ci.setComputeImageStatus(ComputeImageStatus.AVAILABLE
                            .name());
                } else {
                    ci.setComputeImageStatus(ComputeImageStatus.NOT_AVAILABLE
                            .name());
                }
                ci.setLastImportStatusMessage(coded.getMessage());
                dbClient.persistObject(ci);
            }
            dbClient.error(ComputeImage.class, getId(), getOpId(), coded);
            auditMgr.recordAuditLog(null, null, serviceType, opType,
                    System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_FAILURE,
                    AuditLogManager.AUDITOP_END, ci.getId().toString(),
                    ci.getImageUrl(), ci.getComputeImageStatus());
        } else {
            if (opType == OperationTypeEnum.DELETE_COMPUTE_IMAGE) {
                dbClient.markForDeletion(ci);
            } else if (opType == OperationTypeEnum.CREATE_COMPUTE_IMAGE) {
                ci.setComputeImageStatus(ComputeImageStatus.AVAILABLE.name());
                ci.setLastImportStatusMessage("Success");
                dbClient.persistObject(ci);
            }
            dbClient.ready(ComputeImage.class, getId(), getOpId());
            auditMgr.recordAuditLog(null, null, serviceType, opType,
                    System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS,
                    AuditLogManager.AUDITOP_END, ci.getId().toString(),
                    ci.getImageUrl(), ci.getComputeImageStatus());
        }
    }
}
