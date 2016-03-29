/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.sys.licensing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "license_feature")
public class LicenseFeature implements Serializable {

    private static final long serialVersionUID = -5873033105809411374L;
    public static final String MODELID_DELIMETER = ":";
    public static final String OLD_LICENSE_SUBMODEL = "LEGACY";   // For original managed capacity license
    public static final String TRIAL_LICENSE_SUBMODEL = "TRIAL";   // For trial license


    private String serial;
    private String version;
    private String dateIssued;
    private String dateExpires;

    // Format: ViPR_Controller:<LicenseType>  LicenseType could be TIER1/2/3 or any array type (VMAX3 etc.)
    private String modelId;

    private String productId;
    private String siteId = "UNKNOWN";
    private String issuer;
    private String notice;
    private boolean licensed = false;
    private boolean expired = false;
    private String licenseIdIndicator;
    private String errorMessage;
    private String storageCapacity;
    private String storageCapacityUnit;
    private boolean trialLicense = false;

    /**
     * public constructor
     */
    public LicenseFeature() {
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "serial")
    public String getSerial() {
        return serial;
    }

    /**
     * 
     * @param serial
     */
    public void setSerial(String serial) {
        this.serial = serial;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "version")
    public String getVersion() {
        return version;
    }

    /**
     * 
     * @param version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "issued_date")
    public String getDateIssued() {
        return this.dateIssued;
    }

    /**
     * 
     * @param dateIssued
     */
    public void setDateIssued(String dateIssued) {
        this.dateIssued = dateIssued;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "expiration_date")
    public String getDateExpires() {
        return dateExpires;
    }

    /**
     * 
     * @param dateExpires
     */
    public void setDateExpires(String dateExpires) {
        this.dateExpires = dateExpires;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "model")
    public String getModelId() {
        return modelId;
    }

    /**
     * 
     * @param modelId
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "product")
    public String getProductId() {
        return productId;
    }

    /**
     * 
     * @param productId
     */
    public void setProductId(String productId) {
        this.productId = productId;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "site_id")
    public String getSiteId() {
        return siteId;
    }

    /**
     * 
     * @param siteId
     */
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "issuer")
    public String getIssuer() {
        return issuer;
    }

    /**
     * 
     * @param issuer
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "notice")
    public String getNotice() {
        return notice;
    }

    /**
     * 
     * @param notice
     */
    public void setNotice(String notice) {
        this.notice = notice;
    }

    /**
     * Flag which indicates if the application contains a license file..whether
     * expired or not.
     * 
     * @return
     */
    @XmlElement(name = "licensed_ind")
    public boolean isLicensed() {
        return licensed;
    }

    /**
     * 
     * @param licensed
     */
    public void setLicensed(boolean licensed) {
        this.licensed = licensed;
    }

    /**
     * Method that returns whether or not the license is expired.
     * 
     * @return
     */
    @XmlElement(name = "expired_ind")
    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    /**
     * Indicator to show whether license id was generated as LAC or UID.
     * 
     * @return
     */
    @XmlElement(name = "license_id_indicator")
    public String getLicenseIdIndicator() {
        return licenseIdIndicator;
    }

    /**
     * 
     * @param licenseIdIndicator
     */
    public void setLicenseIdIndicator(String licenseIdIndicator) {
        this.licenseIdIndicator = licenseIdIndicator;
    }

    /**
     * Error message describing why license is not valid.
     * 
     * @return
     */
    @XmlElement(name = "error_message")
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @XmlElement(name = "storage_capacity_unit")
    public String getStorageCapacityUnit() {
        return storageCapacityUnit;
    }

    public void setStorageCapacityUnit(String storageCapacityUnit) {
        this.storageCapacityUnit = storageCapacityUnit;
    }

    @XmlElement(name = "storage_capacity")
    public String getStorageCapacity() {
        return storageCapacity;
    }

    public void setStorageCapacity(String storageCapacity) {
        this.storageCapacity = storageCapacity;
    }

    @XmlElement(name = "trial_license_ind")
    public boolean isTrialLicense() {
        return trialLicense;
    }

    public void setTrialLicense(boolean trialLicense) {
        this.trialLicense = trialLicense;
    }

    // e.g.: VIPR_CONTROLLER
    public String getLicenseFeature() {
        return getModelId().split(MODELID_DELIMETER)[0];
    }

    // e.g.: TIER1/TIER2/TIER3/ARRAY
    public String getLicenseType() {
        if (isTrialLicense()) {
            return TRIAL_LICENSE_SUBMODEL;
        }
        return getModelId().contains(MODELID_DELIMETER) ? getModelId().split(MODELID_DELIMETER)[1] : OLD_LICENSE_SUBMODEL;
    }
}
