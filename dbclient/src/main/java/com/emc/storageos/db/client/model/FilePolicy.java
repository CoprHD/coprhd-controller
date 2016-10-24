package com.emc.storageos.db.client.model;

@Cf("FilePolicy")
public class FilePolicy extends DataObject {
    // Type of the policy
    private String filePolicyType;

    // Name of the policy
    private String filePolicyName;

    // Level at which policy has to be applied..
    private String applyAt;

    public static enum FilePolicyType {
        file_snapshot, file_replication, file_quota
    }

    public static enum policyApplyLevel {
        vpool, project, file_system
    }

    @Name("filePolicyType")
    public String getFilePolicyType() {
        return filePolicyType;
    }

    public void setFilePolicyType(String filePolicyType) {
        this.filePolicyType = filePolicyType;
        setChanged("filePolicyType");
    }

    @Name("filePolicyName")
    public String getFilePolicyName() {
        return filePolicyName;
    }

    public void setFilePolicyName(String filePolicyName) {
        this.filePolicyName = filePolicyName;
        setChanged("filePolicyName");
    }

    @Name("applyAt")
    public String getApplyAt() {
        return applyAt;
    }

    public void setApplyAt(String applyAt) {
        this.applyAt = applyAt;
        setChanged("applyAt");
    }

}
