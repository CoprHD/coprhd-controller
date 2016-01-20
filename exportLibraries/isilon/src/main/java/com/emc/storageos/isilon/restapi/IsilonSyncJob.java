/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

public class IsilonSyncJob {

    public static enum Action {
        resync_prep,
        allow_write,
        allow_write_revert,
        test,
        run
    }

    private String policy_name;
    private IsilonSyncPolicy policy;
    private String id; // same as policy name
    private Action action;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getPolicy_name() {
        return policy_name;
    }

    public IsilonSyncPolicy getPolicy() {
        return policy;
    }

    @Override
    public String toString() {
        return "IsilonSyncJob [policy_name=" + policy_name + ", policy=" + policy + ", id=" + id + ", Action=" + action + "]";
    }

}
