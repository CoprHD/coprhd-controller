/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;


import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogManagerFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class AuditBlockUtil {

    /**
     * Record audit log for block devices
     * 
     * @param dbClient A reference to a database client
     * @param auditType Type of AuditLog
     * @param operationalStatus success or failure
     * @param operation stage
     *            For sync operation, it should be null;
     *            For async operation, it should be "BEGIN" or "END";
     * @param descparams Description parameters
     */
    public static void auditBlock(DbClient dbClient, OperationTypeEnum auditType,
            boolean operationalStatus, String operationStage, Object... descparams) {
        auditBlock(dbClient, ControllerUtils.BLOCK_EVENT_SERVICE, auditType, operationalStatus, operationStage, descparams);
    }
    
    /**
     * Record audit log for block devices
     * 
     * @param dbClient A reference to a database client
     * @param serviceType The service type for the log message.
     * @param auditType Type of AuditLog
     * @param operationalStatus success or failure
     * @param operation stage
     *            For sync operation, it should be null;
     *            For async operation, it should be "BEGIN" or "END";
     * @param descparams Description parameters
     */
    public static void auditBlock(DbClient dbClient, String serviceType, OperationTypeEnum auditType,
            boolean operationalStatus, String operationStage, Object... descparams) {
        AuditLogManager auditMgr = AuditLogManagerFactory.getAuditLogManager();
        auditMgr.recordAuditLog(null,
                null,
                serviceType,
                auditType,
                System.currentTimeMillis(),
                operationalStatus ? AuditLogManager.AUDITLOG_SUCCESS : AuditLogManager.AUDITLOG_FAILURE,
                operationStage,
                descparams);
    }
}
