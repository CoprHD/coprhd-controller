/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

/**
 * Facade class for processing sending SYR events including update coordinator when necessary
 * Note: Should not make methods in this class as synchronized. Otherwise it will cause
 * deadlocks of the caller of these methods because the monitor lock cannot be released 
 * (out of synchronized method) until the shared targetVersionLock of CoordinatorClientExt
 * is released. Synchronization of sending SYR events is covered in callEMCHome() of
 * SendEvent class
 * 
 */
@Component
public class CallHomeEventsFacade {
    private static final Logger _log = LoggerFactory.getLogger(CallHomeEventsFacade.class);
    @Autowired
    private LicenseManager _licenseManager;
    @Autowired
    protected LogSvcPropertiesLoader logSvcPropertiesLoader;
    @Autowired
    private ServiceImpl serviceInfo;
    @Autowired
    private AuditLogManager auditMgr;
    @Autowired
    private CoordinatorClientExt coordinator;
    /**
     * Sends Object Registration Event to SYR.
     * 
     * 
     * @param mediaType
     * @throws CoordinatorClientException
     * @throws Exception
     */
    public void sendRegistrationEvent(LicenseInfoExt licenseInfo, MediaType mediaType) {

        if(licenseInfo.isTrialLicense()) {
            _log.info("CallHomeEventsFacade will not send registration event for trial license of type {}",
                    licenseInfo.getLicenseType().toString());
            return;
        }
        
        // update coordinator with a new latest registration date..
        licenseInfo.setLastRegistrationEventDate(formatCurrentDate());

        // update coordinator with registration information.
        _log.info("CallHomeEventsFacade::sendRegistrationEvent updating coordinator with {} registration data",
                licenseInfo.getLicenseType().toString());
        _licenseManager.updateCoordinatorWithLicenseInfo(licenseInfo);

        _log.info("CallHomeEventsFacade::sendRegistrationEvent sending {} registration to SYR", licenseInfo.getLicenseType());
        // send registration data to SYR
        SendRegistrationEvent sendRegistrationEvent = new SendRegistrationEvent
                (serviceInfo, logSvcPropertiesLoader, mediaType, licenseInfo, coordinator);
        sendRegistrationEvent.callEMCHome();              
    }
    
    /**
     * Sends Controller Heartbeat Event to SYR.
     *
     * @param licenseInfo
     * @param mediaType
     * @throws CoordinatorClientException
     * @throws Exception
     */
    public void sendHeartBeatEvent(LicenseInfoExt licenseInfo, MediaType mediaType) {
        if(licenseInfo.isTrialLicense()) {
            _log.info("CallHomeEventsFacade will not send heartbeat event for trial license of type {}",
                    licenseInfo.getLicenseType().toString());
            return;
        }
                  
        // update coordinator with a new latest heartbeat date..
        licenseInfo.setLastHeartbeatEventDate(formatCurrentDate());
       
        _log.info("CallHomeEventsFacade::sendHeartBeatEvent updating coordinator with {} heartbeat data",
                licenseInfo.getLicenseType().toString());
        _licenseManager.updateCoordinatorWithLicenseInfo(licenseInfo);

        // send heartbeat data to SYR
        _log.info("CallHomeEventsFacade::sendHeartBeatEvent sending {} heartbeat to SYR", 
               licenseInfo.getLicenseType());
        SendHeartbeatEvent sendHeartbeatEvent = new SendHeartbeatEvent
                (serviceInfo, logSvcPropertiesLoader, mediaType, licenseInfo, coordinator);
        sendHeartbeatEvent.callEMCHome();
    }
  
    /**
     * Sends Controller License Expiration Event to SYR.
     *
     * @param licenseInfo
     * @param mediaType
     * @throws CoordinatorClientException
     * @throws Exception
     */
    public void sendExpirationEvent(LicenseInfoExt licenseInfo, MediaType mediaType) 
            throws CoordinatorClientException, Exception {
        if(licenseInfo.isTrialLicense()) {
            _log.info("CallHomeEventsFacade will not send expiration event for trial license of type {}",
                    licenseInfo.getLicenseType().toString());
            return;
        }
        
        // update coordinator with a new latest license expiration date..
        licenseInfo.setLastLicenseExpirationDateEventDate(formatCurrentDate());
        
        // update coordinator with a new license expiration date
        _log.info("CallHomeEventsFacade::sendExpirationEvent updating coordinator with {} license expiration data",
                licenseInfo.getLicenseType().toString());
        _licenseManager.updateCoordinatorWithLicenseInfo(licenseInfo);

        // send expiration data to SYR
        _log.info("CallHomeEventsFacade::sendExpirationEvent sending {} license expiration to SYR", 
                licenseInfo.getLicenseType());
        SendExpirationEvent sendExpirationEvent = new SendExpirationEvent
                (serviceInfo, logSvcPropertiesLoader, mediaType, licenseInfo, coordinator);
        sendExpirationEvent.callEMCHome();
        
        auditMgr.recordAuditLog(null, null, "callHome", 
                OperationTypeEnum.SEND_LICENSE_EXPIRED,                
                System.currentTimeMillis(),
                AuditLogManager.AUDITLOG_SUCCESS,
                null, 
                licenseInfo.getLicenseType().toString(), 
                licenseInfo.getProductId(), licenseInfo.getModelId(), 
                licenseInfo.getExpirationDate());
    }
   
    /**
     * Sends Object License Expiration Event to SYR.
     * 
     * @param licenseInfo
     * @param mediaType
     * @throws CoordinatorClientException
     * @throws Exception
     */
    public void sendCapacityExceededEvent(LicenseInfoExt licenseInfo, MediaType mediaType)
            throws CoordinatorClientException, Exception {
        if(licenseInfo.isTrialLicense()) {
            _log.info("CallHomeEventsFacade will not send capacity exceeded event for trial license of type {}",
                    licenseInfo.getLicenseType().toString());
            return;
        }

        // update coordinator with a new latest license expiration date..
        licenseInfo.setLastCapacityExceededEventDate(formatCurrentDate());
        
        // update coordinator with a new capacity exceeded date.
        _log.info("CallHomeEventsFacade::sendCapacityExceededEvent updating coordinator with {} capacity exceeded data",
                licenseInfo.getLicenseType().toString());
        _licenseManager.updateCoordinatorWithLicenseInfo(licenseInfo);

        // send capacity exceeded data to SYR
        _log.info("CallHomeEventsFacade::sendCapacityExceededEvent sending {} capacity exceeded data to SYR", 
                licenseInfo.getLicenseType());
        SendCapacityExceededEvent sendCapacityExceededEvent = new SendCapacityExceededEvent
                (serviceInfo, logSvcPropertiesLoader, mediaType, licenseInfo, coordinator);
        sendCapacityExceededEvent.callEMCHome();
        
        auditMgr.recordAuditLog(null, null, "callHome", 
                OperationTypeEnum.SEND_CAPACITY_EXCEEDED,                
                System.currentTimeMillis(),
                AuditLogManager.AUDITLOG_SUCCESS,
                null, 
                licenseInfo.getLicenseType().toString(), 
                licenseInfo.getProductId(), licenseInfo.getModelId(), 
                licenseInfo.getStorageCapacity());
    }

    /**
     * Format current date.
     * @return String
     */
    public static String formatCurrentDate() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(CallHomeConstants.SERIALIZE_DATE_FORMAT);
        return sdf.format(calendar.getTime());
    }
}