package com.emc.storageos.driver.restvmax.vmax.type;

/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
public class HostIOLimitType {
    private String host_io_limit_mb_sec;
    private String host_io_limit_io_sec;

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

    private String dynamicDistribution;


}
