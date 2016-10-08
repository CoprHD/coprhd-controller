package com.emc.storageos.systemservices.impl.driver;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.DriverInfo2;
import com.emc.storageos.coordinator.client.model.StorageDriversInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;

public class DriverManager {

    public static final String DRIVER_DIR = "/data/drivers";
    public static final String CONTROLLER_SERVICE = "controllersvc";
    // private static final String LISTEN_PATH = String.format("/config/%s/%s",
    // DriverInfo2.CONFIG_KIND, DriverInfo2.CONFIG_ID);
    // use new ZNode that only records target installed drivers
    private static final String LISTEN_PATH = String.format("/config/%s/%s", StorageDriversInfo.KIND,
            StorageDriversInfo.ID);
    private static final Logger log = LoggerFactory.getLogger(PropertyManager.class);
    private static final int MAX_RETRY_TIMES = 5;
    private static final String DRIVERS_UPDATE_LOCK = "driversupdatelock";
    private static final ThreadPoolExecutor EXECUTOR = new NamedThreadPoolExecutor("DriverUpdateThead", 1);
    protected volatile boolean doRun = true;

    private Set<String> localDrivers;
    private Set<String> targetDrivers;
    private boolean needRestartControllerService = false;
    private LocalRepository localRepository;
    private CoordinatorClientExt coordinator;
    private CoordinatorClient coordinatorClient;
    private DbClient dbClient;
    private Service service;
    private String initNode; // Node that has been synced with latest driver list

