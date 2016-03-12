/*
   * Copyright (c) 2015 EMC Corporation
   * All Rights Reserved
   */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "filesystem_rpo_update")

public class FileSystemReplicationRPOParams {

    private Long rpoValue;
    private String rpoType;

    public FileSystemReplicationRPOParams() {
    }

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
     * 
     */
    @XmlElement(name = "rpo_type", required = true)
    public String getRpoType() {
        return rpoType;
    }

    public void setRpoType(String rpoType) {
        this.rpoType = rpoType;
    }

}
