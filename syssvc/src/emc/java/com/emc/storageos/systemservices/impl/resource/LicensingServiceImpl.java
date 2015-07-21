/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.coordinator.client.service.LicenseInfo;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.licensing.LicenseConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.cams.elm.exception.ELMLicenseException;

import com.emc.storageos.systemservices.impl.licensing.LicenseManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;

public class LicensingServiceImpl extends BaseLogSvcResource implements LicensingService{
    // Logger reference.
    private static final Logger _log = LoggerFactory.getLogger(LicensingServiceImpl.class);
    // Spring Injected
    private LicenseManager _licenseManager;
    private static final String EVENT_SERVICE_TYPE = "license";

    @Autowired
    private AuditLogManager _auditMgr;
    @Autowired
    private CallHomeService _callHomeService;
    
    /**
     * Default constructor.
     */
    public LicensingServiceImpl() {
    }

    @Override
    public License getLicense() throws Exception {
        _log.info("Received GET /license request");
        // Changing invalid 01/01/12006 license expiration date to null
        License license = _licenseManager.getLicense();
        if(license != null && license.getLicenseFeatures() != null) {
            for(LicenseFeature feature : license.getLicenseFeatures()) {
                if(LicenseConstants.LICENSE_EXPIRATION_DATE.equals(feature
                        .getDateExpires())) {
                    feature.setDateExpires(null);
                }
                if(feature.getStorageCapacity().equals(LicenseInfo.VALUE_NOT_SET)) {
                    feature.setStorageCapacity(null);
                }
            }
        }
        return license;
    }

    @Override
    public Response postLicense(License license) throws Exception {
        _log.info("Received POST /license request");
              
        if(license.getLicenseText() == null || license.getLicenseText().isEmpty()) {
            throw APIException.badRequests.licenseTextIsEmpty();
        }

        // check if it is trial license, if so only check if it is stand alone deployment
        boolean isTrialLicense = false;
        try {
            isTrialLicense = _licenseManager.isTrialLicense(license);
        }
        catch(Exception e) {
            throw APIException.badRequests.licenseIsNotValid(e.getMessage());
        }
        if(isTrialLicense && !_licenseManager.isTrialPackage()) {
            _log.error("Trial license can only be applied to single node ViPR appliance");
            throw APIException.badRequests.licenseIsNotValid("Trial license can only be applied to single node ViPR appliance");
        }
        
        try {
            _licenseManager.addLicense(license);
            _log.info("Done adding license");
        } catch (ELMLicenseException e) {
            throw APIException.badRequests.licenseIsNotValid(e.getMessage());
        }

        try{
            ((CallHomeServiceImpl)_callHomeService).internalSendRegistrationEvent();
        } catch (Exception e) {
            _log.warn("Error occurred while sending registration event. {}", e);
        }
                
        auditLicense(OperationTypeEnum.ADD_LICENSE,
                AuditLogManager.AUDITLOG_SUCCESS, null);
        
        return Response.ok().build();
    }

    public void setLicenseManager(LicenseManager licenseManager) {
        _licenseManager = licenseManager;
    }
    
    /**
     * Record audit log for license service
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param description Description for the AuditLog
     * @param descparams Description parameters
     */
    public void auditLicense(OperationTypeEnum auditType,
                                              String operationalStatus,
                                              String description,
                                              Object... descparams)  {
    
        _auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus,
                description, 
                descparams);       
    }   
}
