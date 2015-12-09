/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a file policy, specified
 * during file policy creation.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "file_schedule_policy")
public class FilePolicyParam {

    // File schedule policy name
    private String policyName;

    // File schedule policy pattern
    private String policyPattern;

    // File schedule policy param
    private FilePolicyScheduleParam policySchedule;

    // File snapshot expire param
    private FileSnapshotExpireParam snapshotExpire;

    @XmlElement(required = true, name = "policy_name")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(required = true, name = "policy_schedule")
    public FilePolicyScheduleParam getPolicySchedule() {
        return policySchedule;
    }

    public void setPolicySchedule(FilePolicyScheduleParam policySchedule) {
        this.policySchedule = policySchedule;
    }

    @XmlElement(name = "policy_pattern")
    public String getPolicyPattern() {
        return policyPattern;
    }

    public void setPolicyPattern(String policyPattern) {
        this.policyPattern = policyPattern;
    }

    @XmlElement(name = "snapshot_expire")
    public FileSnapshotExpireParam getSnapshotExpire() {
        return snapshotExpire;
    }

    public void setSnapshotExpire(FileSnapshotExpireParam snapshotExpire) {
        this.snapshotExpire = snapshotExpire;
    }
}
