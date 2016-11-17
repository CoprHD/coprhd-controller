package com.emc.storageos.model.file;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

public class FileReplicationPolicyParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private String replicationType;
    private String replicationCopyType;
    private boolean replicateConfiguration = false;
    private FilePolicyScheduleParams policySchedule;

    public enum ReplicationCopyType {
        SYNC, ASYC
    }

    public enum ReplicationType {
        LOCAL, REMOTE
    }

    public FileReplicationPolicyParam() {

    }

    @XmlElement(name = "replicationType")
    public String getReplicationType() {
        return this.replicationType;
    }

    public void setReplicationType(String replicationType) {
        this.replicationType = replicationType;
    }

    @XmlElement(name = "replicationCopyType")
    public String getReplicationCopyType() {
        return this.replicationCopyType;
    }

    public void setReplicationCopyType(String replicationCopyType) {
        this.replicationCopyType = replicationCopyType;
    }

    /**
     * Whether to replicate File System configurations i.e CIFS shares, NFS Exports at the time of failover/failback.
     * Default value is False.
     * 
     */
    @XmlElement(name = "replicate_configuration")
    public boolean isReplicateConfiguration() {
        return this.replicateConfiguration;
    }

    public void setReplicateConfiguration(boolean replicateConfiguration) {
        this.replicateConfiguration = replicateConfiguration;
    }

    @XmlElement(name = "policy_schedule")
    public FilePolicyScheduleParams getPolicySchedule() {
        return this.policySchedule;
    }

    public void setPolicySchedule(FilePolicyScheduleParams policySchedule) {
        this.policySchedule = policySchedule;
    }
}
