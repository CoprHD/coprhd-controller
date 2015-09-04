/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.licensing;

import com.emc.storageos.model.vpool.ManagedResourcesCapacity;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
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
