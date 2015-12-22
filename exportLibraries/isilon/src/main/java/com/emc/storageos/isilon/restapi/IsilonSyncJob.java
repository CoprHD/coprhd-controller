package com.emc.storageos.isilon.restapi;

public class IsilonSyncJob {

    public static enum State {
        scheduled,
        running,
        paused,
        finished,
        failed,
        canceled,
        needs_attention,
        skipped,
        pending,
        unknown,
    }

    public static enum Action {
        copy,
        sync
    }

    public static enum SyncType {
        invalid,
        legacy,
        initial,
        incremental,
        upgrade,
        fofb,
        domainmark,
    }

    /* A unique identifier for this object. */
    private String id;

    /* The ID of the job */
    private Integer jobId;

    /* The name of the policy */
    private String policyName;

    /*
     * The time the job started and ended in unix epoch seconds.
     * The field is null if the job hasn't started or ended.
     */
    private Integer startTime;
    private Integer endTime;

    /* The state of the job. */
    private IsilonSyncJob.State state;

    /* The action to be taken by this job */
    private IsilonSyncJob.Action action;

    /* The type of sync being performed by this job */
    private IsilonSyncJob.SyncType syncType;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public Integer getStartTime() {
        return startTime;
    }

    public void setStartTime(Integer startTime) {
        this.startTime = startTime;
    }

    public Integer getEndTime() {
        return endTime;
    }

    public void setEndTime(Integer endTime) {
        this.endTime = endTime;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public SyncType getSyncType() {
        return syncType;
    }

    public void setSyncType(SyncType syncType) {
        this.syncType = syncType;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "IsilonSyncJob{" +
                "id='" + id + '\'' +
                ", jobId=" + jobId +
                ", policyName='" + policyName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", state=" + state +
                ", action=" + action +
                ", syncType=" + syncType +
                '}';
    }

}
