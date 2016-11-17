/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

public class FileSystemReplicationSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    // to be removed
    private Long rpoValue;
    // to be removed
    private String rpoType;

    private String replicationType;

    private String replicationCopyType;

    // policy schedule parameters..
    private FilePolicyScheduleParams policySchedule;

    public enum ReplicationRPOType {
        MINUTES, HOURS, DAYS
    }

    public enum ReplicationCopyType {
        SYNC, ASYC
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

    @XmlElement(name = "replicationType")
    public String getReplicationType() {
        return replicationType;
    }

    public void setReplicationType(String replicationType) {
        this.replicationType = replicationType;
    }

    @XmlElement(name = "replicationCopyType")
    public String getReplicationCopyType() {
        return replicationCopyType;
    }

    public void setReplicationCopyType(String replicationCopyType) {
        this.replicationCopyType = replicationCopyType;
    }

    @XmlElement(name = "policy_schedule")
    public FilePolicyScheduleParams getPolicySchedule() {
        return policySchedule;
    }

    public void setPolicySchedule(FilePolicyScheduleParams policySchedule) {
        this.policySchedule = policySchedule;
    }
}
