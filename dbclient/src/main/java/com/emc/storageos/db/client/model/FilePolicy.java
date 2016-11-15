package com.emc.storageos.db.client.model;

@Cf("FilePolicy")
public class FilePolicy extends DataObject {
    // Type of the policy
    private String filePolicyType;

    // Name of the policy
    private String filePolicyName;

    // Level at which policy has to be applied..
    private String applyAt;

    // Tenants who will have access to this policy
    private StringSet accessTenants;

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

    // Snapshot expire type e.g hours, days, weeks, months or never
    private String snapshotExpireType;

    // Snapshot expire at
    private Long snapshotExpireTime;

    // File Replication type
    private String fileReplicationType;

    // File Replication copy type
    private String fileReplicationCopyType;

    public static enum FileReplicationType {
        LOCAL, REMOTE;
    }

    public static enum ScheduleFrequency {
        DAYS, WEEKS, MONTHS
    }

    public static enum SnapshotExpireType {
        HOURS, DAYS, WEEKS, MONTHS, NEVER
    }

    public static enum FilePolicyType {
        file_snapshot, file_replication, file_quota
    }

    public static enum policyApplyLevel {
        vpool, project, file_system
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

    @Name("accessTenants")
    public StringSet getTenantOrg() {
        return this.accessTenants;
    }

    public void setTenantOrg(StringSet accessTenants) {
        this.accessTenants = accessTenants;
        setChanged("accessTenants");
    }

}
