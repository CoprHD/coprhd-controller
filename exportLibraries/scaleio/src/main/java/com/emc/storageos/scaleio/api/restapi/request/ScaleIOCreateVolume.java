/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio.api.restapi.request;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
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
