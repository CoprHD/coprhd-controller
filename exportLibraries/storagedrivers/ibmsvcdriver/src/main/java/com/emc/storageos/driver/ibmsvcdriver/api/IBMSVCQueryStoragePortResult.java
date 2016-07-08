package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.storagedriver.model.StoragePort;

public class IBMSVCQueryStoragePortResult {

    private List<StoragePort> storagePorts;

    private boolean isSuccess;

    private String errorString;

    public IBMSVCQueryStoragePortResult() {
        super();
        storagePorts = new ArrayList<StoragePort>();
    }

    public List<StoragePort> getStoragePorts() {
        return storagePorts;
    }

    public void setStoragePorts(List<StoragePort> storagePorts) {
        this.storagePorts = storagePorts;
    }

    public void addStoragePort(StoragePort storagePort) {
        this.storagePorts.add(storagePort);
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
