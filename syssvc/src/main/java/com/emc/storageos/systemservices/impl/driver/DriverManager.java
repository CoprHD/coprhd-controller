package com.emc.storageos.systemservices.impl.driver;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.DriverInfo;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.util.AbstractManager;

public class DriverManager extends AbstractManager {

    public static final String DRIVER_DIR = "/data/drivers";
    public static final String CONTROLLER_SERVICE = "controllersvc";
    private static final Logger log = LoggerFactory.getLogger(PropertyManager.class);

    protected volatile boolean doRun = true;

    private List<String> localDrivers;
    private List<String> targetDrivers;
    private boolean needRestartControllerService = false;

    @Override
    protected URI getWakeUpUrl() {
        return SysClientFactory.URI_WAKEUP_DRIVER_MANAGER;
    }

    @Override
    protected void innerRun() {

        addDriverInfoListener();

        while (doRun) {
            initializeLocalAndTargetInfo();

            // remove drivers and restart controller service
            List<String> toRemove = minus(localDrivers, targetDrivers);
            boolean removeSuccess = false;
            if (toRemove != null && !toRemove.isEmpty()) {
                try {
                    removeSuccess = removeDrivers(toRemove);
                } catch (Exception e) {
                    log.warn("Exception thrown when trying to remove drivers file", e);
                    retrySleep();
                    continue;
                } finally {
                    restartControllerService();
                    if (!removeSuccess) {
                        log.warn("Failed to remove some driver file, will short sleep and retry");
                        retrySleep();
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
                    retrySleep();
                    continue;
                } finally {
                    restartControllerService();
                    if (!downloadSuccess) {
                        log.warn("Failed to download some driver file, will short sleep and retry");
                        retrySleep();
                        continue;
                    }
                }
            }

            // restart controller service if it's needed
            if(!restartControllerService()) {
                retrySleep();
                continue;
            }

            longSleep();
        }
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

    //TODO
    private boolean downloadDrivers(List<String> drivers) {
        for (String driver : drivers) {
            // This is to emulating downloading by just creating a local file
            File driverFile = new File (DRIVER_DIR + "/" + driver);
            try {
                driverFile.createNewFile();
                needRestartControllerService = true;
                log.info("Driver {} has been downloaded", driver);
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

    private void initializeLocalAndTargetInfo() {
        localDrivers = getLocalDrivers();
        log.info("Local drivers initialized: {}", Arrays.toString(localDrivers.toArray()));
        DriverInfo targetDriversInfo = coordinator.getCoordinatorClient().getTargetInfo(DriverInfo.class);
        if (targetDriversInfo == null) {
            DriverInfo initDriversInfo = new DriverInfo();
            coordinator.getCoordinatorClient().setTargetInfo(initDriversInfo);
            log.info("Created DriverInfo ZNode");
            targetDrivers = initDriversInfo.getDrivers();
        } else {
            targetDrivers = targetDriversInfo.getDrivers();
        }
        log.info("Target drivers initialized: {}", Arrays.toString(targetDrivers.toArray()));
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

    private void addDriverInfoListener() {
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

    class DriverInfoListener implements NodeListener {
        @Override
        public String getPath() {
            return String.format("/config/%s/%s", DriverInfo.KIND, DriverInfo.ID);
        }

        @Override
        public void connectionStateChanged(State state) {
            log.info("Driver info connection state changed to {}", state);
            if (state != State.CONNECTED) {
                return;
            }
            log.info("Curator (re)connected. Waking up the driver manager...");
            wakeup();
        }

        @Override
        public void nodeChanged() throws Exception {
            log.info("Driver info changed. Waking up the driver manager...");
            wakeup();
        }
    }
}
