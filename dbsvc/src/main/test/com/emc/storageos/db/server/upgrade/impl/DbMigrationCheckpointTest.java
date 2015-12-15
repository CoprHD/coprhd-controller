/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.db.server.upgrade.DbStepSkipUpgradeTestBase;
import com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3NewFlagsInitializer;

/**
 * test migration checkpoint
 */
public class DbMigrationCheckpointTest extends DbStepSkipUpgradeTestBase {
    private static final Logger log = LoggerFactory.getLogger(DbMigrationCheckpointTest.class);

    private String getCheckpoint(String version) {
        CoordinatorClient coordinator = getCoordinator();
        Configuration config = coordinator.queryConfiguration( coordinator.getSiteId(),
                coordinator.getVersionedDbConfigPath(Constants.DBSVC_NAME, version), Constants.GLOBAL_ID);
        Assert.assertNotNull(config);
        return config.getConfig(DbConfigConstants.MIGRATION_CHECKPOINT);
    }

    /**
     * reset migration status
     * 
     * @param version
     */
    private void resetMigrationStatus(String version) {
        CoordinatorClient coordinator = getCoordinator();
        Configuration config = coordinator.queryConfiguration(coordinator.getSiteId(),
                coordinator.getVersionedDbConfigPath(Constants.DBSVC_NAME, version), Constants.GLOBAL_ID);
        Assert.assertNotNull(config);
        log.info("setMigrationStatus: target version \"{}\" status {}",
                version, MigrationStatus.RUNNING);
        config.setConfig(Constants.MIGRATION_STATUS, MigrationStatus.RUNNING.name());
        coordinator.persistServiceConfiguration(coordinator.getSiteId(), config);
    }

    /**
     * Verify if migration checkpoint information is saved to ZK
     * 
     * @param version target schema version for migration
     */
    private void verifyMigrationFailed(String version) {
        CoordinatorClient coordinator = getCoordinator();
        Assert.assertEquals(MigrationStatus.FAILED, coordinator.getMigrationStatus());

        String checkpoint = getCheckpoint(version);
        log.info("Current migration checkpoint: {}", checkpoint);
        Assert.assertNotNull(checkpoint);
        String failedCallbackName = com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3NewFlagsInitializer.class.getSimpleName();
        Assert.assertNotSame(failedCallbackName, checkpoint);
    }

    /**
     * Verify if migration checkpoint information is saved to ZK
     * 
     * @param version target schema version for migration
     */
    private void verifyMigrationInterrupted(String version) {
        CoordinatorClient coordinator = getCoordinator();
        Assert.assertEquals(MigrationStatus.RUNNING, coordinator.getMigrationStatus());

        String checkpoint = getCheckpoint(version);
        log.info("Current migration checkpoint: {}", checkpoint);
        Assert.assertNotNull(checkpoint);
        String failedCallbackName = com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3NewFlagsInitializer.class.getSimpleName();
        Assert.assertNotSame(failedCallbackName, checkpoint);
    }

    /**
     * Verify if migration checkpoint information is cleared after migration done
     * 
     * @param version target schema version for migration
     */
    private void verifyMigrationDone(String version) {
        CoordinatorClient coordinator = getCoordinator();
        Assert.assertEquals(MigrationStatus.DONE, coordinator.getMigrationStatus());

        String checkpoint = getCheckpoint(version);
        Assert.assertNull(checkpoint);
    }

    /**
     * Simulate migration failure by injecting a fault. Test if migration could restart from checkpoint
     * 
     * @throws Exception
     */
    @Test
    public void runMigrationCheckpointTest() throws Exception {
        final String targetVersion = secondUpgradeVersion;
        String targetDoPackage = "com.emc.storageos.db.server.upgrade.util.models.updated2";

        // prepare data for version 1.2
        stopAll();
        setupDB(initalVersion, "com.emc.storageos.db.server.upgrade.util.models.old");
        prepareData1();
        prepareData2();
        stopAll();

        // fatal exception -- make sure we are moving into failed state
        Resource3NewFlagsInitializer.injectFatalFault = true;
        setupDB(targetVersion, targetDoPackage);
        verifyMigrationFailed(targetVersion);
        stopAll();

        // reset migration state - for next test
        resetMigrationStatus(targetVersion);

        // retryable exception -- should get automatically retried
        Resource3NewFlagsInitializer.injectFault = true;
        Resource3NewFlagsInitializer.injectFatalFault = false;
        Resource3NewFlagsInitializer.faultInjected = false;
        ScheduledExecutorService exe = Executors.newScheduledThreadPool(1);
        exe.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                if (Resource3NewFlagsInitializer.faultInjected) {
                    verifyMigrationInterrupted(targetVersion);
                    log.info("resetting fault injection");
                    Resource3NewFlagsInitializer.injectFault = false;
                }
            }
        }, 10, 10, TimeUnit.SECONDS);

        setupDB(targetVersion, targetDoPackage);
        verifyAll();
        verifyMigrationDone(targetVersion);
        stopAll();
        exe.shutdownNow();
    }
}
