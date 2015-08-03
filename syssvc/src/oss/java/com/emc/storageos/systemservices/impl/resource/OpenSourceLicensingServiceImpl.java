/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import java.lang.Override;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.LicenseInfo;
import com.emc.storageos.systemservices.impl.licensing.LicenseConstants;
import com.emc.storageos.systemservices.impl.licensing.LicenseManager;
import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;

public class OpenSourceLicensingServiceImpl extends BaseLogSvcResource implements LicensingService{
    // Logger reference.
    private static final Logger _log = LoggerFactory.getLogger(OpenSourceLicensingServiceImpl.class);
    // Spring Injected

    @Autowired
    private LicenseManager _licenseManager;
    
    /**
     * Default constructor.
     */
    public OpenSourceLicensingServiceImpl() {
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
        return Response.status(501).build();
    }
}
