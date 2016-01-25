/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

public class FileReplicationPolicy {

    private String copyMode;
    private Long rpoValue;
    private String rpoType;
    private String replicationType;

    public FileReplicationPolicy() {
    }

    public FileReplicationPolicy(String copyMode, Long rpoValue,
    		String rpoType, String replicationType) {
        this.copyMode = copyMode;
        this.rpoValue = rpoValue;
        this.rpoType = rpoType;
        this.replicationType = replicationType;
    }

    /**
     * The remote copy mode, sync or async
     * 
     * @valid ASYNCHRONOUS = Replication  will be in Asynchronous mode (default)
     * @valid SYNCHRONOUS = Replication will be in Synchronous mode
     */
    @XmlElement(name = "copy_mode", required = false)
    public String getCopyMode() {
        return copyMode;
    }

    public void setCopyMode(String copyMode) {
        this.copyMode = copyMode;
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
    
    /**
     * Type of file replication
     *  
     * @valid LOCAL
     * @valid REMOTE
     * @valid NONE
     */
    @XmlElement(name = "replication_type", required = false)
    public String getReplicationType() {
        return replicationType;
    }

    public void setReplicationType(String replicationType) {
        this.replicationType = replicationType;
    }
}