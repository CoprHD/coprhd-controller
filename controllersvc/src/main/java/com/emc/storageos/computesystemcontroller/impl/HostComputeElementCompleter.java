/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeElementHBA;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.ProvisioningJobStatus;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogManagerFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

@SuppressWarnings("serial")
public class HostComputeElementCompleter extends TaskCompleter {

    /**
     * logger instance
     */
    private static final Logger log = LoggerFactory.getLogger(HostComputeElementCompleter.class);

    private OperationTypeEnum opType;
    private String serviceType;

    private URI computeElementId;
    private URI computeVPoolId;

    public HostComputeElementCompleter(URI id, String opId, OperationTypeEnum opType, String serviceType) {
        super(Host.class, id, opId);
        this.opType = opType;
        this.serviceType = serviceType;
    }

    public HostComputeElementCompleter(URI hostId, String taskId, OperationTypeEnum opType,
            String eventServiceType, URI computeElementId, URI computeVPoolId) {
        this(hostId, taskId, opType, eventServiceType);
        this.computeElementId = computeElementId;
        this.computeVPoolId = computeVPoolId;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        Host host = dbClient.queryObject(Host.class, getId());
        try {
            AuditLogManager auditMgr = AuditLogManagerFactory.getAuditLogManager();
            log.info("HostComputeElementCompleter.complete {}", status.name());
            switch (status) {
            case ready:
                if (opType == OperationTypeEnum.RELEASE_HOST_COMPUTE_ELEMENT) {
                    log.info("HostComputeElementCompleter.complete status ready and is opType {}", opType);
                    if (host != null) {
                        if (!NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                            ComputeElement ce = dbClient.queryObject(ComputeElement.class, host.getComputeElement());
                            if (ce.getAvailable()) {
                                host.setComputeElement(NullColumnValueGetter.getNullURI());
                            }
                        }
                        URIQueryResultList ceHBAUriList = new URIQueryResultList();

                        dbClient.queryByConstraint(
                                ContainmentConstraint.Factory.getHostComputeElemetHBAsConstraint(host.getId()),
                                ceHBAUriList);

                        List<ComputeElementHBA> ceHBAs = dbClient.queryObject(ComputeElementHBA.class, ceHBAUriList);
                        for (ComputeElementHBA computeElementHBA : ceHBAs) {
                            computeElementHBA.setComputeElement(NullColumnValueGetter.getNullURI());
                            dbClient.updateObject(computeElementHBA);
                        }
                    }
                } else if (opType == OperationTypeEnum.ASSOCIATE_HOST_COMPUTE_ELEMENT) {
                    log.info("HostComputeElementCompleter.complete status ready and is opType {}", opType);
                    if (host != null) {
                        if (!NullColumnValueGetter.isNullURI(host.getComputeElement())
                                && host.getComputeElement().equals(computeElementId)) {
                            ComputeElement ce = dbClient.queryObject(ComputeElement.class, host.getComputeElement());
                            if (ce.getAvailable()) {
                                ce.setAvailable(false);
                                dbClient.updateObject(ce);
                            }
                            host.setComputeVirtualPoolId(computeVPoolId);

                            URIQueryResultList ceHBAUriList = new URIQueryResultList();

                            dbClient.queryByConstraint(
                                    ContainmentConstraint.Factory.getHostComputeElemetHBAsConstraint(host.getId()),
                                    ceHBAUriList);

                            List<ComputeElementHBA> ceHBAs = dbClient.queryObject(ComputeElementHBA.class,
                                    ceHBAUriList);
                            for (ComputeElementHBA computeElementHBA : ceHBAs) {
                                if (!NullColumnValueGetter.isNullURI(computeElementHBA.getComputeElement())
                                        && !host.getComputeElement().equals(computeElementHBA.getComputeElement())) {
                                    computeElementHBA.setComputeElement(host.getComputeElement());
                                    dbClient.updateObject(computeElementHBA);
                                }
                            }
                        }
                    }
                } else {
                    host.setProvisioningStatus(ProvisioningJobStatus.ERROR.toString());
                    dbClient.updateObject(host);
                    dbClient.error(Host.class, getId(), getOpId(), coded);

                    auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(),
                            AuditLogManager.AUDITLOG_FAILURE, AuditLogManager.AUDITOP_END, host.getId().toString());
                    log.error("Unknown operation type {}", opType);
                    throw new RuntimeException("Unknown operation type " + opType );
                }

                host.setProvisioningStatus(ProvisioningJobStatus.COMPLETE.toString());
                dbClient.updateObject(host);
                dbClient.ready(Host.class, getId(), getOpId());

                auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(),
                        AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END, host.getId().toString());
                break;
            case error:
                if (host != null) {
                    host.setProvisioningStatus(ProvisioningJobStatus.ERROR.toString());
                    dbClient.updateObject(host);
                    dbClient.error(Host.class, getId(), getOpId(), coded);

                    auditMgr.recordAuditLog(null, null, serviceType, opType, System.currentTimeMillis(),
                            AuditLogManager.AUDITLOG_FAILURE, AuditLogManager.AUDITOP_END, host.getId().toString());
                }
                break;
            default:
                dbClient.ready(Host.class, getId(), getOpId());
                break;
            }
        } finally {
            //release cs locks.
        }
    }
}
