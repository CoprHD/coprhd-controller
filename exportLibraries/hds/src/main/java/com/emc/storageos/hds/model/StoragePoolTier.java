/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

public class StoragePoolTier {
    private String objectID;
    private String tierID;
    private String capacityInKB;
    private String freeCapacityInKB;
    private String usageRate;
    private String diskType;
    private String raidLevel;

    public StoragePoolTier() {
    }

    public String getObjectID() {
        return objectID;
    }

    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    public String getTierID() {
        return tierID;
    }

    public void setTierID(String tierID) {
        this.tierID = tierID;
    }

    public String getCapacityInKB() {
        return capacityInKB;
    }

    public void setCapacityInKB(String capacityInKB) {
        this.capacityInKB = capacityInKB;
    }

    public String getFreeCapacityInKB() {
        return freeCapacityInKB;
    }

    public void setFreeCapacityInKB(String freeCapacityInKB) {
        this.freeCapacityInKB = freeCapacityInKB;
    }

    public String getUsageRate() {
        return usageRate;
    }

    public void setUsageRate(String usageRate) {
        this.usageRate = usageRate;
    }

    public String getDiskType() {
        return diskType;
    }

    public void setDiskType(String diskType) {
        this.diskType = diskType;
    }

    public String getRaidLevel() {
        return raidLevel;
    }

    public void setRaidLevel(String raidLevel) {
        this.raidLevel = raidLevel;
    }

}
