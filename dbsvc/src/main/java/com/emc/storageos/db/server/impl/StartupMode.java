/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import org.apache.cassandra.db.SystemKeyspace;
import org.apache.cassandra.service.StorageService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.vipr.model.sys.recovery.RecoveryConstants;
import com.emc.vipr.model.sys.recovery.RecoveryStatus;

/**
 * We introduce several different dbsvc startup mode to encapsulate some special handling logic
 * before/after cassandra daemon starts
 * - Normal startup. Do schema check and setup. That's for most start flow as name indicates
 * - Db reinit mode. Drop all keyspaces in db and reinitialize schema from other vdc. Currently
 * designed for geodb when joining another vdc. Set ZK flag REINIT_DB to true
 * to enter this mode
 * - Obsolete peers cleanup mode. Drop given peers info and tokens in cassandra system stable.
 * Currently designed for geodb when leaving from geo system. Set ZK flag
 * OBSOLETE_CASSANDRA_PEERS to list of node ip to enter this mode.
 * - Hibernate mode. for node recovery. Hold on Cassandra initialization until recovery status
 * is changed to syncing
 * - Geodb restore mode. for geodb restore, it picks all data from remove vdc(if there are multiple
 * vdc connected)
 */
public abstract class StartupMode {
    private static final Logger log = LoggerFactory.getLogger(StartupMode.class);

    protected Configuration config;
    protected CoordinatorClient coordinator;
    protected StartupModeType type;

    // Supported mode type
    enum StartupModeType {
        NORMAL_MODE,
        DB_REINIT_MODE,
        OBSOLETE_PEERS_CLEANUP_MODE,
        HIBERNATE_MODE,
        RESTORE_MODE
    }

    protected StartupMode(Configuration config) {
        this.config = config;
    }

    /**
     * Called before cassandra daemon starts. With cluster wide lock held
     * 
     * @throws Exception
     */
    abstract void onPreStart() throws Exception;

    /**
     * Called just after cassandra daemon starts successfully. With cluster wide lock held
     * 
     * @throws Exception
     */
    abstract void onPostStart() throws Exception;

    void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Remove given flag from current db config in zk
     * 
     * @param flagName
     */
    void removeFlag(String flagName) {
        config.removeConfig(flagName);
        coordinator.persistServiceConfiguration(coordinator.getSiteId(), config);
    }

    public String toString() {
        return String.valueOf(type);
    }

    static class NormalMode extends StartupMode {
        SchemaUtil schemaUtil;

        public NormalMode(Configuration config) {
            super(config);
            type = StartupModeType.NORMAL_MODE;
        }

        void setSchemaUtil(SchemaUtil util) {
            schemaUtil = util;
        }

        void onPreStart() throws Exception {
            // no-op
        }

        /**
         * Normal db schema setup. Create column families according to object models if
         * no schema available.
         */
        void onPostStart() throws Exception {
            log.info("Checking DB schema");
            schemaUtil.scanAndSetupDb(false);
            log.info("DB schema validated");
        }

    }

    static class DbReinitMode extends NormalMode {
        String dbDir;

        DbReinitMode(Configuration config) {
            super(config);
            type = StartupModeType.DB_REINIT_MODE;
        }

        void setDbDir(String dbDir) {
            this.dbDir = dbDir;
        }

        /**
         * Remove all db/commitlog files - including system and GeoStorageOS keyspace
         */
        void onPreStart() {
            log.info("Remove all dirs under {}", dbDir);
            File dir = new File(dbDir);
            try {
                for (File file : dir.listFiles()) {
                    if (!file.isDirectory()) {
                        continue;
                    }
                    FileUtils.deleteDirectory(file);
                    log.info("Delete directory({}) successful", file.getAbsolutePath());
                }
            } catch (IOException ex) {
                log.warn("Could not cleanup db directory {}", dbDir);
                throw new IllegalStateException("Error when cleanup db dir", ex);
            }
        }

        /**
         * Fetch db schema and rebuild data from other nodes
         */
        void onPostStart() throws Exception {
            log.info("Fetching DB schema");
            schemaUtil.scanAndSetupDb(true);
            log.info("DB schema validated");

            // Rebuild everything from other site
            log.info("Start rebuilding data");
            StorageService.instance.rebuild(null);
            log.info("Rebuilding data done");

            cleanReinitFlags();
        }

