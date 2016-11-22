/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

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

    // Replication related parameters
    private FileReplicationPolicyParam replicationPolicyParams;

    // Snapshot related parameters..
    private FileSnapshotPolicyParam snapshotPolicyPrams;

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

    @XmlElement(name = "policy_description")
    public String getPolicyDescription() {
        return this.policyDescription;
    }

    public void setPolicyDescription(String policyDescription) {
        this.policyDescription = policyDescription;
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
