package com.emc.storageos.isilon.restapi;

import java.util.Arrays;

public class IsilonSyncPolicyReport extends IsilonSyncPolicy {
    private String error;
    private String[] errors;
    private String id;
    private String policy_name;
    private JobState state;
    private IsilonSyncPolicy policy;

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

}
