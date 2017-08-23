/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public class ConfigurationManagementParamType extends ParamType {

    // min/max occurs: 0/1
    private ExecutionOption executionOption;

    public void setExecutionOption(ExecutionOption executionOption) {
        this.executionOption = executionOption;
    }
}
