/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.storagedriver;

import static com.emc.storageos.coordinator.client.model.Constants.CONTROL_NODE_SYSSVC_ID_PATTERN;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.StorageDriverMetaData;
import com.emc.storageos.coordinator.client.model.StorageDriversInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import com.emc.storageos.services.util.Waiter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.systemservices.mapper.StorageDriverMapper;
import com.google.common.io.Files;

/**
 * This Manager contains logic including below:
 *   - Monitor drivers' target list at /config/storagedrivers/global
 *   - Download/remove local driver to keep aligned with target list
 *   - Update status of storage system type after operations is done
 */
public class StorageDriverManager {

    public static final String DRIVER_DIR = "/data/drivers/";
    public static final String TMP_DIR = "/tmp/";
    public static final String CONTROLLER_SERVICE = "controllersvc";

    private static final String LISTEN_PATH = String.format("/config/%s/%s", StorageDriversInfo.KIND,
            StorageDriversInfo.ID);
    private static final Logger log = LoggerFactory.getLogger(StorageDriverManager.class);
    private static final String DRIVERS_UPDATE_LOCK = "driversupdatelock";
    private static final String EVENT_SERVICE_TYPE = "StorageDriver";
    private static final ThreadPoolExecutor EXECUTOR = new NamedThreadPoolExecutor("DriverUpdateThead", 1);

    private Set<String> localDriverFiles;
    private Set<String> targetDriverFiles;
    private CoordinatorClientExt coordinator;
    private CoordinatorClient coordinatorClient;
    private DrUtil drUtil;
    private DbClient dbClient;
    private Service service;
    private Waiter waiter = new Waiter();
    private LocalRepository localRepo = LocalRepository.getInstance();

    private Map<String, StorageDriverMetaData> upgradingDriverMap;

    @Autowired
    private AuditLogManager auditMgr;

    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
        this.coordinatorClient = coordinator.getCoordinatorClient();
        this.drUtil = new DrUtil(coordinatorClient);
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setService(Service service) {
        this.service = service;
    }

    private void restartControllerServices() {
        for (String node : coordinator.getAllNodeIds()) {
            localRepo.remoteRestartService(node, CONTROLLER_SERVICE);
        }
    }

    private List<StorageSystemType> queryDriversByStatus(StorageSystemType.STATUS status) {
        List<StorageSystemType> types = new ArrayList<StorageSystemType>();
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (it.hasNext()) {
            StorageSystemType type = it.next();
            if (StringUtils.equals(type.getDriverStatus(), status.toString())) {
                types.add(type);
            }
        }
        return types;
    }

    private boolean updateInstallMetadata(List<StorageDriversInfo> infos) {
        List<StorageSystemType> installingTypes = queryDriversByStatus(StorageSystemType.STATUS.INSTALLING);
        List<StorageSystemType> finishedTypes = new ArrayList<StorageSystemType>();
        boolean needRestart = false;
        log.info("Installing storage system types: {}", concatStorageSystemTypeNames(installingTypes));
        for (StorageSystemType type : installingTypes) {
            boolean finished = true;
            for (StorageDriversInfo info : infos) {
                if (!info.getInstalledDrivers().contains(type.getDriverFileName())) {
                    finished = false;
                    break;
                }
            }
            if (finished) {
                type.setDriverStatus(StorageSystemType.STATUS.ACTIVE.toString());
                dbClient.updateObject(type);
                finishedTypes.add(type);
                log.info("update status from installing to active for {}", type.getStorageTypeName());
                needRestart = true;
            }
        }
        for (String driver : extractDrivers(finishedTypes)) {
            auditCompleteOperation(OperationTypeEnum.INSTALL_STORAGE_DRIVER, AuditLogManager.AUDITLOG_SUCCESS,
                    driver);
        }
        return needRestart;
    }

