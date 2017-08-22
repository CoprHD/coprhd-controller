/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.system84;

public class TaskType {

    // min/max occurs: 0/1
    private Integer execution_order;
    // min/max occurs: 0/1
    private String description;

    public Integer getExecution_order() {
        return execution_order;
    }

    public String getDescription() {
        return description;
    }
}
