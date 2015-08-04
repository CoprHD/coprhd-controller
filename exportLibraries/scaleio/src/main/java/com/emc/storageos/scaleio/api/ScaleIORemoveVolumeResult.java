/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

public class ScaleIORemoveVolumeResult {
    private boolean isSuccess;
    private String errorString;

    public void setIsSuccess(boolean success) {
        isSuccess = success;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String errorString() {
        return errorString;
    }

    public void setErrorString(String error) {
        errorString = error;
    }
}
