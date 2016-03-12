/*
   * Copyright (c) 2015 EMC Corporation
   * All Rights Reserved
   */
package com.emc.storageos.model.file;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "filesystem_rpo_update")

public class FileSystemReplicationRPOParams implements Serializable {

    private static final long serialVersionUID = 1L;
    private Long rpoValue;
    private String rpoType = ReplicationRPOType.HOURS.name();

    public enum ReplicationRPOType {
        MINUTES, HOURS, DAYS
    }

    public FileSystemReplicationRPOParams() {
    }

    /**
     * File system replication RPO value.
     */
    @XmlElement(name = "rpo_value", required = true)
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

}
