/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

public class IsilonSyncJob {

    public static enum State {
        scheduled,
        running,
        paused,
        finished,
        resumed,
        failed,
        canceled,
        needs_attention,
        skipped,
        pending,
        unknown,
    }

    public static enum Action {
        resync_prep,
        allow_write,                                                      // for fail over to secondary cluster
        allow_write_revert,
        test,
        run
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

    /* The name of the policy */
    private String policy_name;

    /*
     * The policy associated with this job, or null if there is currently no policy associated with this job,
     * this can happen if the job is newly created and not yet fully populated in the underlying database.
     */
    private IsilonSyncPolicy policy;

    /* The time the job started and ended in unix epoch seconds.The field is null if the job hasn't started or ended. */
    private Integer startTime;

    /* The time the job ended in UNIX epoch seconds. The field is null if the job hasn't ended. */
    private Integer endTime;

    /*
     * The amount of time in seconds between when the job was started and when it ended. If the job has not yet ended,
     * this is the amount of time since the job started. This field is null if the job has not yet started.
     */
    private Integer duration;
    private IsilonSyncJob.State state;
    private IsilonSyncJob.Action action;
    private IsilonSyncJob.SyncType syncType;
    private int job_id;
    private String id;

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public IsilonSyncPolicy getPolicy() {
        return policy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getJobId() {
        return job_id;
    }

    public void setJobId(int job_id) {
        this.job_id = job_id;
    }

    public String getPolicyName() {
        return policy_name;
    }

    public Integer getStartTime() {
        return startTime;
    }

    public Integer getEndTime() {
        return endTime;
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

    public Integer getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return "IsilonSyncJob [policy_name=" + policy_name + ", id=" + id + ", job_id=" + job_id + ", startTime=" + startTime + ", endTime="
                + endTime + ", duration=" + duration + ", state=" + state + ", action=" + action + ", syncType=" + syncType + "]";
    }

}