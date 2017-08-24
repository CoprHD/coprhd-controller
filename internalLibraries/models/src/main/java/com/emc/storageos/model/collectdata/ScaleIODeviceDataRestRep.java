/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

public class ScaleIODeviceDataRestRep {

    private ScaleIOStoragePoolDataRestRep storagePool;
    private String id;
    private String name;
    private String deviceCurrentPathName;
    private String storagePoolId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeviceCurrentPathName() {
        return deviceCurrentPathName;
    }

    public void setDeviceCurrentPathName(String deviceCurrentPathName) {
        this.deviceCurrentPathName = deviceCurrentPathName;
    }

    public String getStoragePoolId() {
        return storagePoolId;
    }

    public void setStoragePoolId(String storagePoolId) {
        this.storagePoolId = storagePoolId;
    }

    public ScaleIOStoragePoolDataRestRep getStoragePool() {
        return storagePool;
    }

    public void setStoragePool(ScaleIOStoragePoolDataRestRep storagePool) {
        this.storagePool = storagePool;
    }
}
