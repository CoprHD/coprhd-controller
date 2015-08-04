/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.licensing;

import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;

import com.emc.storageos.coordinator.exceptions.DecodingException;

public class LicenseTextInfo implements
        CoordinatorSerializable {

    // raw license the way it was provided to customer.
    private String _licenseText;
    public static final String LICENSE_TEXT_TARGET_PROPERTY_ID = "global";
    // coordinator values for the controller license.
    public static final String LICENSE_TEXT_INFO = "licenseTextInfo";
    public static final String LICENSE_TEXT_INFO_TARGET_PROPERTY = "licenseTextInfoProperty";
    public static final String LICENSE_TEXT = "licenseText";

    @Override
    public String encodeAsString() {

        // License Expiration Date
        return (_licenseText != null ? _licenseText : "NA");

    }

    @Override
    public LicenseTextInfo decodeFromString(String infoStr)
            throws DecodingException {

        LicenseTextInfo licenseTextInfo = new LicenseTextInfo();

        if (infoStr != null && !infoStr.isEmpty()) {
            licenseTextInfo.setLicenseText(infoStr);
        }

        return licenseTextInfo;
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(LICENSE_TEXT_TARGET_PROPERTY_ID,
                LICENSE_TEXT_INFO_TARGET_PROPERTY, LICENSE_TEXT_INFO);
    }

    public String getLicenseText() {
        return _licenseText;
    }

    public void setLicenseText(String licenseText) {
        this._licenseText = licenseText;
    }
}
