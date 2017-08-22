/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class HostIOLimitType extends ParamType {

    // min/max occurs: 0/1
    private String host_io_limit_mb_sec;
    // min/max occurs: 0/1
    private String host_io_limit_io_sec;
    // min/max occurs: 0/1
    private String dynamicDistribution;

    public String getHost_io_limit_mb_sec() {
        return host_io_limit_mb_sec;
    }

    public String getHost_io_limit_io_sec() {
        return host_io_limit_io_sec;
    }

    public String getDynamicDistribution() {
        return dynamicDistribution;
    }
}
