/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.system;

public class SymmetrixType {

    // min/max occurs: 1/1
    private String symmetrixId;
    // min/max occurs: 1/1
    private Double device_count;
    // min/max occurs: 1/1
    private String ucode;
    // min/max occurs: 1/1
    private String model;
    // min/max occurs: 1/1
    private Boolean local;

    public String getSymmetrixId() {
        return symmetrixId;
    }

    public Double getDevice_count() {
        return device_count;
    }

    public String getUcode() {
        return ucode;
    }

    public String getModel() {
        return model;
    }

    public Boolean getLocal() {
        return local;
    }
}
