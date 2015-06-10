/*
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

public class ScaleIOAddVolumeResult {
    private String id;
    private String name;
    private String requestedSize;
    private String actualSize;
    private String protectionDomainName;
    private String storagePoolName;
    private boolean isSuccess;
    private String errorString;
    private boolean isThinlyProvisioned;

    public String getRequestedSize() {
        return requestedSize;
    }

    public String getProtectionDomainName() {
        return protectionDomainName;
    }

    public void setProtectionDomainName(String protectionDomainName) {
        this.protectionDomainName = protectionDomainName;
    }

    public String getStoragePoolName() {
        return storagePoolName;
    }

    public void setStoragePoolName(String storagePoolName) {
        this.storagePoolName = storagePoolName;
    }

    public void setRequestedSize(String requestedSize) {
        this.requestedSize = requestedSize;

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

    public String getActualSize() {
        return actualSize;
    }

    public void setActualSize(String actualSize) {
        this.actualSize = actualSize;
    }

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

    public void setIsThinlyProvisioned(boolean isThinlyProvisioned) {
        this.isThinlyProvisioned = isThinlyProvisioned;
    }

    public boolean isThinlyProvisioned() {
        return isThinlyProvisioned;
    }

    public void setThinlyProvisioned(boolean isThinlyProvisioned) {
        this.isThinlyProvisioned = isThinlyProvisioned;
    }
}
