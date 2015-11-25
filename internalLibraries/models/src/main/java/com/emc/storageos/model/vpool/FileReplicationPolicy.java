/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

public class FileReplicationPolicy {

    private String remoteCopyMode;
    private Long rpoValue;
    private String rpoType;

    public FileReplicationPolicy() {
    }

    public FileReplicationPolicy(String remoteCopyMode, Long rpoValue, String rpoType) {
        this.remoteCopyMode = remoteCopyMode;
        this.rpoValue = rpoValue;
        this.rpoType = rpoType;
    }

    /**
     * The remote copy mode, sync or async
     * 
     * @valid ASYNCHRONOUS = Replication  will be in Asynchronous mode (default)
     * @valid SYNCHRONOUS = Replication will be in Synchronous mode
     */
    @XmlElement(name = "remote_copy_mode", required = false)
    public String getRemoteCopyMode() {
        return remoteCopyMode;
    }

    public void setRemoteCopyMode(String remoteCopyMode) {
        this.remoteCopyMode = remoteCopyMode;
    }

    /**
     * RPO value
     * 
     * @return RPO value
     */
    @XmlElement(name = "rpo_value", required = false)
    public Long getRpoValue() {
        return rpoValue;
    }

    public void setRpoValue(Long rpoValue) {
        this.rpoValue = rpoValue;
    }

    /**
     * Type of RPO unit
     * 
     * @valid SECONDS = Seconds (time-based RPO)
     * @valid MINUTES = Minutes (time-based RPO)
     * @valid HOURS = Hours (time-based RPO)
     */
    @XmlElement(name = "rpo_type", required = false)
    public String getRpoType() {
        return rpoType;
    }

    public void setRpoType(String rpoType) {
        this.rpoType = rpoType;
    }
}