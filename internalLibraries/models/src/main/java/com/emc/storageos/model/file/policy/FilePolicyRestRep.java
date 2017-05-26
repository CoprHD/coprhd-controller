/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "file_policy")
@XmlType(namespace = "file_schedule_policy", name = "file_policy")
public class FilePolicyRestRep extends DataObjectRestRep {

    private String type;

    private String description;

    private Boolean hasAccessToTenants;

    private String appliedAt;

    private List<NamedRelatedResourceRep> assignedResources;

    private NamedRelatedResourceRep vpool;

    private ScheduleRestRep schedule;

    private ReplicationSettingsRestRep replicationSettings;

    private SnapshotSettingsRestRep snapshotSettings;

    private String priority;

    private Long numWorkerThreads;

    private Boolean applyOnTargetSite;

    @XmlElement(name = "schedule")
    public ScheduleRestRep getSchedule() {
        return schedule;
    }

    @XmlElement(name = "replication_settings")
    public ReplicationSettingsRestRep getReplicationSettings() {
        return replicationSettings;
    }

    @XmlElement(name = "snapshot_settings")
    public SnapshotSettingsRestRep getSnapshotSettings() {
        return snapshotSettings;
    }

    @XmlElement(name = "has_access_to_tenants")
    public Boolean getHasAccessToTenants() {
        return hasAccessToTenants;
    }

    @XmlElement(name = "apply_on_target_site")
    public Boolean getApplyOnTargetSite() {
        return applyOnTargetSite;
    }

    @XmlElement(name = "vpool")
    public NamedRelatedResourceRep getVpool() {
        return vpool;
    }

    @XmlElement(name = "applied_at")
    public String getAppliedAt() {
        return appliedAt;
    }

    @XmlElementWrapper(name = "assigned_resources")
    @XmlElement(name = "resource")
    public List<NamedRelatedResourceRep> getAssignedResources() {
        return assignedResources;
    }

    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    @XmlElement(name = "priority")
    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAssignedResources(List<NamedRelatedResourceRep> assignedResources) {
        this.assignedResources = assignedResources;
    }

    public void setAppliedAt(String appliedAt) {
        this.appliedAt = appliedAt;
    }

    public void setVpool(NamedRelatedResourceRep vpool) {
        this.vpool = vpool;
    }

    public void setApplyOnTargetSite(Boolean applyOnTargetSite) {
        this.applyOnTargetSite = applyOnTargetSite;
    }

    public void setHasAccessToTenants(Boolean hasAccessToTenants) {
        this.hasAccessToTenants = hasAccessToTenants;
    }

    public void setSnapshotSettings(SnapshotSettingsRestRep snapshotSettings) {
        this.snapshotSettings = snapshotSettings;
    }

    public void setReplicationSettings(ReplicationSettingsRestRep replicationSettings) {
        this.replicationSettings = replicationSettings;
    }

    public void setSchedule(ScheduleRestRep schedule) {
        this.schedule = schedule;
    }

    public void addAssignedResource(NamedRelatedResourceRep resource) {
        if (assignedResources == null) {
            assignedResources = new ArrayList<NamedRelatedResourceRep>();
        }
        assignedResources.add(resource);
    }

    @XmlElement(name = "numWorkerThreads")
    public Long getNumWorkerThreads() {
        return numWorkerThreads;
    }

