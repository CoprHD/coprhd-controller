/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model;

import org.apache.log4j.Logger;
import org.junit.BeforeClass;

import com.emc.sa.model.dao.BourneDbClient;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.DbClientTest.DbClientImplUnitTester;

public class DBClientTestBase {
    
    private static final Logger _logger = Logger.getLogger(DBClientTestBase.class);

    private static EncryptionProviderImpl ENCRYPTION_PROVIDER = new EncryptionProviderImpl();
    
    protected static final String DEFAULT_TENANT = "defaultTenant";
    
    private static ModelClient MODEL_CLIENT;
    
    protected static synchronized ModelClient getModelClient() {
        if (MODEL_CLIENT == null) {
            MODEL_CLIENT = createModelClient();
        }
        return MODEL_CLIENT;
    }
    
    @BeforeClass
    public static void cleanupDb() {
        _logger.info("DBClientTestBase.cleanupDb");
        
        ModelClient modelClient = getModelClient();

        modelClient.delete(modelClient.approvalRequests().findAll(DEFAULT_TENANT));
        modelClient.delete(modelClient.catalogCategories().findAll(DEFAULT_TENANT));
        modelClient.delete(modelClient.catalogServices().findAll());
        modelClient.delete(modelClient.catalogServiceFields().findAll());
        modelClient.delete(modelClient.datacenters().findAll());
        modelClient.delete(modelClient.esxHosts().findAll(DEFAULT_TENANT));
        modelClient.delete(modelClient.executionLogs().findAll());
        modelClient.delete(modelClient.executionStates().findAll());
        modelClient.delete(modelClient.executionTaskLogs().findAll());
        modelClient.delete(modelClient.orders().findAll(DEFAULT_TENANT));
        modelClient.delete(modelClient.vcenters().findAll(DEFAULT_TENANT));
        modelClient.delete(modelClient.virtualMachines().findAll());
    }    
    
    /**
     * Create DbClient to embedded DB
     *
     * @return
     */
    protected static DbClient createDbClient() {
        ENCRYPTION_PROVIDER.setCoordinator(ModelTestSuite.getCoordinator());
        
        DbClientContext localCtx = new DbClientContext();
        localCtx.setClusterName("Test");
        localCtx.setKeyspaceName("Test");

        DbClientImpl dbClient = new DbClientImpl();
        dbClient.setCoordinatorClient(ModelTestSuite.getCoordinator());
        dbClient.setEncryptionProvider(ENCRYPTION_PROVIDER);
        dbClient.setBypassMigrationLock(true);
        dbClient.setDbVersionInfo(ModelTestSuite.getDbVersionInfo());
        dbClient.setLocalContext(localCtx);
        
        VdcUtil.setDbClient(dbClient);
        dbClient.start();

        return dbClient;
    }    
    
    protected static ModelClient createModelClient() {
        BourneDbClient bourneDbClient = new BourneDbClient();
        bourneDbClient.setDbClient(createDbClient());
        bourneDbClient.init();

        ModelClient modelClient = new ModelClient(bourneDbClient);
        return modelClient;
    }
    
}
