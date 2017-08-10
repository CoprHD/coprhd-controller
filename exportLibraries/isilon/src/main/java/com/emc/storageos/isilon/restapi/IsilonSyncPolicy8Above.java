/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

/**
 * @author sanjes
 *
 *         Class representing the isilon sync iq policy object for isilon onefs v8.0.0 and above
 *         member names should match the key names in json object
 */
public class IsilonSyncPolicy8Above extends IsilonSyncPolicy {
    // set 0-Normal, 1-High
    private Integer priority;

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "IsilonSyncPolicy [name=" + name + ", source_root_path="
                + source_root_path + ", action=" + action + ", target_path="
                + target_path + ", target_host=" + target_host + ", schedule="
                + schedule + ", description=" + description
                + ", priority=" + priority
                + ", last_job_state=" + last_job_state + ", enabled=" + enabled
                + "]";
    }

    public IsilonSyncPolicy8Above copy(IsilonSyncPolicy policy) {
        this.name = policy.name;
        this.id = policy.id;
        this.source_root_path = policy.source_root_path;
        this.action = policy.action;
        this.target_path = policy.target_path;
        this.target_host = policy.target_host;
        this.schedule = policy.schedule;
        this.description = policy.description;
        this.last_job_state = policy.last_job_state;
        this.enabled = policy.enabled;
        this.workers_per_node = policy.workers_per_node;
        return this;
    }
}
