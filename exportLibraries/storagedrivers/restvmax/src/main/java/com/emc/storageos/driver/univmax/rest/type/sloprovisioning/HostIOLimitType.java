/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

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

    public void setHost_io_limit_mb_sec(String host_io_limit_mb_sec) {
        this.host_io_limit_mb_sec = host_io_limit_mb_sec;
    }

    public String getHost_io_limit_io_sec() {
        return host_io_limit_io_sec;
    }

    public void setHost_io_limit_io_sec(String host_io_limit_io_sec) {
        this.host_io_limit_io_sec = host_io_limit_io_sec;
    }

    public String getDynamicDistribution() {
        return dynamicDistribution;
    }

    public void setDynamicDistribution(String dynamicDistribution) {
        this.dynamicDistribution = dynamicDistribution;
    }
}
