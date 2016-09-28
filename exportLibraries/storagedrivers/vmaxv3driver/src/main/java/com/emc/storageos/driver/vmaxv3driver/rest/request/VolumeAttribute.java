/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.request;

/**
 * Created by gang on 9/28/16.
 */
public class VolumeAttribute {
    private String volume_size;
    private String capacityUnit;

    @Override
    public String toString() {
        return "VolumeAttribute{" +
            "volume_size='" + volume_size + '\'' +
            ", capacityUnit='" + capacityUnit + '\'' +
            '}';
    }

    public String getVolume_size() {
        return volume_size;
    }

    public void setVolume_size(String volume_size) {
        this.volume_size = volume_size;
    }

    public String getCapacityUnit() {
        return capacityUnit;
    }

    public void setCapacityUnit(String capacityUnit) {
        this.capacityUnit = capacityUnit;
    }
}
