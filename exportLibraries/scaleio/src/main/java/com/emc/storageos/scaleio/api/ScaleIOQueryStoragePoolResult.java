/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

public class ScaleIOQueryStoragePoolResult {
    private String name;
    private String totalCapacity;
    private String availableCapacity;
    private String volumeCount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(String totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public String getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(String availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public String getVolumeCount() {
        return volumeCount;
    }

    public void setVolumeCount(String volumeCount) {
        this.volumeCount = volumeCount;
    }
}
