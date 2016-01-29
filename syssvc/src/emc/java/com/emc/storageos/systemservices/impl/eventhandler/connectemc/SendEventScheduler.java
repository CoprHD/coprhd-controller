/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoListExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseManager;

@Service
public class SendEventScheduler implements Runnable {

    private CallHomeEventManager _callHomeEventManager;
    private LicenseManager _licenseManager;
    private CallHomeEventsFacade _callHomeEventsFacade;
    private static final Logger _log = LoggerFactory.getLogger(SendEventScheduler.class);

    /**
     * Sets up the scheduler.
     */
    public SendEventScheduler() {

        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        service.scheduleAtFixedRate(this, CallHomeConstants.SERVICE_START_LAG, CallHomeConstants.LAG_BETWEEN_RUNS, TimeUnit.SECONDS);
    }

    /**
     * 
     * Method which sends the Heartbeat event and also verifies if the license
     * has expired. If so, it will send a license expiration event.
     * 
     * Runs once per day.
     */
    @Override
    public void run() {

        _log.info("SendEventScheduler::run() start");

        // if customer is not configured to send callhome events to SYR, do not continue.
        try {
            if (!_callHomeEventManager.canSendEvent()) {
                return;
            }

            int totalSleep = 0;
            while (true) {

                if (_licenseManager.getTargetInfoLock()) {

                    // verify if there are any eligible callhome events for the license type to be sent.
                    try {
                        performSendEvents();
                    } catch (Exception e) {
                        _log.error("Exception sending events to SYR: {} ", e);
                        break;
                    } finally {
                        _licenseManager.releaseTargetVersionLock();
                    }
                    break;
                }
                else {
                    _log.info("Cannot acquire TargetLock. Sleeping 5s before retrying...");
                    if (totalSleep >= CallHomeConstants.MAX_LOCK_WAIT_TIME_MS) {
                        _log.warn("Cannot acquire TargetLock in {}ms. So quitting SendEventScheduler",
                                CallHomeConstants.MAX_LOCK_WAIT_TIME_MS);
                        break;
                    }
                    Thread.sleep(CallHomeConstants.LOCK_WAIT_TIME_MS); // 5s
                    totalSleep += CallHomeConstants.LOCK_WAIT_TIME_MS;
                }
            }
        } catch (APIException i) {
            _log.info("ConnectEMC is not configured. Ending SendEventScheduler.");
        } catch (Exception e) {
            _log.error("Exception while running event scheduler: {} ", e);
        }
        _log.info("SendEventScheduler::run() end");
    }

    /**
     * Send eligible events based on their license type (CONTROLLER or OBJECT)
     * 
     * @param licenseType
     */
    private void performSendEvents() {
        LicenseInfoListExt licenseList = null;
        try {
            licenseList = _licenseManager.getLicenseInfoListFromCoordinator();
        } catch (Exception e) {
            _log.error("SendEventScheduler::performSendEvents(): getLicenseInfoListFromCoordinator exception: {}", e.getMessage());
            return;
        }
        if (licenseList != null) {
            for (LicenseInfoExt licenseInfo : licenseList.getLicenseList()) {
                _log.info("SendEventScheduler::run() getting LicenseInfoExt for {}", licenseInfo.getLicenseType());
                try {
                    if (!licenseInfo.isTrialLicense()) {
                        if (_callHomeEventManager.doSendHeartBeat(licenseInfo)) {
                            sendHeartbeat(licenseInfo);
                        }

                        if (_licenseManager.isLicenseExpired(licenseInfo) &&
                                _callHomeEventManager.doSendLicenseExpiration(licenseInfo)) {
                            sendLicenseExpiration(licenseInfo);
                        }

                        // In case user has not import 2.0 license after upgrade, Capacity exceeded
                        // event from existing 1.0 license of HDFS/OBJECT/OBJECTHDFS should be blocked.
                        LicenseType licType = licenseInfo.getLicenseType();
                        if (licType == LicenseType.OBJECTHDFS ||
                                licType == LicenseType.OBJECT || licType == LicenseType.HDFS) {
                            continue;
                        }

                        /* TODO: enable compliance check after Yoda
                        if (licenseInfo.hasStorageCapacity() &&
                                _licenseManager.isCapacityExceeded(licenseInfo) &&
                                _callHomeEventManager.doSendCapicityExceeded(licenseInfo)) {
                            sendCapacityExceeded(licenseInfo);
                        }
                        */
                    }
                } catch (Exception e) {
                    _log.error("SendEventScheduler::performSendEvents(): Exception: {}", e.getMessage());
                    continue;
                }
            }
        }

    }

    /**
     * Send Registration event to SYR.
     */
    private void sendRegistration(LicenseInfoExt licenseInfo)
            throws Exception {
        _log.info("SendEventScheduler::sendRegistration() for {}",
                licenseInfo.getLicenseType());
        _callHomeEventsFacade.sendRegistrationEvent(licenseInfo, MediaType.APPLICATION_XML_TYPE);
    }

    /**
     * Send Heartbeat event to SYR.
     */
    private void sendHeartbeat(LicenseInfoExt licenseInfo)
            throws Exception {
        _log.info("SendEventScheduler::sendHeartbeat() for {}",
                licenseInfo.getLicenseType());
        _callHomeEventsFacade.sendHeartBeatEvent(licenseInfo, MediaType.APPLICATION_XML_TYPE);
    }

    /**
     * Send License Expiration event to SYR.
     */
    private void sendLicenseExpiration(LicenseInfoExt licenseInfo)
            throws Exception {
        _log.info("SendEventScheduler::validateAndSendLicenseExpiration() for {}",
                licenseInfo.getLicenseType());
        _callHomeEventsFacade.sendExpirationEvent(licenseInfo, MediaType.APPLICATION_XML_TYPE);
    }

    /**
     * Send Capacity Exceeded event to SYR
     * 
     * @param licenseInfo
     * @throws Exception
     */
    private void sendCapacityExceeded(LicenseInfoExt licenseInfo)
            throws Exception {
        _log.info("SendEventScheduler::validateAndSendCapacityExceeded() for {}",
                licenseInfo.getLicenseType());
        _callHomeEventsFacade.sendCapacityExceededEvent(licenseInfo, MediaType.APPLICATION_XML_TYPE);
    }

    /**
     * Set the CallHomeEventManager
     * 
     * @param callHomeEventManager
     */
    @Autowired
    public void setCallHomeEventManager(CallHomeEventManager callHomeEventManager) {
        _callHomeEventManager = callHomeEventManager;
    }

    /**
     * Set the LicenseManager
     */
    @Autowired
    public void setLicenseManager(LicenseManager licenseManager) {
        _licenseManager = licenseManager;
    }

    /**
     * Set the CallHomeEventsFacade.
     * 
     * @param callHomeEventsFacade
     */
    @Autowired
    public void setCallHomeEventsFacace(CallHomeEventsFacade callHomeEventsFacade) {
        _callHomeEventsFacade = callHomeEventsFacade;
    }
}