    private Set<String> toRemove;
    private Set<String> toDownload;

    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
        this.coordinatorClient = coordinator.getCoordinatorClient();
    }

    public void setLocalRepository(final LocalRepository localRepository) {
        this.localRepository = localRepository;
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

    /**
     * return true if no need to restart or restart succeeded, otherwise return
     * false
     */
    private boolean restartControllerService() {
        if (!needRestartControllerService) {
            return true;
        }
        try {
            localRepository.restart(CONTROLLER_SERVICE);
            log.info("Local controller service has been restarted");
            needRestartControllerService = false; // reset restart flag after
                                                  // successfully restarting
            return true;
        } catch (Exception e) {
            log.error("Failed to restart controller service", e);
            needRestartControllerService = true;
            return false;
        }
    }

    //TODO to see if all nodes are synced now
    private boolean areAllNodesUpdated() {
        return true;
    }

    // TODO need to change storagesystemtype object definition
    private void updateMetaData() {
        if (!(toDownload != null && !toDownload.isEmpty()) && !(toRemove != null && !toRemove.isEmpty())) {
            return;
        }
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> iter = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (iter.hasNext()) {
            StorageSystemType type = iter.next();
            if (toDownload != null && toDownload.contains(type.getDriverFileName())) {
                type.setIsUsable(true);
                dbClient.updateObject(type);
                log.info("update: {} done", type.getDriverFileName());
            } else if (toRemove != null && toRemove.contains(type.getDriverFileName())) {
                dbClient.removeObject(type);
                log.info("remove: {} done", type.getDriverFileName());
            }
        }
    }

    private boolean removeDrivers(Set<String> drivers) {
        for (String driver : drivers) {
            File driverFile = new File(DRIVER_DIR + "/" + driver);
            if (!driverFile.exists()) {
                continue;
            }
            if (!driverFile.delete()) {
                log.warn("Failed to delete driver file {}", driver);
                return false;
            } else {
                // If driver is deleted successfully deleted, need to restart
                // controller service
                needRestartControllerService = true;
                log.info("Driver {} has been removed", driver);
            }
        }
        return true;
    }

    private boolean downloadDrivers(Set<String> drivers) {
        for (String driver : drivers) {
            File driverFile = new File(DRIVER_DIR + "/" + driver);
            try {
                String uri = SysClientFactory.URI_GET_DRIVER + "?name=" + driver;
                InputStream in = SysClientFactory.getSysClient(URI.create(initNode)).get(new URI(uri),
                        InputStream.class, MediaType.APPLICATION_OCTET_STREAM);

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

                needRestartControllerService = true;
                log.info("Driver {} has been downloaded from {}", driver, initNode);
            } catch (Exception e) {
                log.error("Failed to download driver {} with exception", driver, e);
                return false;
            }
        }
        return true;
    }

    /**
     * @return elements who are included in original list but not in subtractor list
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
        localDrivers = getLocalDrivers();
        log.info("Local drivers initialized: {}", Arrays.toString(localDrivers.toArray()));

        StorageDriversInfo targetInfo = coordinator.getTargetInfo(StorageDriversInfo.class);
        if (targetInfo == null) {
            targetInfo = new StorageDriversInfo();
            targetInfo.setInstalledDrivers(localDrivers);
            coordinator.setTargetInfo(targetInfo);
            log.info("Can't find target storage drivers info, so init it with local drivers list");
        }
        targetDrivers = targetInfo.getInstalledDrivers();
        log.info("Target drivers info initialized: {}", Arrays.toString(targetDrivers.toArray()));
    }

    private Set<String> getLocalDrivers() {
        File driverDir = new File(DRIVER_DIR);
        if (!driverDir.exists() || !driverDir.isDirectory()) {
            driverDir.mkdir();
            log.info("Drivers directory: {} has been created", DRIVER_DIR);
            return new HashSet<String>();
        }
        File[] driverFiles = driverDir.listFiles();
        Set<String> drivers = new HashSet<String>();
        for (File driver : driverFiles) {
            drivers.add(driver.getName());
        }
        return drivers;
    }

    public void addDriverInfoListener() {
        try {
            coordinator.getCoordinatorClient().addNodeListener(new DriverInfoListener());
        } catch (Exception e) {
            log.error("Fail to add node listener for driver info target znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("Successfully added node listener for driver info target znode");
    }

    public void stop() {
        doRun = false;
    }

    /**
     * Update locally installed drivers list to syssvc service beacon
     */
    public void updateLocalDriversList() {
        localDrivers = getLocalDrivers();
        StorageDriversInfo info = new StorageDriversInfo();
        info.setInstalledDrivers(localDrivers);
        coordinator.setNodeSessionScopeInfo(info);
    }

    // TODO
    // should return a ip:9998 format string
    private String getSyncedNode() {
        return "TODO";
    }

    /**
     * Check and update local drivers asynchronously, so not to block
     * notification thread
     */
    private void checkAndUpdate() {
        EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                int retryTimes = 0;
                while (doRun) {
                    if (retryTimes > MAX_RETRY_TIMES) {
                        log.error("Retry time exceeded, exit loop");
                        break;
                    }
                    retryTimes++;

                    initializeLocalAndTargetInfo();

                    // remove drivers and restart controller service
                    toRemove = minus(localDrivers, targetDrivers);
                    boolean removeSuccess = false;
                    if (toRemove != null && !toRemove.isEmpty()) {
                        try {
                            removeSuccess = removeDrivers(toRemove);
                            if (removeSuccess) {
                                updateLocalDriversList();
                            }
                        } catch (Exception e) {
                            log.warn("Exception thrown when trying to remove drivers file", e);
                            continue;
                        } finally {
                            restartControllerService();
                            if (!removeSuccess) {
                                log.warn("Failed to remove some driver file, will short sleep and retry");
                                continue;
                            }
                        }
                    }

                    // download drivers and restart controller service
                    toDownload = minus(targetDrivers, localDrivers);
                    boolean downloadSuccess = false;
                    if (toDownload != null && !toDownload.isEmpty()) {
                        try {
                            // need to initialize initNode first
                            initNode = getSyncedNode();
                            if (initNode == null) {
                                log.error("Can't find a synced node to download driver jar files");
                                return;
                            }
                            downloadSuccess = downloadDrivers(toDownload);
                            if (downloadSuccess) {
                                updateLocalDriversList();
                            }
                        } catch (Exception e) {
                            log.warn("Exception thrown when trying to download drivers file", e);
                            continue;
                        } finally {
                            restartControllerService();
                            if (!downloadSuccess) {
                                log.warn("Failed to download some driver file, will short sleep and retry");
                                continue;
                            }
                        }
                    }

                    // restart controller service if it's needed
                    if (!restartControllerService()) {
                        continue;
                    }
                    // It means all logic finished smoothly if thread goes here,
                    // exit loop, finish thread
                    log.info("Update finished smoothly, congratulations");
                    if ((toRemove == null || toRemove.isEmpty()) && (toDownload == null || toDownload.isEmpty())) {
                        log.info("No change, no need to update drivers info, break to finish thread");
                        break;
                    }
                    InterProcessLock lock = null;
                    try {
                        lock = getLock(DRIVERS_UPDATE_LOCK);
                        if (areAllNodesUpdated()) {
                            log.info("Last update thread has finished update");
                            updateMetaData();
                        }
                        break;
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
        });
    }

    private InterProcessLock getLock(String name) throws Exception {
        InterProcessLock lock = null;
        while (true) {
            try {
                lock = coordinator.getCoordinatorClient().getLock(name); // global
                                                                         // lock
                                                                         // across
                                                                         // all
                                                                         // sites
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
