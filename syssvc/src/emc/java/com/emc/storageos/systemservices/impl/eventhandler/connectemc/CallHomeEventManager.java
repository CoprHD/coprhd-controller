/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseManager;

@Component
public class CallHomeEventManager {

    private LicenseManager _licenseManager;
    // Common date format when storing in coordinator service.
    public static final String SERIALIZE_DATE_FORMAT = "MM/dd/yyyy";
    // used to check if the system.connectemc.transport is configured for SYR.
    // If it is "None", SYR is turned off.
    private static final String NONE = "None";
    private static final Logger _log = LoggerFactory.getLogger(CallHomeEventManager.class);
    @Autowired
    private CoordinatorClientExt coordinatorClientExt;

    /**
     * Determines whether it's time to send a new heartbeat event.
     * 
     * @return boolean
     * @throws ParseException
     * @throws Exception
     */
    public boolean doSendHeartBeat(LicenseInfoExt licenseInfo)
            throws ParseException {

        if (licenseInfo == null) {
            return true;
        }

        String lastHeartbeatDate = licenseInfo.getLastHeartbeatEventDate();
        if (lastHeartbeatDate == null ||
                lastHeartbeatDate.isEmpty() ||
                lastHeartbeatDate.equals(CallHomeConstants.VALUE_NOT_SET)) {
            _log.info("There is no previous heartbeat event sent.");
            return true;
        }
        _log.info("Previous heartbeat event was sent on: {}", lastHeartbeatDate);

        // compare today's date to the date stored in the coordinator service.
        // If it's >= than
        // the HEARTBEART_EVENT_THRESHOLD, return true.
        SimpleDateFormat storedDate = new SimpleDateFormat(SERIALIZE_DATE_FORMAT);
        Date zkDate = storedDate.parse(lastHeartbeatDate);
        Date today = Calendar.getInstance().getTime();
        int days = Days.daysBetween(new DateTime(zkDate), new DateTime(today)).getDays();
        if (days > (CallHomeConstants.HEARTBEART_EVENT_THRESHOLD - 1)) {
            _log.info("It's been {} days since last heartbeat event was sent. Sending another one now. ", days);
            return true;
        }
        _log.info("Heartbeat event was sent {} days back. Will send another one in {} days.",
                days, (CallHomeConstants.HEARTBEART_EVENT_THRESHOLD - days));
        return false;
    }

    /**
     * Determines whether a registration event has already been sent.
     * 
     * @return boolean
     * @throws Exception
     */
    public boolean doSendRegistration(LicenseInfoExt licenseInfo)
            throws Exception {

        if (licenseInfo == null) {
            return true;
        }

        String registrationDate = licenseInfo.getLastRegistrationEventDate();
        if (registrationDate == null ||
                registrationDate.isEmpty() ||
                registrationDate.equals(CallHomeConstants.VALUE_NOT_SET)) {
            _log.info("There is no previous registration event sent.");
            return true;
        }
        _log.info("Previous registration event was sent on: {}", registrationDate);

        // compare today's date to the date stored in the coordinator service.
        // If it's >= than
        // the HEARTBEART_EVENT_THRESHOLD, return true.
        SimpleDateFormat storedDate = new SimpleDateFormat(SERIALIZE_DATE_FORMAT);
        Date zkDate = storedDate.parse(registrationDate);
        Date today = Calendar.getInstance().getTime();
        int days = Days.daysBetween(new DateTime(zkDate), new DateTime(today)).getDays();
        if (days > (CallHomeConstants.REGISTRATION_EVENT_THRESHOLD - 1)) {
            _log.info("It's been {} days since last registration event was sent. Sending another one now. ", days);
            return true;
        }
        _log.info("Registration event was sent {} days back. Will send another one in {} days.",
                days, (CallHomeConstants.REGISTRATION_EVENT_THRESHOLD - days));
        return false;

    }

