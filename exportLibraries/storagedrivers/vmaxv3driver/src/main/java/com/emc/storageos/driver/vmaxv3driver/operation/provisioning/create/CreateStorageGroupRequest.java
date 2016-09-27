/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.provisioning.create;

/**
 * The request class parsed from the framework arguments and is used by the REST API request body parsing.
 *
 * Created by gang on 9/26/16.
 */
public class CreateStorageGroupRequest {
    private String srpId;
    private String storageGroupId;
    private SloBasedStorageGroupParam sloBasedStorageGroupParam;

    public String getSrpId() {
        return srpId;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public String getStorageGroupId() {
        return storageGroupId;
    }

    public void setStorageGroupId(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public SloBasedStorageGroupParam getSloBasedStorageGroupParam() {
        return sloBasedStorageGroupParam;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParam sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }
}

class SloBasedStorageGroupParam {
    private Integer numOfVols;
    private String sloId;
    private String volumeSize;
    private String capacityUnit;

    public Integer getNumOfVols() {
        return numOfVols;
    }

    public void setNumOfVols(Integer numOfVols) {
        this.numOfVols = numOfVols;
    }

    public String getSloId() {
        return sloId;
    }

    public void setSloId(String sloId) {
        this.sloId = sloId;
    }

    public String getVolumeSize() {
        return volumeSize;
    }

    public void setVolumeSize(String volumeSize) {
        this.volumeSize = volumeSize;
    }

    public String getCapacityUnit() {
        return capacityUnit;
    }

    public void setCapacityUnit(String capacityUnit) {
        this.capacityUnit = capacityUnit;
    }
}
