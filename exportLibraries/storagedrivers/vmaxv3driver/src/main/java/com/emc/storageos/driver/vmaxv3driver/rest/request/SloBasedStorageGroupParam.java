/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.request;

/**
 * Created by gang on 9/28/16.
 */
public class SloBasedStorageGroupParam {
    private Integer num_of_vols;
    private String sloId;
    private String workloadSelection;
    private VolumeAttribute volumeAttribute;

    @Override
    public String toString() {
        return "SloBasedStorageGroupParam{" +
            "num_of_vols=" + num_of_vols +
            ", sloId='" + sloId + '\'' +
            ", workloadSelection='" + workloadSelection + '\'' +
            ", volumeAttribute=" + volumeAttribute +
            '}';
    }

    public Integer getNum_of_vols() {
        return num_of_vols;
    }

    public void setNum_of_vols(Integer num_of_vols) {
        this.num_of_vols = num_of_vols;
    }

    public String getSloId() {
        return sloId;
    }

    public void setSloId(String sloId) {
        this.sloId = sloId;
    }

    public String getWorkloadSelection() {
        return workloadSelection;
    }

    public void setWorkloadSelection(String workloadSelection) {
        this.workloadSelection = workloadSelection;
    }

    public VolumeAttribute getVolumeAttribute() {
        return volumeAttribute;
    }

    public void setVolumeAttribute(VolumeAttribute volumeAttribute) {
        this.volumeAttribute = volumeAttribute;
    }
}
