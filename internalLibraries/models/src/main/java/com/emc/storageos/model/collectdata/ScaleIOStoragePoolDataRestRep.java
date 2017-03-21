/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

public class ScaleIOStoragePoolDataRestRep {
    private String protectionDomainId;
    private String name;
    private String id;
    private String capacityAvailableForVolumeAllocationInKb;
    private String maxCapacityInKb;
    private String numOfVolumes;

    public String getProtectionDomainId() {
        return protectionDomainId;
    }

    public void setProtectionDomainId(String protectionDomainId) {
        this.protectionDomainId = protectionDomainId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCapacityAvailableForVolumeAllocationInKb() {
        return capacityAvailableForVolumeAllocationInKb;
    }

    public void setCapacityAvailableForVolumeAllocationInKb(
            String capacityAvailableForVolumeAllocationInKb) {
        this.capacityAvailableForVolumeAllocationInKb = capacityAvailableForVolumeAllocationInKb;
    }

    public String getMaxCapacityInKb() {
        return maxCapacityInKb;
    }

    public void setMaxCapacityInKb(String maxCapacityInKb) {
        this.maxCapacityInKb = maxCapacityInKb;
    }

    public String getNumOfVolumes() {
        return numOfVolumes;
    }

    public void setNumOfVolumes(String numOfVolumes) {
        this.numOfVolumes = numOfVolumes;
    }

}
