/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

import com.emc.storageos.driver.univmax.rest.JsonUtil;

public class ParamType {
    public String toJsonString() {
        return JsonUtil.toJsonString(this);
    }

    private ExecutionOption executionOption;

    /**
     * @return the executionOption
     */
    public ExecutionOption getExecutionOption() {
        return executionOption;
    }

    /**
     * @param executionOption the executionOption to set
     */
    public void setExecutionOption(ExecutionOption executionOption) {
        this.executionOption = executionOption;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ParamType [executionOption=" + executionOption + "]";
    }

}