    private boolean updateUninstallMetadata(List<StorageDriversInfo> infos) {
        List<StorageSystemType> uninstallingTypes = queryDriversByStatus(StorageSystemType.STATUS.UNISNTALLING);
        List<StorageSystemType> finishedTypes = new ArrayList<StorageSystemType>();
        boolean needRestart = false;
        log.info("Uninstalling storage system types: {}", concatStorageSystemTypeNames(uninstallingTypes));
        for (StorageSystemType type : uninstallingTypes) {
            boolean finished = true;
            for (StorageDriversInfo info : infos) {
                if (info.getInstalledDrivers().contains(type.getDriverFileName())) {
                    finished = false;
                    break;
                }
            }
            if (finished) {
                dbClient.removeObject(type);
                finishedTypes.add(type);
                log.info("Remove {}", type.getStorageTypeName());
                needRestart = true;
            }
        }
        for (String driver : extractDrivers(finishedTypes)) {
            auditCompleteOperation(OperationTypeEnum.UNINSTALL_STORAGE_DRIVER, AuditLogManager.AUDITLOG_SUCCESS,
                    driver);
        }
        return needRestart;
    }

    private boolean updateUpgradeMetadata(List<StorageDriversInfo> infos) {
        List<StorageSystemType> upgradingTypes = queryDriversByStatus(StorageSystemType.STATUS.UPGRADING);
        List<StorageSystemType> finishedTypes = new ArrayList<StorageSystemType>();
        boolean needRestart = false;
        Map<String, StorageDriverMetaData> toInsertNewMetaDatas = new HashMap<String, StorageDriverMetaData>();
        log.info("Upgrading storage system types: {}", concatStorageSystemTypeNames(upgradingTypes));
        for (StorageSystemType type : upgradingTypes) {
            String driverName = type.getDriverName();
            String driverFileName = type.getDriverFileName();
            if (upgradingDriverMap.containsKey(driverName)) {
                StorageDriverMetaData metaData = upgradingDriverMap.get(driverName);
                // last one removes old meta data
                boolean finished = true;
                for (StorageDriversInfo info : infos) {
                    if (info.getInstalledDrivers().contains(driverFileName)) {
                        finished = false;
                        break;
                    }
                }
                if (finished) {
                    toInsertNewMetaDatas.put(metaData.getDriverName(), metaData);
                    log.info("DriverUpgradephase1: remove {} ", type.getStorageTypeName());
                    dbClient.removeObject(type);
                }
            } else {
                // last one marks active
                boolean finished = true;
                for (StorageDriversInfo info : infos) {
                    if (!info.getInstalledDrivers().contains(type.getDriverFileName())) {
                        finished = false;
                        break;
                    }
                }
                if (finished) {
                    type.setDriverStatus(StorageSystemType.STATUS.ACTIVE.toString());
                    dbClient.updateObject(type);
                    finishedTypes.add(type);
                    log.info("DriverUpgradephase2: mark active for {}", type.getStorageTypeName());
                    needRestart = true;
                }
            }
        }
        insertMetadata(toInsertNewMetaDatas);
        for (String driver : extractDrivers(finishedTypes)) {
            auditCompleteOperation(OperationTypeEnum.UPGRADE_STORAGE_DRIVER, AuditLogManager.AUDITLOG_SUCCESS,
                    driver);
        }
        return needRestart;
    }

    /**
     * During upgrade, the last node who finished uninstalling old driver takes responsibility
     * to fetch meta data of new driver from ZK (and delete) and store it into DB, and then add
     * new driver file name to target list, to trigger all nodes to download it.
     */
    private void insertMetadata(Map<String, StorageDriverMetaData> toInsertNewMetaDatas) {
        if (toInsertNewMetaDatas.isEmpty()) {
            return;
        }
        StorageDriversInfo info = coordinatorClient.getTargetInfo(StorageDriversInfo.class);
        if (info == null) {
            info = new StorageDriversInfo();
        }
        for (StorageDriverMetaData metaData : toInsertNewMetaDatas.values()) {
            List<StorageSystemType> types = StorageDriverMapper.map(metaData);
            for (StorageSystemType type : types) {
                type.setIsNative(false);
                type.setDriverStatus(StorageSystemType.STATUS.UPGRADING.toString());
            }
            log.info("DriverUpgradePhase1: Delete metadata from zk and insert it into db: {}", metaData.toString());
            dbClient.createObject(types);
            coordinatorClient.removeServiceConfiguration(metaData.toConfiguration());
            info.getInstalledDrivers().add(metaData.getDriverFileName());
        }
        // update target list, trigger new driver downloading
        log.info("DriverUpgradePhase1: trigger downloading for new driver files listed above");
        coordinatorClient.setTargetInfo(info);
    }

