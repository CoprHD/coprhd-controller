/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.api;

public class IBMSVCDetachCloneMirrorResult {

    private String volumeId;

    private String cloneVolumeId;

    private String cloneVolumeName;

    private boolean isSuccess;

    private String errorString;

    public String getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    public String getCloneVolumeId() {
        return cloneVolumeId;
    }

    public void setCloneVolumeId(String cloneVolumeId) {
        this.cloneVolumeId = cloneVolumeId;
    }

    public String getCloneVolumeName() {
        return cloneVolumeName;
    }

    public void setCloneVolumeName(String cloneVolumeName) {
        this.cloneVolumeName = cloneVolumeName;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public String getErrorString() {
        return errorString;
    }

    public void setErrorString(String errorString) {
        this.errorString = errorString;
    }
}
