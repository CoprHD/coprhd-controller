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
package com.emc.storageos.scaleio.api.restapi.response;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.emc.storageos.scaleio.api.ScaleIOContants;

/**
 * Volume attributes
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaleIOVolume {
    private String sizeInKb;
    private String storagePoolId;
    private String id;
    private String name;
    private String vtreeId;
    private String volumeType;

    public String getSizeInKb() {
        return sizeInKb;
    }

    public void setSizeInKb(String sizeInKb) {
        this.sizeInKb = sizeInKb;
    }

    public String getStoragePoolId() {
        return storagePoolId;
    }

    public void setStoragePoolId(String storagePoolId) {
        this.storagePoolId = storagePoolId;
    }

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

    public boolean isThinProvisioned() {
        boolean result = false;
        if (volumeType != null && volumeType.equals(ScaleIOContants.THIN_PROVISIONED)) {
            result = true;
        }
        return result;
    }
}