        /**
         * Remove given flags and flag files
         */
        private void cleanReinitFlags() {
            removeFlag(Constants.REINIT_DB);
            removeFlagFile(Constants.REINIT_DB);
        }

        /**
         * Remove given flag file under dbDir
         */
        private void removeFlagFile(String flagName) {
            File flagFile = new File(dbDir, flagName);
            if (flagFile.exists()) {
                flagFile.delete();
            }
        }
    }

    static class ObsoletePeersCleanupMode extends NormalMode {
        List<String> obsoletePeers;

        ObsoletePeersCleanupMode(Configuration config) {
            super(config);
            type = StartupModeType.OBSOLETE_PEERS_CLEANUP_MODE;
        }

        void setObsoletePeers(List<String> peers) {
            obsoletePeers = peers;
        }

        /**
         * Disable loading peers from system table - since there are obsolete peers we are going to drop
         */
        void onPreStart() {
            log.info("Found obsolete cassandra peers {}. Disable loading peer and ring state from system table",
                    StringUtils.join(obsoletePeers.iterator(), ","));
            System.setProperty("cassandra.load_ring_state", "false");
        }

        /**
         * Drop peers info and token from system table
         */
        void onPostStart() throws Exception {
            super.onPostStart();
            for (String peer : obsoletePeers) {
                log.info("Remove node {} from cassandra peer table", peer);
                SystemKeyspace.removeEndpoint(InetAddress.getByName(peer));
            }
            removeFlag(Constants.OBSOLETE_CASSANDRA_PEERS);
        }
    }

    static class HibernateMode extends DbReinitMode {
        static int STATE_CHECK_INTERVAL = 10 * 1000; // state check interval in ms
        static int LOG_MSG_THROTTLE = 6; // log a message for every LOG_MSG_THROTTLE checks

        HibernateMode(Configuration config) {
            super(config);
            type = StartupModeType.HIBERNATE_MODE;
        }

        void onPreStart() {
            waitForRecoveryStatusChanged(RecoveryStatus.Status.SYNCING);
            super.onPreStart();
        }

        void onPostStart() throws Exception {
            super.onPostStart();
            DbServiceImpl.instance.removeStartupModeOnDisk();
        }

        private void waitForRecoveryStatusChanged(RecoveryStatus.Status targetStatus) {
            int cnt = 0;
            while (true) {
                RecoveryStatus.Status status = getRecoveryStatus();
                if (status == null) {
                    log.error("Failed to get recovery status");
                    throw new IllegalStateException("Failed to get recovery status");
                }

                if (targetStatus.equals(status)) {
                    log.info("Recovery status is SYNCING now");
                    break;
                }
                try {
                    if (cnt++ % LOG_MSG_THROTTLE == 0) {
                        log.info("Wait on recovery status");
                    }
                    Thread.sleep(STATE_CHECK_INTERVAL);
                } catch (InterruptedException ex) {
                    log.warn("Thread is interrupted during refresh recovery status", ex);
                }
            }
        }

        private RecoveryStatus.Status getRecoveryStatus() {
            RecoveryStatus.Status status = null;
            Configuration cfg = coordinator.queryConfiguration(Constants.NODE_RECOVERY_STATUS, Constants.GLOBAL_ID);
            if (cfg != null) {
                String statusStr = cfg.getConfig(RecoveryConstants.RECOVERY_STATUS);
                if (statusStr != null && statusStr.length() > 0) {
                    status = RecoveryStatus.Status.valueOf(statusStr);
                }
            }
            log.info("Recovery status is: {}", status);
            return status;
        }
    }

    static class GeodbRestoreMode extends DbReinitMode {
        GeodbRestoreMode(Configuration config) {
            super(config);
            type = StartupModeType.RESTORE_MODE;
        }

        void onPreStart() {
            if (!Boolean.parseBoolean(config.getConfig(Constants.STARTUPMODE_RESTORE_REINIT))) {
                config.setConfig(Constants.STARTUPMODE_RESTORE_REINIT, Boolean.TRUE.toString());
                coordinator.persistServiceConfiguration(coordinator.getSiteId(), config);
            }
            super.onPreStart();
        }

        void onPostStart() throws Exception {
            super.onPostStart();
            DbServiceImpl.instance.removeStartupModeOnDisk();
            removeFlag(Constants.STARTUPMODE_RESTORE_REINIT);
        }
    }
}
