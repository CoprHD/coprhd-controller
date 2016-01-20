/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

/**
 * class provides methods to check dbsvc status
 */
public class DbServiceStatusChecker {
    private static final Logger log = LoggerFactory.getLogger(DbServiceStatusChecker.class);
    private static final int MAX_WAIT_TIME_IN_MIN = 10;
    private static final int WAIT_INTERVAL_IN_SEC = 1;

    private CoordinatorClient coordinator;
    private String version;
    private int clusterNodeCount;
    private DbVersionInfo dbVersionInfo;
    private String serviceName;

    /**
     * Set coordinator
     * 
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setDbVersionInfo(DbVersionInfo info) {
        dbVersionInfo = info;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Set ClusterNodeCount
     * 
     * @param clusterNodeCount
     */
    public void setClusterNodeCount(int clusterNodeCount) {
        this.clusterNodeCount = clusterNodeCount;
    }

    public int getClusterNodeCount() {
        return clusterNodeCount;
    }

    private String getDbsvcVersion(boolean isVersioned) {
        if (isVersioned) {
            if (this.version == null) {
                throw new IllegalStateException(String.format(
                        "version is not set for a versioned configuration"));
            }
            return version;
        }
        return null;
    }

    /**
     * Checks to see if any node in the cluster has entered a certain state
     */
    private boolean isAnyNodeInState(String state, boolean isVersioned) throws Exception {
        List<Configuration> configs = coordinator.queryAllConfiguration(coordinator.getSiteId(), 
                coordinator.getVersionedDbConfigPath(serviceName, getDbsvcVersion(isVersioned)));
        
        for (int i = 0; i < configs.size(); i++) {
            Configuration config = configs.get(i);
            String value = config.getConfig(state);
            if (value != null && Boolean.parseBoolean(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks to see if the cassandra cluster finished entering a certain state
     */
    private boolean isClusterStateDone(String state, boolean isVersioned, String svcName) throws Exception {
        if (clusterNodeCount == 0) {
            throw new IllegalStateException("node count not set");
        }

        List<Configuration> configs = coordinator.queryAllConfiguration(coordinator.getSiteId(), 
                coordinator.getVersionedDbConfigPath(svcName, getDbsvcVersion(isVersioned)));
        
        List<Configuration> leftoverConfig = coordinator.queryAllConfiguration(coordinator.getVersionedDbConfigPath(svcName, getDbsvcVersion(isVersioned)));
        configs.addAll(leftoverConfig);
        
        Set<String> qualifiedConfigs = new HashSet<String>();
        for (int i = 0; i < configs.size(); i++) {
            Configuration config = configs.get(i);
            String value = config.getConfig(state);
            if (value != null && Boolean.parseBoolean(value)) {
                qualifiedConfigs.add(config.getId());
            }
        }
        return (qualifiedConfigs.size() == clusterNodeCount);
    }

    private boolean isClusterStateDone(String state, boolean isVersioned) throws Exception {
        return isClusterStateDone(state, isVersioned, serviceName);
    }

    /**
     * A private helper method to wait for all cluster nodes until they all become
     * a certain state (JOINED/MIGRATION_INIT/etc)
     */
    private void waitForAllNodesToBecome(String state, boolean isVersioned, String svcName) {
        final String prefix = "Waiting for all cluster nodes to become state: " + state;
        long start = System.currentTimeMillis();
        log.info(prefix);
        while (System.currentTimeMillis() - start < MAX_WAIT_TIME_IN_MIN * 60 * 1000) {
            try {
                if (isClusterStateDone(state, isVersioned, svcName)) {
                    log.info("{} : Done", prefix);
                    return;
                }
                Thread.sleep(WAIT_INTERVAL_IN_SEC * 1000);
            } catch (InterruptedException ex) {
                log.warn("InterruptedException:{}", ex);
            } catch (FatalCoordinatorException ex) {
                log.error("fatal coodinator exception", ex);
                throw ex;
            } catch (Exception ex) {
                log.error("exception checking node status", ex);
            }
        }
        log.info("{} : Timed out", prefix);
        throw new IllegalStateException(String.format("%s : Timed out", prefix));
    }

    private void waitForAllNodesToBecome(String state, boolean isVersioned) {
        waitForAllNodesToBecome(state, isVersioned, serviceName);
    }

    public boolean isDbSchemaVersionChanged() {
        String currentVersion = coordinator.getCurrentDbSchemaVersion();
        String targetVersion = coordinator.getTargetDbSchemaVersion();
        log.info("currentVersion: {}, targetVersion {} ", currentVersion, targetVersion);
        return !(currentVersion.equals(targetVersion));
    }

    public boolean checkAllNodesJoined() throws Exception {
        return isClusterStateDone(DbConfigConstants.JOINED, false);
    }

    public boolean checkAllNodesInMigrationInit() throws Exception {
        return isClusterStateDone(DbConfigConstants.MIGRATION_INIT, true);
    }

    public boolean checkAnyNodeInitDone() throws Exception {
        return isAnyNodeInState(DbConfigConstants.INIT_DONE, true);
    }

    public void waitForAllNodesJoined() {
        waitForAllNodesToBecome(DbConfigConstants.JOINED, false);
    }

    public void waitForAllNodesMigrationInit() {
        waitForAllNodesMigrationInit(serviceName);
    }

    public void waitForAllNodesNumTokenAdjusted() {
        while (true) {
            if (this.clusterNodeCount == 1) {
                log.info("no adjust toke for single node vipr, skip");
                return;
            }

            List<Configuration> cfgs = this.coordinator.queryAllConfiguration(coordinator.getSiteId(), this.coordinator.getDbConfigPath(this.serviceName));
            int adjustedCount = 0;
            for (Configuration cfg : cfgs) {
                // Bypasses item of "global" and folders of "version", just check db configurations.
                if (cfg.getId() == null || cfg.getId().equals(Constants.GLOBAL_ID)) {
                    continue;
                }

                String numTokens = cfg.getConfig(DbConfigConstants.NUM_TOKENS_KEY);
                if (numTokens == null) {
                    log.info("Did not found {} for {}, treating as not adjusted", DbConfigConstants.NUM_TOKENS_KEY, cfg.getId());
                } else if (Integer.valueOf(numTokens).equals(DbConfigConstants.DEFUALT_NUM_TOKENS)) {
                    log.info("Found num_tokens of node {} reached target version {}", cfg.getId(), numTokens);
                    adjustedCount++;
                }
            }

            if (adjustedCount == this.clusterNodeCount) {
                return;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Interrupted", e);
            }
        }
    }

    public void waitForAllNodesMigrationInit(String svcName) {
        waitForAllNodesToBecome(DbConfigConstants.MIGRATION_INIT, true, svcName);
    }

    public boolean isMigrationDone() {
        String targetVersion = coordinator.getTargetDbSchemaVersion();
        String currentVersion = coordinator.getCurrentDbSchemaVersion();
        log.debug("current version {}, target version {}", currentVersion,
                targetVersion);
        if (currentVersion != null && currentVersion.equals(targetVersion)) {
            log.debug("migration done already");
            return true;
        }
        return false;
    }

    public void waitForMigrationDone() {
        final String prefix = "Waiting for current version to match target version ...";
        log.warn(prefix);

        String targetVersion = coordinator.getTargetDbSchemaVersion();

        while (true) {
            try {
                String currentVersion = coordinator.getCurrentDbSchemaVersion();
                log.debug("current version {}, target version {}", currentVersion,
                        targetVersion);
                if (currentVersion != null && currentVersion.equals(targetVersion)) {
                    log.info("{} : Done", prefix);
                    return;
                }
                // check every 30 seconds and there's no timeout
                Thread.sleep(WAIT_INTERVAL_IN_SEC * 30 * 1000);
            } catch (InterruptedException ex) {
                log.warn("InterruptedException:{}", ex);
            } catch (Exception ex) {
                log.error("exception checking db status", ex);
            }
        }
    }

    public void waitForAnyNodeInitDone() {
        long start = System.currentTimeMillis();
        log.info("Waiting for db schema initialization ... ");
        while (System.currentTimeMillis() - start < MAX_WAIT_TIME_IN_MIN * 60 * 1000) {
            try {
                if (checkAnyNodeInitDone()) {
                    log.info("Waiting for db schema initialization ... Done");
                    return;
                }
                Thread.sleep(WAIT_INTERVAL_IN_SEC * 1000);
            } catch (InterruptedException ex) {
                log.warn("InterruptedException:{}", ex);
            } catch (Exception ex) {
                log.error("exception checking db status", ex);
            }
        }
        log.info("Waiting for db schema initialization ... Timed out");
    }
}
