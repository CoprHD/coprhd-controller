package com.emc.storageos.storagedriver.model;


public class StorageBlockObject extends StorageObject {

    // unique wwn of the block object
    private String wwn;

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }
}
