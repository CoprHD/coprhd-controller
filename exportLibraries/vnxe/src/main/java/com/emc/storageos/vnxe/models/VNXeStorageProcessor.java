/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeStorageProcessor extends VNXeBase {
    private Health health;
    private List<Integer> operationalStatus;
    private boolean needsReplacement;
    private boolean isRescueMode;
    private String model;
    private Integer slotNumber;
    private String name;
    private String emcPartNumber;
    private String emcSerialNumber;

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public List<Integer> getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(List<Integer> operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public boolean getIsNeedsReplacement() {
        return needsReplacement;
    }

    public void setIsNeedsReplacement(boolean needsReplacement) {
        this.needsReplacement = needsReplacement;
    }

    public boolean getIsRescueMode() {
        return isRescueMode;
    }

    public void setIsRescueMode(boolean isRescueMode) {
        this.isRescueMode = isRescueMode;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Integer slotNumber) {
        this.slotNumber = slotNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmcPartNumber() {
        return emcPartNumber;
    }

    public void setEmcPartNumber(String emcPartNumber) {
        this.emcPartNumber = emcPartNumber;
    }

    public String getEmcSerialNumber() {
        return emcSerialNumber;
    }

    public void setEmcSerialNumber(String emcSerialNumber) {
        this.emcSerialNumber = emcSerialNumber;
    }

}
