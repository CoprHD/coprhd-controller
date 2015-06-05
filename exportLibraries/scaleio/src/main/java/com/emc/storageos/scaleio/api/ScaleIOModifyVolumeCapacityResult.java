/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

public class ScaleIOModifyVolumeCapacityResult {
    private String errorString;
    private boolean isSuccess;

    public String getNewCapacity() {
        return newCapacity;
    }

    public void setNewCapacity(String newCapacity) {
        this.newCapacity = newCapacity;
    }

    String newCapacity;

    public void setErrorString(String errorString) {
        this.errorString = errorString;
    }

    public String getErrorString() {
        return errorString;
    }

    public void setIsSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }
}
