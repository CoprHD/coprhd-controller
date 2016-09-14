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

public class IBMSVCCreateVolumeResult {

    private String id;
    private String name;
    private String storagePoolName;
    private String requestedCapacity;
    private String provisionedCapacity;
    private String allocatedCapacity;
    private boolean isThinlyProvisioned;
    private boolean isSuccess;
    private String errorString;

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

    public String getStoragePoolName() {
        return storagePoolName;
    }

    public void setStoragePoolName(String storagePoolName) {
        this.storagePoolName = storagePoolName;
    }

    public String getRequestedCapacity() {
        return requestedCapacity;
    }

    public void setRequestedCapacity(String requestedCapacity) {
        this.requestedCapacity = requestedCapacity;
    }

    public String getProvisionedCapacity() {
        return provisionedCapacity;
    }

    public void setProvisionedCapacity(String provisionedCapacity) {
        this.provisionedCapacity = provisionedCapacity;
    }

    public String getAllocatedCapacity() {
        return allocatedCapacity;
    }

    public void setAllocatedCapacity(String allocatedCapacity) {
        this.allocatedCapacity = allocatedCapacity;
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
