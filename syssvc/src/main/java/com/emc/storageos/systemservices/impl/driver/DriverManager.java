package com.emc.storageos.systemservices.impl.driver;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.DriverInfo;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.systemservices.impl.util.AbstractManager;

public class DriverManager {

    public static final String DRIVER_DIR = "/data/drivers";
    public static final String CONTROLLER_SERVICE = "controllersvc";
    private static final String LISTEN_PATH = String.format("/config/%s/%s", DriverInfo.KIND, DriverInfo.ID);
    private static final Logger log = LoggerFactory.getLogger(PropertyManager.class);
    private static final int MAX_RETRY_TIMES = 5;

    protected volatile boolean doRun = true;

    private List<String> localDrivers;
    private List<String> targetDrivers;
    private boolean needRestartControllerService = false;
    private String finishNode;
    private LocalRepository localRepository;
    protected CoordinatorClientExt coordinator;

    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
    }

    public void setLocalRepository(final LocalRepository localRepository) {
        this.localRepository = localRepository;
    }

    /**
     * return true if no need to restart or restart succeeded, otherwise return false
     */
    private boolean restartControllerService() {
        if (!needRestartControllerService) {
            return true;
        }
        try {
            localRepository.restart(CONTROLLER_SERVICE);
            log.info("Local controller service has been restarted");
            needRestartControllerService = false; // reset restart flag after successfully restarting
            return true;
        } catch (Exception e) {
            log.error("Failed to restart controller service", e);
            needRestartControllerService = true;
            return false;
        }
    }

    private boolean removeDrivers(List<String> drivers) {
        for (String driver : drivers) {
            File driverFile = new File(DRIVER_DIR + "/" + driver);
            if (!driverFile.exists()) {
                continue;
            }
            if(!driverFile.delete()) {
                log.warn("Failed to delete driver file {}", driver);
                return false;
            } else {
                // If driver is deleted successfully deleted, need to restart controller service
                needRestartControllerService = true;
                log.info("Driver {} has been removed", driver);
            }
        }
        return true;
    }

    private boolean downloadDrivers(List<String> drivers) {
        for (String driver : drivers) {
            File driverFile = new File (DRIVER_DIR + "/" + driver);
            try {
                String uri = SysClientFactory.URI_GET_DRIVER + "?name=" + driver;
                InputStream in = SysClientFactory.getSysClient(URI.create(finishNode)).get(new URI(uri), InputStream.class, MediaType.APPLICATION_OCTET_STREAM);

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
                log.info("Driver {} has been downloaded from {}", driver, finishNode);
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
    private List<String> minus(List<String> original, List<String> subtractor) {
        List<String> result = new ArrayList<String>();
        for (String element : original) {
            if (subtractor.contains(element)) {
                continue;
            }
            result.add(element);
        }
        return result;
    }

    private void assureDriverInfoNodeExist() {
        if (!coordinator.getCoordinatorClient().nodeExists(LISTEN_PATH)) {
            DriverInfo initDriversInfo = new DriverInfo();
            coordinator.getCoordinatorClient().setTargetInfo(initDriversInfo);
            log.info("Created DriverInfo ZNode");
        }
    }
    private void initializeLocalAndTargetInfo() {
        localDrivers = getLocalDrivers();
        log.info("Local drivers initialized: {}", Arrays.toString(localDrivers.toArray()));

        assureDriverInfoNodeExist();

        DriverInfo targetDriversInfo = coordinator.getCoordinatorClient().getTargetInfo(DriverInfo.class);
        if (targetDriversInfo != null) {
            targetDrivers = targetDriversInfo.getDrivers();
            finishNode = targetDriversInfo.getFinishNode();
        } else {
            targetDrivers = new ArrayList<String>();
        }
        log.info("Target drivers info initialized: {}, finish node: {}", Arrays.toString(targetDrivers.toArray()), finishNode);
    }

    private List<String> getLocalDrivers() {
        File driverDir = new File(DRIVER_DIR);
        if (!driverDir.exists() || !driverDir.isDirectory()) {
            driverDir.mkdir(); // Need to check result TODO
            log.info("Drivers directory: {} has been created", DRIVER_DIR);
            return new ArrayList<String>();
        }
        File[] driverFiles = driverDir.listFiles();
        List<String> drivers = new ArrayList<String>();
        for (File driver : driverFiles) {
            drivers.add(driver.getName());
        }
        Collections.sort(drivers);
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
     * Check and update local drivers asynchronously, so not to block notification thread
     */
    private void checkAndUpdate() {
        Thread  t = new Thread(new Runnable() {
            @Override
            public void run() {
                int retryTimes = 0;
                while (doRun) {
                    if (retryTimes > MAX_RETRY_TIMES) {
                        log.error("Retry time exceeded, exit loop");
                        break;
                    }
                    retryTimes ++;

                    initializeLocalAndTargetInfo();

                    // remove drivers and restart controller service
                    List<String> toRemove = minus(localDrivers, targetDrivers);
                    boolean removeSuccess = false;
                    if (toRemove != null && !toRemove.isEmpty()) {
                        try {
                            removeSuccess = removeDrivers(toRemove);
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
                    List<String> toDownload = minus(targetDrivers, localDrivers);
                    boolean downloadSuccess = false;
                    if (toDownload != null && !toDownload.isEmpty()) {
                        try {
                            downloadSuccess = downloadDrivers(toDownload);
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
                    if(!restartControllerService()) {
                        continue;
                    }
                    // It means all logic finished smoothly if thread goes here, exit loop, finish thread
                    log.info("Update finished smoothly, congratulations");
                    break;
                }
            }
        });
        t.setName("DriverUpdateThead");
        t.start();
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
            log.info("Curator (re)connected. Waking up the driver manager...");
            checkAndUpdate();
        }

        @Override
        public void nodeChanged() throws Exception {
            log.info("Driver info changed. Waking up the driver manager...");
            checkAndUpdate();
        }
    }
}
