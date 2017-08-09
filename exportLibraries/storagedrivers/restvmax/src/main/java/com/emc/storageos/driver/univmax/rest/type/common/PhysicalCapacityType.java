/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public class PhysicalCapacityType {

    // min/max occurs: 1/1
    private Double used_capacity_gb;
    // min/max occurs: 1/1
    private Double total_capacity_gb;

    public Double getUsed_capacity_gb() {
        return used_capacity_gb;
    }

    public Double getTotal_capacity_gb() {
        return total_capacity_gb;
    }
}
