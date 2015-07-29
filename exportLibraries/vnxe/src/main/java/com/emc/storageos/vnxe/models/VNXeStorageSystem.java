/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeStorageSystem extends VNXeBase {
    private String serialNumber;
    private String model;
    private String name;
    private String isEULAAccepted;
    private String isUpgradeComplete;
    private String platform;
    private String isDefaultAdminPassword;
    private String internalModel;
    private String isAutoFailbackEnabled;
    private String resolutionIds;
    private Health health;
    private String macAddress;
    private List<Integer> operationalStatus;

    public Health getHealth() {
        return health;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public List<Integer> getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(List<Integer> operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public VNXeStorageSystem() {
    };

    public String getIsEULAAccepted() {
        return isEULAAccepted;
    }

    public void setIsEULAAccepted(String isEULAAccepted) {
        this.isEULAAccepted = isEULAAccepted;
    }

    public String getIsUpgradeComplete() {
        return isUpgradeComplete;
    }

    public void setIsUpgradeComplete(String isUpgradeComplete) {
        this.isUpgradeComplete = isUpgradeComplete;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getIsDefaultAdminPassword() {
        return isDefaultAdminPassword;
    }

    public void setIsDefaultAdminPassword(String isDefaultAdminPassword) {
        this.isDefaultAdminPassword = isDefaultAdminPassword;
    }

    public String getInternalModel() {
        return internalModel;
    }

    public void setInternalModel(String internalModel) {
        this.internalModel = internalModel;
    }

    public String getIsAutoFailbackEnabled() {
        return isAutoFailbackEnabled;
    }

    public void setIsAutoFailbackEnabled(String isAutoFailbackEnabled) {
        this.isAutoFailbackEnabled = isAutoFailbackEnabled;
    }

    public String getResolutionIds() {
        return resolutionIds;
    }

    public void setResolutionIds(String resolutionIds) {
        this.resolutionIds = resolutionIds;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
