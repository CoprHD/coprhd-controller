/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

/**
 * @author jainm15
 */
import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_policy_create")
public class FilePolicyParam implements Serializable {

    private static final long serialVersionUID = 1L;

    // Type of the policy
    private String policyType;

    // Name of the policy
    private String policyName;

    // Level at which policy has to be applied..
    private String applyAt;

    // Replication related parameters
    private FileReplicationPolicyParam replicationPolicyParams;

    // Snapshot related parameters..
    private FileSnapshotPolicyParam snapshotPolicyPrams;

    public static enum PolicyType {
        file_snapshot, file_replication, file_quota
    }

    public static enum policyApplyLevel {
        vpool, project, file_system
    }

    public FilePolicyParam() {
    }

    @XmlElement(required = true, name = "policy_type")
    public String getPolicyType() {
        return this.policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    @XmlElement(required = true, name = "policy_name")
    public String getPolicyName() {
        return this.policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(required = true, name = "apply_at")
    public String getApplyAt() {
        return this.applyAt;
    }

    public void setApplyAt(String applyAt) {
        this.applyAt = applyAt;
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

}