    public void setNumWorkerThreads(Long numWorkerThreads) {
        this.numWorkerThreads = numWorkerThreads;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FilePolicyRestRep [");
        if (type != null) {
            builder.append("type=");
            builder.append(type);
            builder.append(", ");
        }
        if (description != null) {
            builder.append("description=");
            builder.append(description);
            builder.append(", ");
        }
        if (hasAccessToTenants != null) {
            builder.append("hasAccessToTenants=");
            builder.append(hasAccessToTenants);
            builder.append(", ");
        }

        if (appliedAt != null) {
            builder.append("appliedAt=");
            builder.append(appliedAt);
            builder.append(", ");
        }
        if (assignedResources != null) {
            builder.append("assignedResources=");
            builder.append(assignedResources);
            builder.append(", ");
        }
        if (vpool != null) {
            builder.append("vpool=");
            builder.append(vpool);
            builder.append(", ");
        }
        if (schedule != null) {
            builder.append("schedule=");
            builder.append(schedule);
            builder.append(", ");
        }
        if (replicationSettings != null) {
            builder.append("replicationSettings=");
            builder.append(replicationSettings);
            builder.append(", ");
        }
        if (snapshotSettings != null) {
            builder.append("snapshotSettings=");
            builder.append(snapshotSettings);
        }
        builder.append("]");
        return builder.toString();
    }

    @XmlRootElement(name = "file_policy_schedule")
    public static class ScheduleRestRep {

        private String frequency;

        // Policy run on every
        private Long repeat;

        // Time when policy run
        private String time;

        // Day of week when policy run
        private String dayOfWeek;

        // Day of month when policy run
        private Long dayOfMonth;

        @XmlElement(name = "frequency")
        public String getFrequency() {
            return frequency;
        }

        @XmlElement(name = "repeat_every")
        public Long getRepeat() {
            return repeat;
        }

        @XmlElement(name = "time")
        public String getTime() {
            return time;
        }

        @XmlElement(name = "day_of_week")
        public String getDayOfWeek() {
            return dayOfWeek;
        }

        @XmlElement(name = "day_of_month")
        public Long getDayOfMonth() {
            return dayOfMonth;
        }

        public void setFrequency(String frequency) {
            this.frequency = frequency;
        }

        public void setRepeat(Long repeat) {
            this.repeat = repeat;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public void setDayOfWeek(String dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public void setDayOfMonth(Long dayOfMonth) {
            this.dayOfMonth = dayOfMonth;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ScheduleRestRep [");
            if (frequency != null) {
                builder.append("frequency=");
                builder.append(frequency);
                builder.append(", ");
            }
            if (repeat != null) {
                builder.append("repeat=");
                builder.append(repeat);
                builder.append(", ");
            }
            if (time != null) {
                builder.append("time=");
                builder.append(time);
                builder.append(", ");
            }
            if (dayOfWeek != null) {
                builder.append("dayOfWeek=");
                builder.append(dayOfWeek);
                builder.append(", ");
            }
            if (dayOfMonth != null) {
                builder.append("dayOfMonth=");
                builder.append(dayOfMonth);
            }
            builder.append("]");
            return builder.toString();
        }

    }

    @XmlRootElement(name = "replication_settings")
    public static class ReplicationSettingsRestRep {

        private String type;
        private String mode;
        private Boolean replicateConfiguration;

        // Represent the replication source and target site relation
        private List<FileReplicationTopologyRestRep> replicationTopologies;

        @XmlElement(name = "type")
        public String getType() {
            return type;
        }

        @XmlElement(name = "mode")
        public String getMode() {
            return mode;
        }

        @XmlElement(name = "replication_configuration")
        public Boolean getReplicateConfiguration() {
            return replicateConfiguration;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public void setReplicateConfiguration(Boolean replicateConfiguration) {
            this.replicateConfiguration = replicateConfiguration;
        }

        @XmlElement(name = "replication_topologies")
        public List<FileReplicationTopologyRestRep> getReplicationTopologies() {
            return replicationTopologies;
        }

        public void setReplicationTopologies(List<FileReplicationTopologyRestRep> replicationTopologies) {
            this.replicationTopologies = replicationTopologies;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ReplicationParametersRestRep [");
            if (type != null) {
                builder.append("type=");
                builder.append(type);
                builder.append(", ");
            }
            if (mode != null) {
                builder.append("mode=");
                builder.append(mode);
                builder.append(", ");
            }
            builder.append("replicateConfiguration=");
            builder.append(replicateConfiguration);
            builder.append("]");
            return builder.toString();
        }

    }

    @XmlRootElement(name = "snapshot_settings")
    public static class SnapshotSettingsRestRep {

        private String snapshotNamePattern;

        private String expiryType;

        private Long expiryTime;

        @XmlElement(name = "snapshot_name_pattern")
        public String getSnapshotNamePattern() {
            return snapshotNamePattern;
        }

        @XmlElement(name = "expiry_type")
        public String getExpiryType() {
            return expiryType;
        }

        @XmlElement(name = "expiry_time")
        public Long getExpiryTime() {
            return expiryTime;
        }

        public void setExpiryTime(Long expiryTime) {
            this.expiryTime = expiryTime;
        }

        public void setSnapshotNamePattern(String snapshotNamePattern) {
            this.snapshotNamePattern = snapshotNamePattern;
        }

        public void setExpiryType(String expiryType) {
            this.expiryType = expiryType;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("SnapshotSettingsRestRep [");
            if (snapshotNamePattern != null) {
                builder.append("snapshotNamePattern=");
                builder.append(snapshotNamePattern);
                builder.append(", ");
            }
            if (expiryType != null) {
                builder.append("expiryType=");
                builder.append(expiryType);
                builder.append(", ");
            }
            if (expiryTime != null) {
                builder.append("expiryTime=");
                builder.append(expiryTime);
            }
            builder.append("]");
            return builder.toString();
        }

    }
}
