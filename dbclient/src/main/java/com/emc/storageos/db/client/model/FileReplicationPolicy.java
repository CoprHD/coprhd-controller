package com.emc.storageos.db.client.model;

public class FileReplicationPolicy extends FilePolicy {
    private String fileReplicationType;

    // File Replication RPO value
    private Long _frRpoValue;

    // File Replication RPO type
    private String _frRpoType;

    public static enum FileReplicationType {
        LOCAL, REMOTE, NONE;
    }

    public static enum FileReplicationRPOType {
        MINUTES,
        HOURS,
        DAYS,
    }

    @Name("fileReplicationType")
    public String getFileReplicationType() {
        return fileReplicationType;
    }

    public void setFileReplicationType(String fileReplicationType) {
        this.fileReplicationType = fileReplicationType;
        setChanged("fileReplicationType");
    }

    @Name("frRpoValue")
    public Long getFrRpoValue() {
        return _frRpoValue;
    }

    public void setFrRpoValue(Long frRpoValue) {
        this._frRpoValue = frRpoValue;
        setChanged("frRpoValue");
    }

    @Name("frRpoType")
    public String getFrRpoType() {
        return _frRpoType;
    }

    public void setFrRpoType(String frRpoType) {
        this._frRpoType = frRpoType;
        setChanged("frRpoType");
    }
}
