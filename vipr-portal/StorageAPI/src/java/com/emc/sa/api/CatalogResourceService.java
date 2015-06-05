/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.api;

import com.emc.storageos.api.service.impl.resource.ResourceService;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;

public abstract class CatalogResourceService extends ResourceService {

    public CatalogResourceService() {
        
    }

    protected void auditOpSuccess(OperationTypeEnum opType, Object... params) {
        auditOp(opType, true, null, params);
    }

    protected void auditOpFailure(OperationTypeEnum opType, Object... params) {
        auditOp(opType, false, null, params);
    }

    protected void auditOpBeginSuccess(OperationTypeEnum opType, Object... params) {
        auditOp(opType, true, AuditLogManager.AUDITOP_BEGIN, params);
    }

    protected void auditOpEndSuccess(OperationTypeEnum opType, Object... params) {
        auditOp(opType, true, AuditLogManager.AUDITOP_END, params);
    }

    protected void auditOpBeginFailure(OperationTypeEnum opType, Object... params) {
        auditOp(opType, false, AuditLogManager.AUDITOP_BEGIN, params);
    }

    protected void auditOpEndFailure(OperationTypeEnum opType, Object... params) {
        auditOp(opType, false, AuditLogManager.AUDITOP_END, params);
    }    
    
}
