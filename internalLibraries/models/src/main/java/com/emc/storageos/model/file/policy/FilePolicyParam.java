/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

/**
 * @author jainm15
 */

public class FilePolicyParam implements Serializable {

    private static final long serialVersionUID = 1L;

    // Name of the policy
    private String policyName;

    // Description of the policy
    private String policyDescription;

    // Priority of the policy
    private String priority;

    // Priority of the policy
    private int numWorkerThreads;

    // Replication related parameters
    private FileReplicationPolicyParam replicationPolicyParams;

    // Snapshot related parameters..
    private FileSnapshotPolicyParam snapshotPolicyPrams;

    // Level at which policy has to be applied..
    private String applyAt;

    //
    private boolean isAccessToTenants;

    public FilePolicyParam() {
    }

    /**
     * Name of the policy
     * 
     * @return
     */
    @XmlElement(name = "policy_name")
    public String getPolicyName() {
        return this.policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(name = "policy_description", required = false)
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

    /**
     * Level at which policy has to applied.
     * Valid values are vpool, project, file_system
     * 
     * @return
     */
    @XmlElement(name = "apply_at")
    public String getApplyAt() {
        return this.applyAt;
    }

    public void setApplyAt(String applyAt) {
        this.applyAt = applyAt;
    }

    @XmlElement(name = "num_worker_threads")
    public int getNumWorkerThreads() {
        return numWorkerThreads;
    }

    public void setNumWorkerThreads(int numWorkerThreads) {
        this.numWorkerThreads = numWorkerThreads;
    }

}
