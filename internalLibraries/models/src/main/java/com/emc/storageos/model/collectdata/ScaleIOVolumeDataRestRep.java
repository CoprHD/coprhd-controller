/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

public class ScaleIOVolumeDataRestRep {

    private ScaleIOStoragePoolDataRestRep storagePool;
    private String sizeInKb;
    private String storagePoolId;
    private String id;
    private String name;
    private String vtreeId;
    private String volumeType;

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

    public String getSizeInKb() {
        return sizeInKb;
    }

    public void setSizeInKb(String sizeInKb) {
        this.sizeInKb = sizeInKb;
    }

    public String getVtreeId() {
        return vtreeId;
    }

    public void setVtreeId(String vtreeId) {
        this.vtreeId = vtreeId;
    }

    public String getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(String volumeType) {
        this.volumeType = volumeType;
    }
}
