/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.ProvisioningJobStatus;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogManagerFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

@SuppressWarnings("serial")
public class ComputeHostCompleter extends TaskCompleter {

    private OperationTypeEnum opType = null;
    private String serviceType = null;

    public ComputeHostCompleter(URI id, String opId, OperationTypeEnum opType,
            String serviceType) {
        super(Host.class, id, opId);
        this.opType = opType;
        this.serviceType = serviceType;

    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        Host host = dbClient.queryObject(Host.class, getId());
        ComputeElement ce = null;
        if (host != null) {
            ce = dbClient.queryObject(ComputeElement.class, host.getComputeElement());
        }

        AuditLogManager auditMgr = AuditLogManagerFactory.getAuditLogManager();

        switch (status) {
            case ready:
                if (host != null) {
                    if (ce != null) {
                        host.setUuid(ce.getUuid());
                    }
                    host.setProvisioningStatus(ProvisioningJobStatus.COMPLETE.toString());
                    dbClient.persistObject(host);

                    dbClient.ready(Host.class, getId(), getOpId());

                    if (ce != null) {
                        auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(),
                                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END, host.getId().toString(),
                                ce.getAvailable(), ce.getUuid(), ce.getDn());
                    }
                    else {
                        auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(),
                                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END, host.getId().toString());
                    }
                }
                break;
            case error:
                if (host != null) {
                    host.setProvisioningStatus(ProvisioningJobStatus.ERROR.toString());
                    dbClient.persistObject(host);
                    dbClient.error(Host.class, getId(), getOpId(), coded);

                    /**
                     * Looks like the provisioning of the Host was unsuccessful...
                     * Set the ComputeElement to available (assuming that the
                     * rollback method executed properly)
                     */
                    if (ce != null) {
                        ce.setAvailable(true);
                        dbClient.persistObject(ce);
                        auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(),
                                AuditLogManager.AUDITLOG_FAILURE, AuditLogManager.AUDITOP_END, host.getId().toString(),
                                ce.getAvailable(), ce.getUuid(), ce.getDn());
                    }
                    else {
                        auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(),
                                AuditLogManager.AUDITLOG_FAILURE, AuditLogManager.AUDITOP_END, host.getId().toString());
                    }
                }
                break;
            default:
                throw new DeviceControllerException(new IllegalStateException(
                        "Terminal state processing called, when operation was in fact not in a terminal state!"));
        }
    }
}
