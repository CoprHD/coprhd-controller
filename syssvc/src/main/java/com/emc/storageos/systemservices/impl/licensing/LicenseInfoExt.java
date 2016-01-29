/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.licensing;

import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;
import com.emc.storageos.coordinator.client.service.LicenseInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.coordinator.exceptions.DecodingException;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;

import com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants;

public class LicenseInfoExt extends LicenseInfo implements CoordinatorSerializable {

    // Date license was issued.
    private String _issuedDate;
    // storage capacity per license.
    private String _storageCapacity;
    // SN or SWID from license
    private String _productId;
    // this is the license feature..what you're buying.
    private String _modelId;
    // denotes is serial number is derived from SWID or LAC.
    private String _licenseTypeIndicator;
    // license version.
    private String _version;
    // Notice section of license.
    private String _notice;
    // if the license is a trial license
    private Boolean _trialLicense = false;
    // Dates of last transmissions to SYR. This is stored in coordinator service and is
    // required to prevent multiple transmissions.
    private String _lastLicenseExpirationDateEventDate;
    private String _lastHeartbeatEventDate;
    private String _lastRegistrationEventDate;
    private String _lastCapacityExceededEventDate;
    // values which are stored in the coordinator service.
    public static final String EXPIRATION_DATE = "expirationDate";
    public static final String ISSUED_DATE = "issuedDate";
    public static final String STORAGE_CAPACITY = "storageCapacity";
    public static final String PRODUCT_ID = "productId";
    public static final String MODEL_ID = "modelId";
    public static final String LICENSE_TYPE_INDICATOR = "licenseTypeIndicator";
    public static final String VERSION = "version";
    public static final String NOTICE = "notice";
    public static final String IS_TRIAL_LICENSE = "isTrialLicense";
    public static final String LAST_REGISTRATION_EVENT_DATE = "lastRegistrationEventDate";
    public static final String LAST_HEARBEAT_EVENT_DATE = "lastHeartbeatEventDate";
    public static final String LAST_EXPIRATION_EVENT_DATE = "lastExpirationEventDate";
    public static final String LAST_CAPACITY_EXCEEDED_EVENT_DATE =
            "lastCapacityExceededEventDate";

