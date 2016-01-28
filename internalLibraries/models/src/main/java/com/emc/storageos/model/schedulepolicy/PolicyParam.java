/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.schedulepolicy;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.file.ScheduleSnapshotExpireParam;
import com.emc.storageos.model.valid.Length;

/**
 * Attributes associated with a schedule policy, specified
 * during schedule policy creation/updates.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "schedule_policy")
public class PolicyParam {

    // Type of the policy
    private String policyType;

    // Schedule policy name
    private String policyName;

    // Schedule policy param
    private SchedulePolicyParam policySchedule;

    // File snapshot expire param
    private ScheduleSnapshotExpireParam snapshotExpire;

    @XmlElement(required = true, name = "policy_type")
    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    @XmlElement(required = true, name = "policy_name")
    @Length(min = 2, max = 128)
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(required = true, name = "schedule")
    public SchedulePolicyParam getPolicySchedule() {
        return policySchedule;
    }

    public void setPolicySchedule(SchedulePolicyParam policySchedule) {
        this.policySchedule = policySchedule;
    }

    @XmlElement(name = "snapshot_expire")
    public ScheduleSnapshotExpireParam getSnapshotExpire() {
        return snapshotExpire;
    }

    public void setSnapshotExpire(ScheduleSnapshotExpireParam snapshotExpire) {
        this.snapshotExpire = snapshotExpire;
    }
}