    /**
     * Determines whether it's time to send a new capacity exceeded event.
     * 
     * @return boolean
     * @throws Exception
     */
    public boolean doSendCapicityExceeded(LicenseInfoExt licenseInfo)
            throws Exception {

        if (licenseInfo == null) {
            return true;
        }

        String lastCapacityExceedDate = licenseInfo.getLastCapacityExceededEventDate();
        if (lastCapacityExceedDate == null
                || lastCapacityExceedDate.isEmpty()
                || lastCapacityExceedDate.equals(CallHomeConstants.VALUE_NOT_SET)) {
            _log.info("There is no previous capacity-exceeded event sent.");
            return true;
        }
        _log.info("Previous capacity-exceeded event was sent on: {}",
                lastCapacityExceedDate);

        // compare today's date to the date stored in zookeeper. If it's >= than
        // the LICENSE_EXPIRATION_EVENT_THRESHOLD, return true.
        SimpleDateFormat storedDate = new SimpleDateFormat(SERIALIZE_DATE_FORMAT);
        Date zkDate = storedDate.parse(lastCapacityExceedDate);
        Date today = Calendar.getInstance().getTime();
        int days = Days.daysBetween(new DateTime(zkDate), new DateTime(today)).getDays();
        if (days > (CallHomeConstants.CAPACITY_EXCEEDED_EVENT_THRESHOLD - 1)) {
            _log.info("It's been {} days since last capacity-exceeded event was sent. Sending another one now. ", days);
            return true;
        }
        _log.info("Capacity-exceeded event was sent {} days back. Will send another one in {} days.",
                days, (CallHomeConstants.CAPACITY_EXCEEDED_EVENT_THRESHOLD - days));
        return false;
    }

    /**
     * Returns true if connectemc is configured and it is control node.
     */
    public boolean canSendEvent() {
        try {
            validateSendEvent();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The method will determine if the client is eligible for callhome events.
     * The client must have a Controller license and configured for ConnectEMC
     * transport.
     * 
     * @return
     */
    public boolean isConnectEMCConfigured() {
        try {
            // Validate if SYR is enabled for customer.
            String transport = coordinatorClientExt.getPropertyInfo().getProperties().get(PropertyInfoExt.CONNECTEMC_TRANSPORT);
            if (transport == null || transport.isEmpty() ||
                    transport.equalsIgnoreCase(NONE)) {
                _log.warn("ConnectEMC is not configured. {} property is set to {}",
                        PropertyInfoExt.CONNECTEMC_TRANSPORT, transport);
                return false;
            }
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError(PropertyInfoExt.CONNECTEMC_TRANSPORT, "local repository", e);
        }
        return true;
    }

    /**
     * Validates if connect emc is configured and if it is control node. Else throws
     * exception.
     */
    public void validateSendEvent() {
        if (!coordinatorClientExt.isControlNode()) {
            throw APIException.badRequests.eventsNotAllowedOnNonControlNode();
        }
        if (!isConnectEMCConfigured()) {
            throw APIException.badRequests.connectEMCNotConfigured();
        }
    }

    /**
     * Determines whether it's time to send a new license expiration event.
     * 
     * @return boolean
     * @throws Exception
     */
    public boolean doSendLicenseExpiration(LicenseInfoExt licenseInfo)
            throws Exception {

        if (licenseInfo == null) {
            return true;
        }

        String lastExpirationDate = licenseInfo.getLastLicenseExpirationDateEventDate();
        if (lastExpirationDate == null ||
                lastExpirationDate.isEmpty() ||
                lastExpirationDate.equals(CallHomeConstants.VALUE_NOT_SET)) {
            _log.info("There is no previous license-expiration event sent.");
            return true;
        }
        _log.info("Previous license-expiration event was sent on: {}",
                lastExpirationDate);

        // compare today's date to the date stored in zookeeper. If it's >= than
        // the LICENSE_EXPIRATION_EVENT_THRESHOLD, return true.
        SimpleDateFormat storedDate = new SimpleDateFormat(SERIALIZE_DATE_FORMAT);
        Date zkDate = storedDate.parse(lastExpirationDate);
        Date today = Calendar.getInstance().getTime();
        int days = Days.daysBetween(new DateTime(zkDate), new DateTime(today)).getDays();
        if (days > (CallHomeConstants.LICENSE_EXPIRATION_EVENT_THRESHOLD - 1)) {
            _log.info("It's been {} days since last license-expiration event was sent. Sending another one now. ", days);
            return true;
        }
        _log.info("License-expired event was sent {} days back. Will send another one in {} days.",
                days, (CallHomeConstants.LICENSE_EXPIRATION_EVENT_THRESHOLD - days));
        return false;
    }

    @Autowired
    public void setLicenseManager(LicenseManager licenseManager) {
        _licenseManager = licenseManager;
    }
}
