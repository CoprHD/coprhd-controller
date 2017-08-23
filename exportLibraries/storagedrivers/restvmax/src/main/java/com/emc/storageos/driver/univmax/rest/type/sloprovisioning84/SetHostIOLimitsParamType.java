/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.DynamicDistributionType;
import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class SetHostIOLimitsParamType extends ParamType {

    // min/max occurs: 0/1
    private String host_io_limit_mb_sec;
    // min/max occurs: 0/1
	private String host_io_limit_io_sec;
    // min/max occurs: 0/1
    private DynamicDistributionType dynamicDistribution;

    public void setHost_io_limit_mb_sec(String host_io_limit_mb_sec) {
        this.host_io_limit_mb_sec = host_io_limit_mb_sec;
    }

    public void setHost_io_limit_io_sec(String host_io_limit_io_sec) {
        this.host_io_limit_io_sec = host_io_limit_io_sec;
    }

    public void setDynamicDistribution(DynamicDistributionType dynamicDistribution) {
        this.dynamicDistribution = dynamicDistribution;
    }
}