    @Override
    public String encodeAsString() {

        final StringBuilder s = new StringBuilder();

        // License Type
        s.append(LICENSE_TYPE);
        s.append(ENCODING_EQUAL);
        s.append(_licenseType.name());
        s.append(ENCODING_SEPARATOR);

        // License Expiration Date
        s.append(EXPIRATION_DATE);
        s.append(ENCODING_EQUAL);
        s.append(_expirationDate != null ? _expirationDate : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // License Issued Date
        s.append(ISSUED_DATE);
        s.append(ENCODING_EQUAL);
        s.append(_issuedDate != null ? _issuedDate : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // Storage Capacity
        s.append(STORAGE_CAPACITY);
        s.append(ENCODING_EQUAL);
        s.append(_storageCapacity != null ? _storageCapacity : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // Product Id (i.e. Serial Number or SWID from the license file)
        s.append(PRODUCT_ID);
        s.append(ENCODING_EQUAL);
        s.append(_productId != null ? _productId : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // Model Id (i.e. feature detail from license.)
        s.append(MODEL_ID);
        s.append(CallHomeConstants.ENCODING_EQUAL);
        s.append(_modelId != null ? _modelId : VALUE_NOT_SET);
        s.append(CallHomeConstants.ENCODING_SEPARATOR);

        // License Type Indicator. Is license using SWID or LAC
        s.append(LICENSE_TYPE_INDICATOR);
        s.append(ENCODING_EQUAL);
        s.append(_licenseTypeIndicator != null ? _licenseTypeIndicator : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // License Version
        s.append(VERSION);
        s.append(ENCODING_EQUAL);
        s.append(_version != null ? _version : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // License Notice.
        s.append(NOTICE);
        s.append(ENCODING_EQUAL);
        s.append(_notice != null ? _notice : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // if it is a trial license
        s.append(IS_TRIAL_LICENSE);
        s.append(ENCODING_EQUAL);
        s.append(_trialLicense.toString());
        s.append(ENCODING_SEPARATOR);

        // Last Registration Event Date to SYR
        s.append(LAST_REGISTRATION_EVENT_DATE);
        s.append(ENCODING_EQUAL);
        s.append(_lastRegistrationEventDate != null ? _lastRegistrationEventDate : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // Last Heartbeat Event Date to SYR
        s.append(LAST_HEARBEAT_EVENT_DATE);
        s.append(ENCODING_EQUAL);
        s.append(_lastHeartbeatEventDate != null ? _lastHeartbeatEventDate : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // Last License Expiration Event to SYR
        s.append(LAST_EXPIRATION_EVENT_DATE);
        s.append(ENCODING_EQUAL);
        s.append(_lastLicenseExpirationDateEventDate != null ? _lastLicenseExpirationDateEventDate : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        // Last Capacity Exceeded Event to SYR
        s.append(LAST_CAPACITY_EXCEEDED_EVENT_DATE);
        s.append(ENCODING_EQUAL);
        s.append(_lastCapacityExceededEventDate != null ? _lastCapacityExceededEventDate : VALUE_NOT_SET);
        s.append(ENCODING_SEPARATOR);

        return s.toString();
    }

    @Override
    public LicenseInfoExt decodeFromString(String infoStr)
            throws DecodingException {

        if (infoStr != null && !infoStr.isEmpty()) {
            for (String licenseProps : infoStr.split(ENCODING_SEPARATOR)) {
                String[] licenseProp = licenseProps.split(ENCODING_EQUAL);
                if (licenseProp.length < 2) {
                    continue;
                }
                if (licenseProp[0].equalsIgnoreCase(LICENSE_TYPE)) {
                    LicenseType licenseType = LicenseType.findByValue(licenseProp[1]);
                    this.setLicenseType(licenseType);
                }
                if (licenseProp[0].equalsIgnoreCase(EXPIRATION_DATE)) {
                    this.setExpirationDate(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(ISSUED_DATE)) {
                    this.setIssuedDate(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(STORAGE_CAPACITY)) {
                    this.setStorageCapacity(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(PRODUCT_ID)) {
                    this.setProductId(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(MODEL_ID)) {
                    this.setModelId(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(LICENSE_TYPE_INDICATOR)) {
                    this.setLicenseTypeIndicator(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(VERSION)) {
                    this.setVersion(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(NOTICE)) {
                    this.setNotice(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(IS_TRIAL_LICENSE)) {
                    this.setTrialLicense(Boolean.valueOf(licenseProp[1]));
                } else if (licenseProp[0].equalsIgnoreCase(LAST_REGISTRATION_EVENT_DATE)) {
                    this.setLastRegistrationEventDate(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(LAST_HEARBEAT_EVENT_DATE)) {
                    this.setLastHeartbeatEventDate(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(LAST_EXPIRATION_EVENT_DATE)) {
                    this.setLastLicenseExpirationDateEventDate(licenseProp[1]);
                } else if (licenseProp[0].equalsIgnoreCase(LAST_CAPACITY_EXCEEDED_EVENT_DATE)) {
                    this.setLastCapacityExceededEventDate(licenseProp[1]);
                }
            }
        }

        return this;
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return null;
    }

    public String getStorageCapacity() {
        return _storageCapacity;
    }

    public void setStorageCapacity(String storageCapacity) {
        this._storageCapacity = storageCapacity;
    }

    public String getProductId() {
        return _productId;
    }

    public boolean hasStorageCapacity() {
        return _storageCapacity != null && !_storageCapacity.equals(LicenseInfo.VALUE_NOT_SET);
    }

    public void setProductId(String productId) {
        this._productId = productId;
    }

    public String getIssuedDate() {
        return _issuedDate;
    }

    public void setIssuedDate(String issuedDate) {
        this._issuedDate = issuedDate;
    }

    public String getModelId() {
        return _modelId;
    }

    public void setModelId(String modelId) {
        this._modelId = modelId;
    }

    public String getLicenseTypeIndicator() {
        return _licenseTypeIndicator;
    }

    public void setLicenseTypeIndicator(String licenseTypeIndicator) {
        this._licenseTypeIndicator = licenseTypeIndicator;
    }

    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        this._version = version;
    }

    public String getNotice() {
        return _notice;
    }

    public void setNotice(String notice) {
        this._notice = notice;
    }

    public Boolean isTrialLicense() {
        return this._trialLicense;
    }

    public void setTrialLicense(Boolean trialLicense) {
        this._trialLicense = trialLicense;
    }

    public String getLastLicenseExpirationDateEventDate() {
        return _lastLicenseExpirationDateEventDate;
    }

    public void setLastLicenseExpirationDateEventDate(String lastLicenseExpirationDateEventDate) {
        this._lastLicenseExpirationDateEventDate = lastLicenseExpirationDateEventDate;
    }

    public String getLastHeartbeatEventDate() {
        return _lastHeartbeatEventDate;
    }

    public void setLastHeartbeatEventDate(String lastHeartbeatEventDate) {
        this._lastHeartbeatEventDate = lastHeartbeatEventDate;
    }

    public String getLastRegistrationEventDate() {
        return _lastRegistrationEventDate;
    }

    public void setLastRegistrationEventDate(String lastRegistrationEventDate) {
        this._lastRegistrationEventDate = lastRegistrationEventDate;
    }

    public String getLastCapacityExceededEventDate() {
        return _lastCapacityExceededEventDate;
    }

    public void setLastCapacityExceededEventDate(String lastCapacityExceededEventDate) {
        this._lastCapacityExceededEventDate = lastCapacityExceededEventDate;
    }
}
