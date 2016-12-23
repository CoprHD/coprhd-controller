/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author jainm15
 */
@XmlRootElement(name = "file_policy_create")
public class FilePolicyParam implements Serializable {

    private static final long serialVersionUID = 1L;

    // Type of the policy
    private String policyType;

    // Name of the policy
    private String policyName;

    // Description of the policy
    private String policyDescription;

    // Priority of the policy
    private String priority;

    // Policy schedule
    private FilePolicyScheduleParams policySchedule;

    // Replication related parameters
    private FileReplicationPolicyParam replicationPolicyParams;

    // Snapshot related parameters..
    private FileSnapshotPolicyParam snapshotPolicyPrams;

    //
    private boolean isAccessToTenants;

    public FilePolicyParam() {
    }

    /**
     * Type of the policy,
     * valid values are : file_snapshot, file_replication, file_quota
     * 
     * @return
     */
    @XmlElement(required = true, name = "policy_type")
    public String getPolicyType() {
        return this.policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    /**
     * Name of the policy
     * 
     * @return
     */
    @XmlElement(required = true, name = "policy_name")
    public String getPolicyName() {
        return this.policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(name = "policy_description")
    public String getPolicyDescription() {
        return this.policyDescription;
    }

    public void setPolicyDescription(String policyDescription) {
        this.policyDescription = policyDescription;
    }

    /**
     * Priority of the policy
     * 
     * @return
     */
    @XmlElement(name = "priority")
    public String getPriority() {
        return this.priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    @XmlElement(name = "policy_schedule")
    public FilePolicyScheduleParams getPolicySchedule() {
        return this.policySchedule;
    }

    public void setPolicySchedule(FilePolicyScheduleParams policySchedule) {
        this.policySchedule = policySchedule;
    }

    @XmlElement(name = "replication_params")
    public FileReplicationPolicyParam getReplicationPolicyParams() {
        return this.replicationPolicyParams;
    }

    public void setReplicationPolicyParams(FileReplicationPolicyParam replicationPolicyParams) {
        this.replicationPolicyParams = replicationPolicyParams;
    }

    @XmlElement(name = "snapshot_params")
    public FileSnapshotPolicyParam getSnapshotPolicyPrams() {
        return this.snapshotPolicyPrams;
    }

    public void setSnapshotPolicyPrams(FileSnapshotPolicyParam snapshotPolicyPrams) {
        this.snapshotPolicyPrams = snapshotPolicyPrams;
    }

    @XmlElement(name = "is_access_to_tenants")
    public boolean getAccessToTenants() {
        return this.isAccessToTenants;
    }

    public void setAccessToTenants(boolean isAccessToTenants) {
        this.isAccessToTenants = isAccessToTenants;
    }

}
