/*
 * Copyright (c) 2013-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.licensing;

import com.emc.storageos.model.vpool.ManagedResourcesCapacity;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.vipr.model.sys.licensing.License;

public interface LicenseManager {

    public static final String PERMANENT_LICENSE = "permanent";
    public static final String EXPIRE_DATE_FORMAT = "MM/dd/yyyy";

    public void addLicense(License license)
            throws Exception;

    public boolean isTrialPackage();

    public boolean isTrialLicense(License license) throws Exception;

    public License getLicense() throws Exception;

    public boolean isProductLicensed(LicenseType licenseType);

    public LicenseInfoListExt getLicenseInfoListFromCoordinator();

    public LicenseInfoExt getLicenseInfoFromCoordinator(LicenseType licenseType);

    public LicenseTextInfo getLicenseTextFromCoordinator() throws Exception;

    public void updateCoordinatorWithLicenseText(License license)
            throws CoordinatorClientException;

    public void updateCoordinatorWithLicenseInfo(LicenseInfoExt licenseInfo)
            throws CoordinatorClientException;

    public boolean isLicenseExpired(LicenseInfoExt licenseInfo);

    public boolean isCapacityExceeded(LicenseInfoExt licenseInfo);

    public ManagedResourcesCapacity getControllerCapacity() throws InternalServerErrorException;

    public boolean getTargetInfoLock();

    public void releaseTargetVersionLock();
}
