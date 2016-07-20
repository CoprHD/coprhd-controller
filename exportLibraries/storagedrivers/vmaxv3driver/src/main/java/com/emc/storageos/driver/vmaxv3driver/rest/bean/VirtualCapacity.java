/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.bean;

/**
 * Java bean class(nested) for "sloprovisioning/symmetrix/{}" GET method JSON result deserialization.
 *
 * Created by gang on 6/23/16.
 */
public class VirtualCapacity {
    private Double used_capacity_gb;
    private Double total_capacity_gb;

    @Override
    public String toString() {
        return "VirtualCapacity{" +
            "used_capacity_gb=" + used_capacity_gb +
            ", total_capacity_gb=" + total_capacity_gb +
            '}';
    }

    public Double getUsed_capacity_gb() {
        return used_capacity_gb;
    }

    public void setUsed_capacity_gb(Double used_capacity_gb) {
        this.used_capacity_gb = used_capacity_gb;
    }

    public Double getTotal_capacity_gb() {
        return total_capacity_gb;
    }

    public void setTotal_capacity_gb(Double total_capacity_gb) {
        this.total_capacity_gb = total_capacity_gb;
    }
}
