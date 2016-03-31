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

}
