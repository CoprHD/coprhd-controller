/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

public class IsilonSyncPolicy {

    /*
     * If set to copy, source files are copied to the target cluster. If set to
     * sync, files and directories that were deleted on the source cluster and
     * files that no longer match the selection criteria are deleted from the
     * target directory.
     */
    public static enum Action {
        copy, // for archival
        sync // for fail over
    }

    public static enum JobState {
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
        unknown
    }

    private String name;
    private String source_root_path;
    private Action action;
    private String target_path;
    private String target_host;
    private String schedule;
    private String description;
    private JobState last_job_state;

    /*
     * If set to true, replication jobs are automatically run based on the
     * associated replication policy and schedule. If set to false, replication
     * jobs are only performed when manually triggered.
     */
    private Boolean enabled;

    /*
     * Specifies the last time a replication job was started for the replication
     * policy. The value is NULL if the replication policy has never run.
     */
    private Integer last_started;

    public IsilonSyncPolicy() {
    }

    public IsilonSyncPolicy(String name, String source_root_path,
            String target_path, String target_host,
            IsilonSyncPolicy.Action action) {
        this.name = name;
        this.source_root_path = source_root_path;
        this.target_path = target_path;
        this.target_host = target_host;
        this.action = action;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource_root_path() {
        return source_root_path;
    }

    public void setSource_root_path(String source_root_path) {
        this.source_root_path = source_root_path;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getTarget_path() {
        return target_path;
    }

    public void setTarget_path(String target_path) {
        this.target_path = target_path;
    }

    public String getTarget_host() {
        return target_host;
    }

    public void setTarget_host(String target_host) {
        this.target_host = target_host;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public JobState getLast_job_state() {
        return last_job_state;
    }

    public Integer getLast_started() {
        return last_started;
    }

    @Override
    public String toString() {
        return "IsilonSyncPolicy [name=" + name + ", source_root_path="
                + source_root_path + ", action=" + action + ", target_path="
                + target_path + ", target_host=" + target_host + ", schedule="
                + schedule + ", description=" + description
                + ", last_job_state=" + last_job_state + ", enabled=" + enabled
                + "]";
    }

}