/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.audit;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;

public class AuditLogManagerFactory {
    private static DbClient dbClient;
    private static CoordinatorClient coordinator;
    
    /**
     * get the instance of AuditLogManager
     * @return
     */
    public static AuditLogManager getAuditLogManager() {
        AuditLogManager auditLogManager = new AuditLogManager();
        auditLogManager.setCoordinator(coordinator);
        auditLogManager.setDbClient(dbClient);
        
        return auditLogManager;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        AuditLogManagerFactory.dbClient = dbClient;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        AuditLogManagerFactory.coordinator = coordinator;
    }
}
