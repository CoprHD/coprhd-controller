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

    // Policy - schedule parameters!! 
    // schedule parameters are applicable only for snapshot and replication
   
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

    // Snap shot expire/retention parameters!!
    // Snapshot expire type e.g hours, days, weeks, months or never
    private String snapshotExpireType;
    
    // Snapshot expire at
    private Long snapshotExpireTime;

    // List of resources associated with schedule policy
    private StringSet assignedResources;
    
    // Replication policy parameters!!
    // replication type
    private String replicationType;
    
    //copy mode - sync or async
    private String copyMode;
    
    // Quota parameters!!
    // Quota policy does not require schedule parameters.
    // Soft limit in percentage of hard limit
    private Integer softLimit; 

    // notification limit in percentage of hard limit
    private Integer advisoryLimit; 

    // soft grace period in days
    private Integer softGrace; 
    
   

	public void setSoftGrace(Integer softGrace) {
		this.softGrace = softGrace;
		setChanged("softGrace");
	}

	public static enum SchedulePolicyType {
        file_snapshot, file_replication, file_quota
    }

    public static enum ScheduleFrequency {
        DAYS, WEEKS, MONTHS
    }

    public static enum SnapshotExpireType {
        HOURS, DAYS, WEEKS, MONTHS, NEVER
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
    
    @Name("replicationType")
    public String getReplicationType() {
		return replicationType;
	}

	public void setReplicationType(String replicationType) {
		this.replicationType = replicationType;
		setChanged("replicationType");
	}

	@Name("copyMode")
	public String getCopyMode() {
		return copyMode;
	}

	public void setCopyMode(String copyMode) {
		this.copyMode = copyMode;
		setChanged("copyMode");
	}

	@Name("softLimit")
	public Integer getSoftLimit() {
		return softLimit;
	}

	public void setSoftLimit(Integer softLimit) {
		this.softLimit = softLimit;
		setChanged("softLimit");
	}

	@Name("advisoryLimit")
	public Integer getAdvisoryLimit() {
		return advisoryLimit;
	}

	public void setAdvisoryLimit(Integer advisoryLimit) {
		this.advisoryLimit = advisoryLimit;
		setChanged("advisoryLimit");
	}

	@Name("softGrace")
	public Integer getSoftGrace() {
		return softGrace;
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
