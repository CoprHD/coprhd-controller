/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.bean;

/**
 * Java bean class for "sloprovisioning/symmetrix" GET method JSON result deserialization.
 *
 * Created by gang on 6/23/16.
 */
public class Symmetrix {
    private String symmetrixId;
    private SloCompliance sloCompliance;
    private String model;
    private String ucode;
    private Integer device_count;
    private Boolean local;
    private VirtualCapacity virtualCapacity;

    @Override
    public String toString() {
        return "Symmetrix{" +
            "symmetrixId='" + symmetrixId + '\'' +
            ", sloCompliance=" + sloCompliance +
            ", model='" + model + '\'' +
            ", ucode='" + ucode + '\'' +
            ", device_count=" + device_count +
            ", local=" + local +
            ", virtualCapacity=" + virtualCapacity +
            '}';
    }

    public String getSymmetrixId() {
        return symmetrixId;
    }

    public void setSymmetrixId(String symmetrixId) {
        this.symmetrixId = symmetrixId;
    }

    public SloCompliance getSloCompliance() {
        return sloCompliance;
    }

    public void setSloCompliance(SloCompliance sloCompliance) {
        this.sloCompliance = sloCompliance;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUcode() {
        return ucode;
    }

    public void setUcode(String ucode) {
        this.ucode = ucode;
    }

    public Integer getDevice_count() {
        return device_count;
    }

    public void setDevice_count(Integer device_count) {
        this.device_count = device_count;
    }

    public Boolean getLocal() {
        return local;
    }

    public void setLocal(Boolean local) {
        this.local = local;
    }

    public VirtualCapacity getVirtualCapacity() {
        return virtualCapacity;
    }

    public void setVirtualCapacity(VirtualCapacity virtualCapacity) {
        this.virtualCapacity = virtualCapacity;
    }
}
