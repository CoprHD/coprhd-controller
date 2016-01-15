/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.licensing;

import static com.emc.storageos.coordinator.client.service.LicenseInfo.TARGET_PROPERTY_ID;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.coordinator.client.service.LicenseInfo;
import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.exceptions.DecodingException;

/**
 * Represent the list of license features stored in coordinator.
 * Starting vipr 1.1, all license features' info are stored in coordinator together in one data node.
 * This will facilitate upgrade and make sure all existing license features to be removed when
 * importing a new license file.
 */
public class LicenseInfoListExt implements CoordinatorSerializable {

    private List<LicenseInfoExt> _licenseList = new ArrayList<LicenseInfoExt>();

    // default constructor
    public LicenseInfoListExt() {
    }

    public LicenseInfoListExt(List<LicenseInfoExt> licenseList) {
        setLicenseList(licenseList);
    }

    public List<LicenseInfoExt> getLicenseList() {
        return _licenseList;
    }

    public void setLicenseList(List<LicenseInfoExt> _licenseList) {
        this._licenseList = _licenseList;
    }

    // helper function for updating license info for a specific license type
    public void updateLicense(LicenseInfoExt licenseInfo) {
        for (int i = 0; i < _licenseList.size(); i++) {
            if (_licenseList.get(i).getModelId().compareTo(licenseInfo.getModelId()) == 0) {
                _licenseList.set(i, licenseInfo);
            }
        }
    }

    @Override
    public String encodeAsString() {
        final StringBuilder sb = new StringBuilder();
        for (LicenseInfoExt licenseInfo : getLicenseList()) {
            sb.append(licenseInfo.encodeAsString());
            sb.append(LicenseInfo.LICENSE_SEPARATOR);
        }
        return sb.toString();
    }

    @Override
    public LicenseInfoListExt decodeFromString(String infoStr) throws DecodingException
    {
        if (infoStr == null) {
            return null;
        }
        String[] licenseStrs = infoStr.split(LicenseInfo.LICENSE_SEPARATOR);
        for (String licenseStr : licenseStrs) {
            getLicenseList().add((new LicenseInfoExt().decodeFromString(licenseStr)));
        }
        return this;
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(TARGET_PROPERTY_ID,
                LicenseInfo.LICENSE_INFO_TARGET_PROPERTY, LicenseInfo.LICENSE_INFO);
    }
}
