/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

public class FileSystemReplicationSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    private Long rpoValue;
    private String rpoType;
    private boolean replicateConfiguration = true;

    public enum ReplicationRPOType {
        MINUTES, HOURS, DAYS
    }

    public FileSystemReplicationSettings() {
    }

    /**
     * File system replication RPO value.
     */
    @XmlElement(name = "rpo_value")
    public Long getRpoValue() {
        return rpoValue;
    }

    public void setRpoValue(Long rpoValue) {
        this.rpoValue = rpoValue;
    }

    /**
     * File system replication RPO type.
     * Valid values:
     * MINUTES
     * HOURS
     * DAYS
     * Default value: Hours
     */

    @XmlElement(name = "rpo_type")
    public String getRpoType() {
        return rpoType;
    }

    public void setRpoType(String rpoType) {
        this.rpoType = rpoType;
    }

    /**
     * Whether to replicate File System configurations i.e CIFS shares, NFS Exports at the time of failover/failback.
     * Default value is TRUE.
     * 
     * @return
     */
    @XmlElement(name = "replicate_configuration")
    public boolean isReplicateConfiguration() {
        return this.replicateConfiguration;
    }

    public void setReplicateConfiguration(boolean replicateConfiguration) {
        this.replicateConfiguration = replicateConfiguration;
    }

}
