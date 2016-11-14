package com.emc.storageos.db.client.model;

public class FileReplicationPolicy extends FilePolicy {

    // File Replication type
    private String fileReplicationType;

    // File Replication copy type
    private String fileReplicationCopyType;

    public static enum FileReplicationType {
        LOCAL, REMOTE;
    }

    @Name("fileReplicationType")
    public String getFileReplicationType() {
        return fileReplicationType;
    }

    public void setFileReplicationType(String fileReplicationType) {
        this.fileReplicationType = fileReplicationType;
        setChanged("fileReplicationType");
    }

    @Name("frCopyType")
    public String getFileReplicationCopyType() {
        return fileReplicationCopyType;
    }

    public void setFileReplicationCopyType(String fileReplicationCopyType) {
        this.fileReplicationCopyType = fileReplicationCopyType;
        setChanged("frCopyType");
    }
}
