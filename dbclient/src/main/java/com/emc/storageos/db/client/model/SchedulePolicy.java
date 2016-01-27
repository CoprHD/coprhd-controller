/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * SchedulePolicy will contain the details of a schedule policy.
 * It will hold information about the policyId, policyName, policyType, schedulePolicyParameters,
 * snapshotExpireParameters.
 * 
 * @author prasaa9
 * 
 */

@Cf("SchedulePolicy")
public class SchedulePolicy extends DiscoveredDataObject {

    // Tenant named URI
    private NamedURI tenantOrg;

    // Type of the policy
    private String policyType;

    // Name of the policy
    private String policyName;

    // Type of schedule policy e.g days, weeks or months
    private String scheduleFrequency;

    // Policy run on every
    private Long scheduleRepeat;

    // Time when policy run
    private String scheduleTime;

    // Day of week when policy run
    private String scheduleDayOfWeek;

    // Day of month when policy run
    private Long scheduleDayOfMonth;

    // Snapshot expire type e.g hours, days, weeks, months or never
    private String snapshotExpireType;

    // Snapshot expire at
    private Long snapshotExpireTime;

    // List of resources associated with schedule policy
    private StringSet assignedResources;

    public static enum SchedulePolicyType {
        snapshot
    }

    public static enum ScheduleFrequency {
        days, weeks, months
    }

    public static enum SnapshotExpireType {
        hours, days, weeks, months, never
    }

    @NamedRelationIndex(cf = "NamedRelation", type = TenantOrg.class)
    @Name("tenantOrg")
    public NamedURI getTenantOrg() {
        return tenantOrg;
    }

    public void setTenantOrg(NamedURI tenantOrg) {
        this.tenantOrg = tenantOrg;
        setChanged("tenantOrg");
    }

    @Name("policyType")
    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
        setChanged("policyType");
    }

    @Name("policyName")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
        setChanged("policyName");
    }

    @Name("scheduleFrequency")
    public String getScheduleFrequency() {
        return scheduleFrequency;
    }

    public void setScheduleFrequency(String scheduleFrequency) {
        this.scheduleFrequency = scheduleFrequency;
        setChanged("scheduleFrequency");
    }

    @Name("scheduleRepeat")
    public Long getScheduleRepeat() {
        return scheduleRepeat;
    }

    public void setScheduleRepeat(Long scheduleRepeat) {
        this.scheduleRepeat = scheduleRepeat;
        setChanged("scheduleRepeat");
    }

    @Name("scheduleTime")
    public String getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
        setChanged("scheduleTime");
    }

    @Name("scheduleDayOfWeek")
    public String getScheduleDayOfWeek() {
        return scheduleDayOfWeek;
    }

    public void setScheduleDayOfWeek(String scheduleDayOfWeek) {
        this.scheduleDayOfWeek = scheduleDayOfWeek;
        setChanged("scheduleDayOfWeek");
    }

    @Name("scheduleDayOfMonth")
    public Long getScheduleDayOfMonth() {
        return scheduleDayOfMonth;
    }

    public void setScheduleDayOfMonth(Long scheduleDayOfMonth) {
        this.scheduleDayOfMonth = scheduleDayOfMonth;
        setChanged("scheduleDayOfMonth");
    }

    @Name("snapshotExpireType")
    public String getSnapshotExpireType() {
        return snapshotExpireType;
    }

    public void setSnapshotExpireType(String snapshotExpireType) {
        this.snapshotExpireType = snapshotExpireType;
        setChanged("snapshotExpireType");
    }

    @Name("snapshotExpireTime")
    public Long getSnapshotExpireTime() {
        return snapshotExpireTime;
    }

    public void setSnapshotExpireTime(Long snapshotExpireTime) {
        this.snapshotExpireTime = snapshotExpireTime;
        setChanged("snapshotExpireTime");
    }

    @Name("assignedResources")
    public StringSet getAssignedResources() {
        if (assignedResources == null) {
            assignedResources = new StringSet();
        }
        return assignedResources;
    }

    public void setAssignedResources(StringSet assignedResources) {
        this.assignedResources = assignedResources;
        setChanged("assignedResources");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Policy [type=");
        builder.append(policyType);
        builder.append(", policy name=");
        builder.append(policyName);
        builder.append(", schedule type=");
        builder.append(scheduleFrequency);
        builder.append(", schedule repeat=");
        builder.append(scheduleRepeat);
        builder.append(", schedule time=");
        builder.append(scheduleTime);
        builder.append(", schedule day od week=");
        builder.append(scheduleDayOfWeek);
        builder.append(", schedule day of month=");
        builder.append(scheduleDayOfMonth);
        builder.append(", snapshot expire type=");
        builder.append(snapshotExpireType);
        builder.append(", snapshot expire time=");
        builder.append(snapshotExpireTime);
        builder.append("]");
        return builder.toString();
    }
}
