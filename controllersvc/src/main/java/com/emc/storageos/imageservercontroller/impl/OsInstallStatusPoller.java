/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller.impl;

import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_FAILURE_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_SUCCESS_DIR;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.imageservercontroller.ImageServerConf;
import com.emc.storageos.networkcontroller.SSHSession;

public class OsInstallStatusPoller implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OsInstallStatusPoller.class);

    private boolean beingPolled = false;
    private Long POLLING_INTERVAL = 60000L;
    private Map<String, OsInstallStatus> sessionStatusMap = Collections
            .synchronizedMap(new HashMap<String, OsInstallStatus>());
    private ImageServerConf imageServerConf;

    public static enum OsInstallStatus {
        SUCCESS, FAILURE
    }

    public void setImageServerConf(ImageServerConf imgServConf) {
        this.imageServerConf = imgServConf;
    }

    /**
     * Getting session status from cached map.
     * 
     * @param sessionId
     * @return
     */
    public OsInstallStatus getOsInstallStatus(String sessionId) {
        OsInstallStatus status = sessionStatusMap.remove(sessionId);
        if (status == null) {
            beingPolled = true;
        }
        log.info("polling session {}, returning status {}", sessionId, status);
        return status;
    }

    /**
     * Start polling thread.
     */
    public void startPolling() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(this);
    }

    private void pollImageServer() {
        log.debug("pollImageServer");
        ImageServerDialog d = null;
        String[] successes = null;
        String[] failures = null;
        try {
            SSHSession session = new SSHSession();
            session.connect(imageServerConf.getImageServerIp(), imageServerConf.getSshPort(),
                    imageServerConf.getImageServerUser(), imageServerConf.getImageServerPassword());
            d = new ImageServerDialog(session, imageServerConf.getSshTimeoutMs());
            d.init();

            successes = d.lsDir(imageServerConf.getTftpbootDir() + HTTP_SUCCESS_DIR);
            failures = d.lsDir(imageServerConf.getTftpbootDir() + HTTP_FAILURE_DIR);

            log.info("successes: {}; failures: {}", Arrays.asList(successes), Arrays.asList(failures));

            if (successes != null) {
                for (String sessionId : successes) {
                    sessionStatusMap.put(sessionId, OsInstallStatus.SUCCESS);
                }
            }
            if (failures != null) {
                for (String sessionId : failures) {
                    sessionStatusMap.put(sessionId, OsInstallStatus.FAILURE);
                }
            }
        } catch (Exception e) {
            log.error("Unexpected exception when polling image server: " + e.getMessage(), e);
        } finally {
            try {
                if (d != null && d.isConnected())
                    d.close();
            } catch (Exception e) {
                log.error("failed to close image server dialog", e);
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            if (beingPolled) {
                pollImageServer();
                beingPolled = false;
            }
            try {
                log.debug("sleep for {} ms", POLLING_INTERVAL);
                Thread.sleep(POLLING_INTERVAL);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
