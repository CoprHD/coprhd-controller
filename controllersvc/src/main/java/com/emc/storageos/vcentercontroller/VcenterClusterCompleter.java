/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogManagerFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

@SuppressWarnings("serial")
public class VcenterClusterCompleter extends TaskCompleter {

    private static final Logger log = LoggerFactory.getLogger(VcenterClusterCompleter.class);

    private OperationTypeEnum opType = null;
    private String serviceType = null;

    public VcenterClusterCompleter(URI id, String opId, OperationTypeEnum opType, String serviceType) {
        super(VcenterDataCenter.class, id, opId);
        this.opType = opType;
        this.serviceType = serviceType;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("VcenterClusterCompleter.complete {}", status.name());
        VcenterDataCenter vcenterDataCenter = dbClient.queryObject(VcenterDataCenter.class, getId());
        AuditLogManager auditMgr = AuditLogManagerFactory.getAuditLogManager();
        if (status == Status.error) {
            log.info("Error in state " + status);
            dbClient.error(VcenterDataCenter.class, getId(), getOpId(), coded);
            auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(), AuditLogManager.AUDITLOG_FAILURE,
                    AuditLogManager.AUDITOP_END);
        } else {
            dbClient.ready(VcenterDataCenter.class, getId(), getOpId());
            auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(), AuditLogManager.AUDITLOG_SUCCESS,
                    AuditLogManager.AUDITOP_END);
        }
    }
}
