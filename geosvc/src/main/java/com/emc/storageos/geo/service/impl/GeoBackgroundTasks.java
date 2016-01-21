/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.geo.service.impl;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.coordinator.client.service.InterProcessLockHolder;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.geo.vdccontroller.impl.InternalDbClient;
import com.emc.storageos.services.util.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Geo service background tasks.
 */
public class GeoBackgroundTasks {
    static final Logger _log = LoggerFactory.getLogger(GeoBackgroundTasks.class);
    private static final String POOL_NAME = "GeoBackgroundTasksPool";
    private ScheduledExecutorService _exe = new NamedScheduledThreadPoolExecutor(POOL_NAME, 3);

    private static final int DEFAULT_VDC_STATUS_INTERVAL = 10;

    // versions with no restGeoBlacklist API support
    private static final List<SoftwareVersion> incompatibleVersions = Collections.unmodifiableList(
            Arrays.asList(new SoftwareVersion(ProductName.getName() + "-2.0.0.0.*"),
                    new SoftwareVersion(ProductName.getName() + "-2.0.0.1.*"),
                    new SoftwareVersion(ProductName.getName() + "-2.1.0.0.*")));

    private CoordinatorClient _coordinatorClient;

    private InternalDbClient _dbClient;

    @Autowired
    private GeoClientCacheManager clientManager;

    private String geodbDir;

    private Integer nodeCount;

    @Autowired
    private VdcConfigHelper helper;

