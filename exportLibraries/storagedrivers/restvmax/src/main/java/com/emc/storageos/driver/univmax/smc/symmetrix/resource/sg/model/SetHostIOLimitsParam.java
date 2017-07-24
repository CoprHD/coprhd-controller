/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.univmax.smc.basetype.DefaultParameter;

/**
 * @author fengs5
 *
 */
public class SetHostIOLimitsParam extends DefaultParameter {

    private String host_io_limit_mb_sec;
    private String host_io_limit_io_sec;
    private DynamicDistributionType dynamicDistribution;

    /**
     * @return the host_io_limit_mb_sec
     */
    public String getHost_io_limit_mb_sec() {
        return host_io_limit_mb_sec;
    }

    /**
     * @param host_io_limit_mb_sec the host_io_limit_mb_sec to set
     */
    public void setHost_io_limit_mb_sec(String host_io_limit_mb_sec) {
        this.host_io_limit_mb_sec = host_io_limit_mb_sec;
    }

    /**
     * @return the host_io_limit_io_sec
     */
    public String getHost_io_limit_io_sec() {
        return host_io_limit_io_sec;
    }

    /**
     * @param host_io_limit_io_sec the host_io_limit_io_sec to set
     */
    public void setHost_io_limit_io_sec(String host_io_limit_io_sec) {
        this.host_io_limit_io_sec = host_io_limit_io_sec;
    }

    /**
     * @return the dynamicDistribution
     */
    public DynamicDistributionType getDynamicDistribution() {
        return dynamicDistribution;
    }

    /**
     * @param dynamicDistribution the dynamicDistribution to set
     */
    public void setDynamicDistribution(DynamicDistributionType dynamicDistribution) {
        this.dynamicDistribution = dynamicDistribution;
    }

    /**
     * @param host_io_limit_mb_sec
     * @param host_io_limit_io_sec
     * @param dynamicDistribution
     */
    public SetHostIOLimitsParam(String host_io_limit_mb_sec, String host_io_limit_io_sec, DynamicDistributionType dynamicDistribution) {
        super();
        this.host_io_limit_mb_sec = host_io_limit_mb_sec;
        this.host_io_limit_io_sec = host_io_limit_io_sec;
        this.dynamicDistribution = dynamicDistribution;
    }

}
