package com.emc.storageos.systemservices.impl.driver;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.emc.storageos.coordinator.client.model.Site;
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
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import static com.emc.storageos.coordinator.client.model.Constants.*;

public class DriverManager {

    public static final String DRIVER_DIR = "/data/drivers/";
    public static final String CONTROLLER_SERVICE = "controllersvc";
    public static final String INSTALLING = "installing";
    public static final String UNINSTALLING = "uninstalling";
    public static final String ACTIVE = "active";

    private static final String LISTEN_PATH = String.format("/config/%s/%s", StorageDriversInfo.KIND,
            StorageDriversInfo.ID);
    private static final Logger log = LoggerFactory.getLogger(PropertyManager.class);
    private static final String DRIVERS_UPDATE_LOCK = "driversupdatelock";
    private static final ThreadPoolExecutor EXECUTOR = new NamedThreadPoolExecutor("DriverUpdateThead", 1);

    private Set<String> localDrivers;
    private Set<String> targetDrivers;
    private LocalRepository localRepository;
    private CoordinatorClientExt coordinator;
    private CoordinatorClient coordinatorClient;
    private DrUtil drUtil;
    private DbClient dbClient;
    private Service service;
    private URI initNode; // Node that has been synced with latest driver list
    private LocalRepository localRepo = LocalRepository.getInstance();

    private Set<String> toRemove;
    private Set<String> toDownload;

    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
        this.coordinatorClient = coordinator.getCoordinatorClient();
        this.drUtil = new DrUtil(coordinatorClient);
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

    private void restartControllerService() {
        try {
            localRepository.restart(CONTROLLER_SERVICE);
            log.info("Local controller service has been restarted");
        } catch (Exception e) {
            log.error("Failed to restart controller service", e);
        }
    }

    private boolean areAllNodesUpdated() throws Exception {
        
        for (Site site : drUtil.listSites()) {
            Map<Service, StorageDriversInfo> localInfos = coordinator.getAllNodeInfos(StorageDriversInfo.class,
                    CONTROL_NODE_SYSSVC_ID_PATTERN, site.getUuid());
            for (Map.Entry<Service, StorageDriversInfo> info : localInfos.entrySet()) {
                if (!targetDrivers.equals(info.getValue().getInstalledDrivers())) {
                    log.info("Drivers on node {} have not been updated", info.getKey().getName());
                    return false;
                }
            }
        }
        return true;
    }

    private void updateMetaData() {
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (it.hasNext()) {
            StorageSystemType type = it.next();
            String status = type.getInstallStatus();
            if (!StringUtils.equals(status, INSTALLING) && !StringUtils.equals(status, UNINSTALLING)) {
                continue;
            }
            if (toDownload.contains(type.getDriverFileName())) {
                type.setInstallStatus("active");
                dbClient.updateObject(type);
                log.info("update: {} done", type.getDriverFileName());
            } else if (toRemove.contains(type.getDriverFileName())) {
                dbClient.removeObject(type);
                log.info("remove: {} done", type.getDriverFileName());
            }
        }
    }

    private void removeDrivers(Set<String> drivers) {
        for (String driver : drivers) {
            LocalRepository.getInstance().removeStorageDriver(driver);
        }
    }

    private void downloadDrivers(Set<String> drivers) {
        for (String driver : drivers) {
            File driverFile = new File(DRIVER_DIR + driver);
            try {
                String uri = SysClientFactory.URI_GET_DRIVER + "?name=" + driver;
                InputStream in = SysClientFactory.getSysClient(initNode).get(new URI(uri),
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

                log.info("Driver {} has been downloaded from {}", driver, initNode);
            } catch (Exception e) {
                log.error("Failed to download driver {} with exception", driver, e);
            }
        }
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
        localDrivers = localRepo.getLocalDrivers();
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

    private void registerLocalDrivers() {
        StorageDriversInfo info = new StorageDriversInfo();
        info.setInstalledDrivers(localRepo.getLocalDrivers());
        coordinator.setNodeSessionScopeInfo(info);
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
        localDrivers = localRepo.getLocalDrivers();
        StorageDriversInfo info = new StorageDriversInfo();
        info.setInstalledDrivers(localDrivers);
        coordinator.setNodeSessionScopeInfo(info);
    }

    private URI getSyncedNode() {
        try {
            DrUtil drUtil = new DrUtil(coordinatorClient);
            String activeSiteId = drUtil.getActiveSite().getUuid();
            Map<Service, StorageDriversInfo> localInfos = coordinator.getAllNodeInfos(StorageDriversInfo.class,
                    CONTROL_NODE_SYSSVC_ID_PATTERN, activeSiteId);
            List<String> candidates = new ArrayList<>();
            for (Map.Entry<Service, StorageDriversInfo> info : localInfos.entrySet()) {
                if (targetDrivers.equals(info.getValue().getInstalledDrivers())) {
                    candidates.add(info.getKey().getId());
                    log.info("Add node {} to synced nodes list", info.getKey().getId());
                }
            }

            if (!candidates.isEmpty()) {
                String syssvcId = candidates.get(new Random().nextInt(candidates.size()));
                return coordinator.getNodeEndpointForSvcId(syssvcId);
            } else {
                // This should not happen
                log.error("There's no synced node now");
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
        EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                initializeLocalAndTargetInfo();

                // remove drivers and restart controller service
                toRemove = minus(localDrivers, targetDrivers);
                toDownload = minus(targetDrivers, localDrivers);
                if (toRemove.isEmpty() && toDownload.isEmpty()) {
                    log.info("Local installed drivers list is synced with target,return");
                    return;
                }

                if (!toRemove.isEmpty()) {
                    removeDrivers(toRemove);
                }

                if (!toDownload.isEmpty()) {
                    initNode = getSyncedNode();
                    if (initNode == null) {
                        log.error("Can't find a synced node to download driver jar files");
                        return;
                    }
                    downloadDrivers(toDownload);
                }
                updateLocalDriversList();

                // restart controller service
                restartControllerService();

                log.info("Update finished smoothly, congratulations");

                InterProcessLock lock = null;
                try {
                    lock = getLock(DRIVERS_UPDATE_LOCK);
                    if (areAllNodesUpdated()) {
                        updateMetaData();
                    }
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

    public void start() {
        addDriverInfoListener();
        registerLocalDrivers();
        checkAndUpdate();
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
