/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

public class IsilonSyncTargetPolicy {

    public static enum failover_failback_state {
        writes_disabled,
        enabling_writes,
        writes_enabled,
        disabling_writes,
        creating_resync_policy,
        resync_policy_created
    }

    private String name;
    private IsilonSyncTargetPolicy.failover_failback_state fofbState;
    private String target_path;
    private String source_host;
    private IsilonSyncJob.State last_job_state;

    public String getName() {
        return name;
    }

    public String getTarget_path() {
        return target_path;
    }

    public IsilonSyncJob.State getLast_job_state() {
        return last_job_state;
    }

    public IsilonSyncTargetPolicy.failover_failback_state getFoFbState() {
        return fofbState;
    }

    public String getSource_host() {
        return source_host;
    }

}
