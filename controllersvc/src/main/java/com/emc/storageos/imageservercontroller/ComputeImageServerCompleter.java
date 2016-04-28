package com.emc.storageos.imageservercontroller;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeImageServer.ComputeImageServerStatus;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogManagerFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Completer class for handling ComputeImageServer completion tasks
 *
 */
@SuppressWarnings("serial")
public class ComputeImageServerCompleter extends TaskCompleter{

    private static final Logger log = LoggerFactory.getLogger(ComputeImageServerCompleter.class);

    private OperationTypeEnum opType = null;
    private String serviceType = null;

    public ComputeImageServerCompleter(URI id, String opId, OperationTypeEnum opType, String serviceType) {
        super(ComputeImageServer.class, id, opId);
        this.opType = opType;
        this.serviceType = serviceType;
    }

    /**
     * Method to be invoked on job execution completion
     * @param dbClient {@link DBClient} instance
     * @param Status {@link Status} of job
     * @param coded {@link ServiceCoded} instance
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {

        log.info("ComputeImageServerCompleter.complete {}", status.name());
        ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, getId());
        AuditLogManager auditMgr = AuditLogManagerFactory.getAuditLogManager();
        if (status == Status.error) {
            dbClient.error(ComputeImageServer.class, getId(), getOpId(), coded);
            auditMgr.recordAuditLog(null, null, serviceType,
                    opType, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_FAILURE, AuditLogManager.AUDITOP_END,
                    imageServer.getId().toString(), imageServer.getComputeImageServerStatus());
        } else {
            if (opType == OperationTypeEnum.DELETE_COMPUTE_IMAGESERVER) {
                dbClient.markForDeletion(imageServer);
            } else if (opType == OperationTypeEnum.IMAGESERVER_VERIFY_IMPORT_IMAGES) {
                imageServer.setComputeImageServerStatus(ComputeImageServerStatus.AVAILABLE.name());
                dbClient.persistObject(imageServer);
            }
            dbClient.ready(ComputeImageServer.class, getId(), getOpId());
            auditMgr.recordAuditLog(null, null, serviceType,
                    opType, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    imageServer.getId().toString(), imageServer.getComputeImageServerStatus());
        }

    }

}
