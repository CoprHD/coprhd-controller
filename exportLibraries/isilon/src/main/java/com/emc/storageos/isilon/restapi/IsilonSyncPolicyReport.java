package com.emc.storageos.isilon.restapi;

import java.util.Arrays;

public class IsilonSyncPolicyReport extends IsilonSyncPolicy {
    private String error;
    private String[] errors;
    private String id;
    private String policy_name;
    private JobState state;
    private IsilonSyncPolicy policy;

    // Specifies the amount of time in seconds between the start and end of the replication job.
    // If the replication job has not ended, this value is the amount of time since the replication job started.
    // This field is null if the replication job has not started.
    private Integer duration;

    // Specifies the time that the replication job ended in Unix EPOCH seconds.This field is null if the replication job
    // has not ended.
    private Integer end_time;

    public String getId() {
        return id;
    }

    public String getError() {
        return error;
    }

    public String[] getErrors() {
        return errors;
    }

    public String getPolicyName() {
        return policy_name;
    }

    public JobState getState() {
        return state;
    }

    public IsilonSyncPolicy getPolicy() {
        return policy;
    }

    @Override
    public String toString() {
        return "IsilonSyncPolicyReport [error=" + error + ", errors=" + Arrays.toString(errors) + ", id=" + id + ", policy_name="
                + policy_name + ", state=" + state + "]";
    }

    public Integer getDuration() {
        return duration;
    }

    public Integer getEndTime() {
        return end_time;
    }

}
