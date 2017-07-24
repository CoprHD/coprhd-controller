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
public class HostIOLimitType extends DefaultParameter {

    private String host_io_limit_mb_sec;
    private String host_io_limit_io_sec;
    private String dynamicDistribution;

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
    public String getDynamicDistribution() {
        return dynamicDistribution;
    }

    /**
     * @param dynamicDistribution the dynamicDistribution to set
     */
    public void setDynamicDistribution(String dynamicDistribution) {
        this.dynamicDistribution = dynamicDistribution;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "HostIOLimitType [host_io_limit_mb_sec=" + host_io_limit_mb_sec + ", host_io_limit_io_sec=" + host_io_limit_io_sec
                + ", dynamicDistribution=" + dynamicDistribution + "]";
    }

}
