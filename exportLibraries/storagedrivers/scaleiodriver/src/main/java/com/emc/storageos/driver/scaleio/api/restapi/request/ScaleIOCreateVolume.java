/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Parameters to create a volume through ScaleIO API
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ScaleIOCreateVolume {
    private String volumeSizeInKb;
    private String storagePoolId;
    private String name;
    private String volumeType;
    private String protectionDomainId;

    public String getStoragePoolId() {
        return storagePoolId;
    }

    public void setStoragePoolId(String storagePoolId) {
        this.storagePoolId = storagePoolId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(String volumeType) {
        this.volumeType = volumeType;
    }

    public String getVolumeSizeInKb() {
        return volumeSizeInKb;
    }

    public void setVolumeSizeInKb(String volumeSizeInKb) {
        this.volumeSizeInKb = volumeSizeInKb;
    }

    public String getProtectionDomainId() {
        return protectionDomainId;
    }

    public void setProtectionDomainId(String protectinDomainId) {
        this.protectionDomainId = protectinDomainId;
    }

}
