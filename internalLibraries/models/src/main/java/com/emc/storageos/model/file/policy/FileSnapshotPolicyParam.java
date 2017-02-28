/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

/**
 * @author jainm15
 */
import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

public class FileSnapshotPolicyParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private String snapshotNamePattern;
    private FilePolicyScheduleParams policySchedule;

    // Snapshot expire parameters like type and value..
    private FileSnapshotPolicyExpireParam snapshotExpireParams;

    public FileSnapshotPolicyParam() {

    }

    /**
     * Snapshot name pattern would generate automatically
     * No need to provide input.
     * 
     * @return
     */
    @XmlElement(name = "snapshot_name_pattern")
    public String getSnapshotNamePattern() {
        return this.snapshotNamePattern;
    }

    public void setSnapshotNamePattern(String snapshotNamePattern) {
        this.snapshotNamePattern = snapshotNamePattern;
    }

    /**
     * Snapshot expire parameters like type and value..
     * 
     * @return
     */
    @XmlElement(required = true, name = "snapshot_expire_params")
    public FileSnapshotPolicyExpireParam getSnapshotExpireParams() {
        return this.snapshotExpireParams;
    }

    public void setSnapshotExpireParams(FileSnapshotPolicyExpireParam snapshotExpireParams) {
        this.snapshotExpireParams = snapshotExpireParams;
    }

    @XmlElement(name = "policy_schedule")
    public FilePolicyScheduleParams getPolicySchedule() {
        return this.policySchedule;
    }

    public void setPolicySchedule(FilePolicyScheduleParams policySchedule) {
        this.policySchedule = policySchedule;
    }
}
