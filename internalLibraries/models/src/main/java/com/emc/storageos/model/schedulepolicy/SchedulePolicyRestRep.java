/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.schedulepolicy;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * Schedule Policy and returned as a response to a REST request.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "schedule_policy")
public class SchedulePolicyRestRep extends DataObjectRestRep{

    // Schedule policy associated with tenant
    private RelatedResourceRep tenant;

    // File policy id
    private URI policyId;

    // Type of the policy
    private String policyType;

    // File policy name
    private String policyName;

    // Type of schedule policy
    private String scheduleFrequency;

    // Policy run on every
    private Long scheduleRepeat;

    // Time when policy run
    private String scheduleTime;

    // Day of week when policy run
    private String scheduleDayOfWeek;

    // Day of month when policy run
    private Long scheduleDayOfMonth;

    // Snapshot expire type
    private String snapshotExpireType;

    // Snapshot expire at
    private Long snapshotExpireTime;

    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    @XmlElement(name = "policy_id")
    public URI getPolicyId() {
        return policyId;
    }

    public void setPolicyId(URI policyId) {
        this.policyId = policyId;
    }

    @XmlElement(name = "policy_type")
    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    @XmlElement(name = "policy_name")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(name = "schedule_frequency")
    public String getScheduleFrequency() {
        return scheduleFrequency;
    }

    public void setScheduleFrequency(String scheduleFrequency) {
        this.scheduleFrequency = scheduleFrequency;
    }

    @XmlElement(name = "schedule_repeat")
    public Long getScheduleRepeat() {
        return scheduleRepeat;
    }

    public void setScheduleRepeat(Long scheduleRepeat) {
        this.scheduleRepeat = scheduleRepeat;
    }

    @XmlElement(name = "schedule_time")
    public String getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    @XmlElement(name = "schedule_day_of_week")
    public String getScheduleDayOfWeek() {
        return scheduleDayOfWeek;
    }

    public void setScheduleDayOfWeek(String scheduleDayOfWeek) {
        this.scheduleDayOfWeek = scheduleDayOfWeek;
    }

    @XmlElement(name = "schedule_day_of_month")
    public Long getScheduleDayOfMonth() {
        return scheduleDayOfMonth;
    }

    public void setScheduleDayOfMonth(Long scheduleDayOfMonth) {
        this.scheduleDayOfMonth = scheduleDayOfMonth;
    }

    @XmlElement(name = "snapshot_expire_type")
    public String getSnapshotExpireType() {
        return snapshotExpireType;
    }

    public void setSnapshotExpireType(String snapshotExpireType) {
        this.snapshotExpireType = snapshotExpireType;
    }

    @XmlElement(name = "snapshot_expire_time")
    public Long getSnapshotExpireTime() {
        return snapshotExpireTime;
    }

    public void setSnapshotExpireTime(Long snapshotExpireTime) {
        this.snapshotExpireTime = snapshotExpireTime;
    }
}