    private void updateMetaData() {
        Site activeSite = drUtil.getActiveSite();
        List<StorageDriversInfo> infos = getDriversInfo(activeSite.getUuid());
        if (activeSite.getNodeCount() != infos.size()) {
            log.warn("Not all nodes are online, skip updating meta data");
            return;
        }
        boolean installFinished = updateInstallMetadata(infos);
        boolean uninstallFinished = updateUninstallMetadata(infos);
        boolean upgradeFinished = updateUpgradeMetadata(infos);
        if (installFinished || uninstallFinished || upgradeFinished) {
            restartControllerServices();
        }
    }

    private Map<String, StorageDriverMetaData> getUpgradeDriverMetaDataMap() {
        List<Configuration> configs = coordinatorClient.queryAllConfiguration(StorageDriverMetaData.KIND);
        Map<String, StorageDriverMetaData> result = new HashMap<String, StorageDriverMetaData>();
        if (configs == null || configs.isEmpty()) {
            return result;
        }
        for (Configuration config : configs) {
            StorageDriverMetaData newMetaData = new StorageDriverMetaData(config);
            result.put(newMetaData.getDriverName(), newMetaData);
        }
        return result;
    }

    private String concatStorageSystemTypeNames(List<StorageSystemType> types) {
        if (types == null || types.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (StorageSystemType type : types) {
            builder.append(type.getStorageTypeName()).append(',');
        }
        builder.setCharAt(builder.length() - 1, ']');
        return builder.toString();
    }

    private List<StorageDriversInfo> getDriversInfo(String siteId) {
        List<StorageDriversInfo> infos = new ArrayList<StorageDriversInfo>();
        try {
            Map<Service, StorageDriversInfo> localInfos = coordinator.getAllNodeInfos(StorageDriversInfo.class,
                    CONTROL_NODE_SYSSVC_ID_PATTERN, siteId);
            for (Map.Entry<Service, StorageDriversInfo> info : localInfos.entrySet()) {
                log.info("Storage drivers info for {} of {}: {}", info.getKey().getId(), siteId,
                        Arrays.toString(info.getValue().getInstalledDrivers().toArray()));
                infos.add(info.getValue());
            }
        } catch (Exception e) {
            log.error("Error happened when geting drivers info for site {}", siteId);
        }
        return infos;
    }

    private boolean hasActiveSiteFinishDownload(String driverFileName) {
        Site activeSite = drUtil.getActiveSite();
        String activeSiteId = activeSite.getUuid();
        List<StorageDriversInfo> infos = getDriversInfo(activeSiteId);
        if (activeSite.getNodeCount() != infos.size()) {
            // there're offline node in active site
            return false;
        }
        for (StorageDriversInfo info : infos) {
            if (!info.getInstalledDrivers().contains(driverFileName)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNewDriverFile(String fileName) {
        for (StorageDriverMetaData driver : upgradingDriverMap.values()) {
            if (StringUtils.equals(driver.getDriverFileName(), fileName)) {
                return true;
            }
        }
        return false;
    }

    private void removeDrivers(Set<String> drivers) {
        for (String driver : drivers) {
            if (isNewDriverFile(driver)) {
                log.info("{} is new driver file, skip removing it", driver);
                continue;
            }
            log.info("removing driver file: {}", driver);
            LocalRepository.getInstance().removeStorageDriver(driver);
        }
    }

    private void downloadDrivers(Set<String> drivers) {
        for (String driver : drivers) {
            File driverFile = new File(TMP_DIR + driver);
            try {
                URI endPoint = null;
                if (drUtil.isActiveSite()) {
                    endPoint = getSyncedNode(driver);
                } else {
                    while (!hasActiveSiteFinishDownload(driver)) {
                        log.info("Sleep 5 seconds to wait active site finish downloading driver {}", driver);
                        waiter.sleep(5000);
                    }
                    Site activeSite = drUtil.getActiveSite();
                    endPoint = URI.create(String.format(SysClientFactory.BASE_URL_FORMAT, activeSite.getVipEndPoint(),
                            service.getEndpoint().getPort()));
                    log.info("Endpoint has been substituted, new endpoint is: {}", endPoint.toString());
                }
                if (endPoint == null) {
                    // should not happen
                    log.error("Can't find node that hold driver file: {}", driver);
                    continue;
                }
                String uri = SysClientFactory.URI_GET_DRIVER + "?name=" + driver;
                log.info("Prepare to download driver file {} from uri {}", driver, uri);
                InputStream in = SysClientFactory.getSysClient(endPoint).get(new URI(uri), InputStream.class,
                        MediaType.APPLICATION_OCTET_STREAM);

                OutputStream os = new BufferedOutputStream(new FileOutputStream(driverFile));
                int bytesRead = 0;
                while (true) {
                    byte[] buffer = new byte[0x10000];
                    bytesRead = in.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }
                    os.write(buffer, 0, bytesRead);
                }
                in.close();
                os.close();
                Files.move(driverFile, new File(DRIVER_DIR + driverFile.getName()));
                log.info("Driver {} has been downloaded from {}", driver, endPoint);
            } catch (Exception e) {
                log.error("Failed to download driver {} with exception", driver, e);
            }
        }
    }

    /**
     * @return elements who are included in original list but not in subtractor
     *         list
     */
    private Set<String> minus(Set<String> original, Set<String> subtractor) {
        Set<String> result = new HashSet<String>();
        for (String element : original) {
            if (subtractor.contains(element)) {
                continue;
            }
            result.add(element);
        }
        return result;
    }

    private void initializeLocalAndTargetInfo() {
        localDriverFiles = localRepo.getLocalDrivers();
        log.info("Local drivers initialized: {}", Arrays.toString(localDriverFiles.toArray()));

        StorageDriversInfo targetInfo = coordinator.getTargetInfo(StorageDriversInfo.class);
        if (targetInfo == null) {
            targetInfo = new StorageDriversInfo();
            targetInfo.setInstalledDrivers(localDriverFiles);
            coordinator.setTargetInfo(targetInfo);
            log.info("Can't find target storage drivers info, so init it with local drivers list");
        }
        targetDriverFiles = targetInfo.getInstalledDrivers();
        log.info("Target drivers info initialized: {}", Arrays.toString(targetDriverFiles.toArray()));
    }

    private void addDriverInfoListener() {
        try {
            coordinator.getCoordinatorClient().addNodeListener(new DriverInfoListener());
        } catch (Exception e) {
            log.error("Fail to add node listener for driver info target znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("Successfully added node listener for driver info target znode");
    }

    /**
     * Update locally installed drivers list to syssvc service beacon
     */
    public void updateLocalDriversList() {
        localDriverFiles = localRepo.getLocalDrivers();
        StorageDriversInfo info = new StorageDriversInfo();
        info.setInstalledDrivers(localDriverFiles);
        coordinator.setNodeSessionScopeInfo(info);
        log.info("Updated local driver list to ZK service beacon: {}", Arrays.toString(localDriverFiles.toArray()));
    }

    /**
     * 
     * @param driverFileName
     * @return node who holds driver file from active site
     */
    private URI getSyncedNode(String driverFileName) {
        try {
            String activeSiteId = drUtil.getActiveSite().getUuid();
            Map<Service, StorageDriversInfo> localInfos = coordinator.getAllNodeInfos(StorageDriversInfo.class,
                    CONTROL_NODE_SYSSVC_ID_PATTERN, activeSiteId);
            List<String> candidates = new ArrayList<>();
            for (Map.Entry<Service, StorageDriversInfo> info : localInfos.entrySet()) {
                if (info.getValue().getInstalledDrivers().contains(driverFileName)) {
                    candidates.add(info.getKey().getId());
                    log.info("Add node {} to synced nodes list", info.getKey().getId());
                }
            }

            if (!candidates.isEmpty()) {
                String syssvcId = candidates.get(new Random().nextInt(candidates.size()));
                return coordinator.getNodeEndpointForSvcId(syssvcId);
            } else {
                // This should not happen
                log.error("There's no synced node for {} now", driverFileName);
            }
        } catch (Exception e) {
            log.error("Can't find node in sync with target drivers list");
        }
        return null;
    }

    /**
     * Check and update local drivers asynchronously, so not to block
     * notification thread
     */
    private void checkAndUpdate() {
        EXECUTOR.execute(new DriverOperationRunner());
    }

    class DriverOperationRunner implements Runnable {

        @Override
        public void run() {
            initializeLocalAndTargetInfo();

            upgradingDriverMap = getUpgradeDriverMetaDataMap();

            // remove drivers and restart controller service
            Set<String> toRemove = minus(localDriverFiles, targetDriverFiles);
            Set<String> toDownload = minus(targetDriverFiles, localDriverFiles);

            if (!toRemove.isEmpty()) {
                removeDrivers(toRemove);
            }

            if (!toDownload.isEmpty()) {
                downloadDrivers(toDownload);
            }

            // After download/remove drivers, update progress to ZK
            updateLocalDriversList();

            // Active site need to update medata and restart all controller
            // services
            if (drUtil.isActiveSite()) {
                InterProcessLock lock = null;
                try {
                    lock = getLock(DRIVERS_UPDATE_LOCK);
                    updateMetaData();
                } catch (Exception e) {
                    log.error("error happend when updating driver info", e);
                } finally {
                    if (lock != null) {
                        try {
                            lock.release();
                        } catch (Exception ignore) {
                            log.warn("lock release failed");
                        }
                    }
                }
            }
        }
    }

    private InterProcessLock getLock(String name) throws Exception {
        InterProcessLock lock = null;
        while (true) {
            try {
                // As only active site nodes will try to acquiring this lock
                // site local lock is competent
                lock = coordinator.getCoordinatorClient().getSiteLocalLock(name);
                lock.acquire();
                break; // got lock
            } catch (Exception e) {
                if (coordinator.isConnected()) {
                    throw e;
                }
            }
        }
        return lock;
    }

    public void start() {
        updateLocalDriversList();
        addDriverInfoListener();
    }

    private Set<String> extractDrivers(List<StorageSystemType> types) {
        Set<String> result = new HashSet<String>();
        if (types == null || types.isEmpty()) {
            return result;
        }
        for (StorageSystemType type :types) {
            result.add(type.getDriverName());
        }
        return result;
    }

    private void auditCompleteOperation(OperationTypeEnum type, String status, Object... descparams) {
        auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE, type, System.currentTimeMillis(), status,
                AuditLogManager.AUDITOP_END, descparams);
    }

    class DriverInfoListener implements NodeListener {
        @Override
        public String getPath() {
            return LISTEN_PATH;
        }

        @Override
        public void connectionStateChanged(State state) {
            log.info("Driver info connection state changed to {}", state);
            if (state != State.CONNECTED) {
                return;
            }
            log.info("Curator (re)connected. Try to pull latest info and update local driver if necessary ...");
            checkAndUpdate();
        }

        @Override
        public void nodeChanged() throws Exception {
            log.info("Driver info changed. Try to pull latest info and update local driver if necessary ...");
            checkAndUpdate();
        }
    }
}
