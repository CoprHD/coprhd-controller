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

    protected String id;
    protected String name;
    protected String source_root_path;
    protected Action action;
    protected String target_path;
    protected String target_host;
    protected String schedule;
    protected String description;
    protected JobState last_job_state;
    protected Integer workers_per_node;

    /*
     * If set to true, replication jobs are automatically run based on the
     * associated replication policy and schedule. If set to false, replication
     * jobs are only performed when manually triggered.
     */
    protected Boolean enabled;

    /*
     * Specifies the last time a replication job was started for the replication
     * policy. The value is NULL if the replication policy has never run.
     */
    protected Integer last_started;

    public IsilonSyncPolicy() {
    }

    public IsilonSyncPolicy(String name, String sourceRootPath,
            String targetPath, String targetHost,
            IsilonSyncPolicy.Action action) {
        this.name = name;
        this.source_root_path = sourceRootPath;
        this.target_path = targetPath;
        this.target_host = targetHost;
        this.action = action;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceRootPath() {
        return source_root_path;
    }

    public void setSourceRootPath(String sourceRootPath) {
        this.source_root_path = sourceRootPath;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getTargetPath() {
        return target_path;
    }

    public void setTargetPath(String targetPath) {
        this.target_path = targetPath;
    }

    public String getTargetHost() {
        return target_host;
    }

    public void setTargetHost(String targetHost) {
        this.target_host = targetHost;
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

    public JobState getLastJobState() {
        return last_job_state;
    }

    public void setLastJobState(JobState lastJobState) {
        this.last_job_state = lastJobState;
    }

    public Integer getLastStarted() {
        return last_started;
    }

    public Integer getWorkersPerNode() {
        return workers_per_node;
    }

    public void setWorkersPerNode(Integer workers_per_node) {
        this.workers_per_node = workers_per_node;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "IsilonSyncPolicy [name=" + name + ", id=" + id
                + ", source_root_path=" + source_root_path + ", action=" + action + ", target_path="
                + target_path + ", target_host=" + target_host + ", schedule="
                + schedule + ", description=" + description
                + ", last_job_state=" + last_job_state + ", enabled=" + enabled
                + "]";
    }

}