    // In minutes
    private int vdcStatusInterval = DEFAULT_VDC_STATUS_INTERVAL;

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinatorClient = coordinator;
    }

    public void setDbClient(InternalDbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setGeoClientCacheManager(GeoClientCacheManager clientManager) {
        this.clientManager = clientManager;
    }

    public void setGeodbDir(String geodbDir) {
        this.geodbDir = geodbDir;
    }

    public void setNodeCount(Integer nodeCount) {
        this.nodeCount = nodeCount;
    }

    public void setVdcStatusInterval(int vdcStatusInterval) {
        this.vdcStatusInterval = vdcStatusInterval;
    }

    public void setHelper(VdcConfigHelper helper) {
        this.helper = helper;
    }

    public void start() {
        _log.info("Starting geosvc background tasks");
        startBackgroundVdcStatusTask();
        startGeodbRestoreHelper();
    }

    public void stop() {
        _log.info("Stop geosvc background tasks");
        _exe.shutdownNow();
    }

    public void startGeodbNodeRepair() {
        startGeodbNodeRepairBackend();
    }

    private void startBackgroundVdcStatusTask() {
        _log.info("Starting background vdc status task");
        MonitorVdcReachableTask task = new MonitorVdcReachableTask();
        _exe.scheduleWithFixedDelay(task, 0, vdcStatusInterval, TimeUnit.MINUTES);
    }

    private void startGeodbRestoreHelper() {
        _log.info("Starting geodb restore helper");
        _exe.schedule(new GeodbRestoreHelper(), 0, TimeUnit.SECONDS);
    }

    private void startGeodbNodeRepairBackend() {
        _log.info("Starting geodb node repair backend");
        _exe.schedule(new TriggerGeodbNodeRepairBackend(), 0, TimeUnit.SECONDS);
    }

    /**
     * Geodb restore helper to reset geodb blacklist in remote vdc.
     */
    private class GeodbRestoreHelper implements Runnable {
        // Restore check interval in seconds
        private static final int CHECK_INTERVAL = 30;

        @Override
        public void run() {
            if (!isRestoring()) {
                _log.info("No geodb restore state detected. Stopping restore helper");
                return;
            }

            while (!isRestored()) {
                int initCount = getReinitCount();
                if (initCount == nodeCount) {
                    // All geodbsvc go into reinit state. We can release
                    // blacklist now
                    VirtualDataCenter localVdc = getLocalVdc();
                    if (localVdc == null) {
                        _log.error("Fail to find local vdc");
                        break;
                    }
                    String localVdcShortId = localVdc.getShortId();
                    resetBlacklist(localVdcShortId);
                    break;
                }
                try {
                    Thread.sleep(1000 * CHECK_INTERVAL);
                } catch (InterruptedException ex) {
                    // Ignore this exception
                }
            }
            _log.info("GeodbRestoreHelper exits");
        }

        private void resetBlacklist(String localVdcShortId) {
            List<URI> ids = _dbClient
                    .queryByType(VirtualDataCenter.class, true);
            for (URI id : ids) {
                VirtualDataCenter vdc = _dbClient.queryObject(
                        VirtualDataCenter.class, id);
                if (vdc.getConnectionStatus() == ConnectionStatus.CONNECTED
                        && !vdc.getLocal()) {
                    GeoServiceClient client = clientManager.getGeoClient(vdc
                            .getShortId());
                    try {
                        String versionStr = client.getViPRVersion();
                        _log.info("Remote vdc {} vipr version {}", vdc.getShortId(),
                                versionStr);
                        SoftwareVersion version = new SoftwareVersion(versionStr);
                        boolean compatible = true;
                        for (SoftwareVersion incompVer : incompatibleVersions) {
                            if (incompVer.weakEquals(version)) {
                                _log.info("Ignore blacklist reset for incompatible version");
                                compatible = false;
                                break;
                            }
                        }
                        if (compatible) {
                            client.resetBlacklist(localVdcShortId);
                            _log.info("Reset geo blacklist done");
                        }
                    } catch (Exception ex) {
                        _log.error("Reset blacklist error", ex);
                    }
                }
            }
        }

        private VirtualDataCenter getLocalVdc() {
            List<URI> vdcIdList = _dbClient.queryByType(
                    VirtualDataCenter.class, true);
            for (URI vdcId : vdcIdList) {
                VirtualDataCenter vdc = _dbClient.queryObject(
                        VirtualDataCenter.class, vdcId);
                if (vdc.getLocal()) {
                    return vdc;
                }
            }
            return null;
        }

        private boolean isRestoring() {
            return checkRestoreFlag() || getReinitCount() > 0;
        }

        private boolean isRestored() {
            try {
                List<Service> service = _coordinatorClient.locateAllServices(
                        Constants.GEODBSVC_NAME, _dbClient.getSchemaVersion(),
                        (String) null, null);
                _log.info("Geodbsvc started count {}", service.size());
                return service.size() == nodeCount;
            } catch (Exception ex) {
                _log.info("Check geodbsvc beacon error", ex);
            }
            return false;
        }

        private boolean checkRestoreFlag() {
            try {
                File startupModeFile = new File(geodbDir, Constants.STARTUPMODE);
                String modeType = FileUtils.readValueFromFile(startupModeFile, Constants.STARTUPMODE);
                if (Constants.STARTUPMODE_RESTORE_REINIT.equalsIgnoreCase(modeType)) {
                    _log.info("Restore reinit flag detected");
                    return true;
                }
            } catch (Exception e) {
                _log.info("Read startup mode file failed", e);
            }
            return false;
        }

        private int getReinitCount() {
            List<Configuration> configs = _coordinatorClient.queryAllConfiguration(_coordinatorClient.getSiteId(), Constants.GEODB_CONFIG);
            int reinitCount = 0;
            for (int i = 0; i < configs.size(); i++) {
                Configuration config = configs.get(i);
                // Bypasses item of "global" and folders of "version", just check db configurations.
                if (config.getId() == null || config.getId().equals(Constants.GLOBAL_ID)) {
                    continue;
                }

                String restoreReinit = config.getConfig(Constants.STARTUPMODE_RESTORE_REINIT);
                if (restoreReinit != null && Boolean.parseBoolean(restoreReinit)) {
                    _log.info("Geodb is restoring on {}", config.getConfig(DbConfigConstants.NODE_ID));
                    reinitCount++;
                }
            }
            return reinitCount;
        }
    }

    public class MonitorVdcReachableTask implements Runnable {
        private static final String VDC_REACHABLE_LOCK = "vdc_reachable_background_task";
        private static final String LAST_COMPLETED_CHECK = "last_completed_check";
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        private Long getLastCheckTime() {
            Configuration cfg = _coordinatorClient.queryConfiguration(Constants.VDC_HEART_BEATER, Constants.GLOBAL_ID);
            if (cfg != null) {
                String lastCheckTimeStart = cfg.getConfig(LAST_COMPLETED_CHECK);
                if (lastCheckTimeStart != null) {
                    _log.info("Loaded previous check time: {}", lastCheckTimeStart);
                    return Long.parseLong(lastCheckTimeStart);
                }
            }

            _log.info("No previous check time found, we're the first one to check");
            return null;
        }

        private void updateLastCheckTime(long checkStartTime) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setKind(Constants.VDC_HEART_BEATER);
            cfg.setId(Constants.GLOBAL_ID);
            cfg.setConfig(LAST_COMPLETED_CHECK, Long.toString(checkStartTime));
            _log.info("Persisting check time: {}", checkStartTime);
            _coordinatorClient.persistServiceConfiguration(cfg);
        }

        @Override
        public void run() {
            try (InterProcessLockHolder lock = new InterProcessLockHolder(_coordinatorClient, VDC_REACHABLE_LOCK, _log)) {
                long currentTime = System.currentTimeMillis();
                Long lastCheckTime = getLastCheckTime();
                if (lastCheckTime != null && currentTime - lastCheckTime < vdcStatusInterval * 60 * 1000) {
                    _log.info("Skipping VDC connectivity check at {}, previously checked at {}",
                            dateFormat.format(new Date(currentTime)), dateFormat.format(new Date(lastCheckTime)));
                    return;
                }
                _log.info("Performing VDC connectivity check at {}, previously checked at {}",
                        dateFormat.format(new Date(currentTime)),
                        lastCheckTime == null ? "N/A" : dateFormat.format(new Date(lastCheckTime)));

                List<URI> vdcIdList = _dbClient.queryByType(VirtualDataCenter.class, true);
                for (URI vdcId : vdcIdList) {
                    VirtualDataCenter vdc = _dbClient.queryObject(VirtualDataCenter.class, vdcId);
                    long nowTime = System.currentTimeMillis();
                    if (helper.areNodesReachable(vdc.getShortId(),
                            vdc.getHostIPv4AddressesMap(), vdc.getHostIPv6AddressesMap(), false)) {
                        _log.info("The vdc {} is seen at {}.", vdc.getShortId(), new Date(nowTime));
                        vdc.setLastSeenTimeInMillis(nowTime);
                        _dbClient.updateAndReindexObject(vdc);
                    }
                    else {
                        _log.warn("The vdc {} is unreachable at {}.", vdc.getShortId(), new Date(nowTime));
                    }
                }

                updateLastCheckTime(currentTime);
            } catch (Exception e) {
                _log.warn("Unexpected exception {} ", e);
            }
        }
    }

    private class TriggerGeodbNodeRepairBackend implements Runnable {
        @Override
        public void run() {
            String localShortVdcId = VdcUtil.getLocalShortVdcId();
            try {
                _dbClient.runNodeRepairBackEnd(localShortVdcId);
            } catch (Exception ex) {
                _log.error("Geodb node repair failed, ignoring...", ex);
            }
        }
    }

}
