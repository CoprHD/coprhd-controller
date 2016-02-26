package com.emc.storageos.isilon.restapi;

public class IsilonSyncPolicyReport {
    private String error;
    private String[] errors;
    private String id;
    private String policy_name;
    private IsilonSyncPolicy.JobState state;
    private IsilonSyncPolicy policy;
    private Integer duration;
    private Integer end_time;
    private Action action;

    public static enum Action {
        resync_prep,
        allow_write,
        allow_write_revert,
        test,
        run
        // writes_disabled, enabling_writes, writes_enabled, disabling_writes, creating_resync_policy, resync_policy_created, allow_write

    }

    /**
     * Determines if the replication policy will start any scheduled replication jobs after encountering an error. If set
     * to true, the replication policy will not start any more replication jobs until this field is set to false.
     */
    private Boolean conflicted;
    /**
     * Specifies the number of bytes that were unchanged by the replication job.
     * Specifies the number of up-to-date files that are skipped by the
     * replication job. These files were on the source cluster during a file system scan, but were not transferred because the file had not
     * changed.
     */
    private Integer unchanged_data_bytes;
    private Integer bytes_transferred; // Specifies the number of bytes that were transferred by the replication job.
    private Integer file_data_bytes;    // Specifies the number of bytes transferred from the source to target clusters.
    private Integer network_bytes_to_source;    // Source to target bytes transferred
    private Integer network_bytes_to_target; // Target to source bytes transferred..
    private Integer total_network_bytes; // Specifies the total number of bytes that were sent over the network by the replication job.

    private Integer up_to_date_files_skipped;
    private Integer updated_files_replicated; // Specifies the number of updates file that are replicated by the replication job.

    private Integer directories_replicated;
    private Integer dirs_changed;
    private Integer dirs_deleted;
    private Integer dirs_moved;
    private Integer dirs_new;
    private Integer source_directories_created;
    private Integer source_directories_deleted;
    private Integer target_directories_created;
    private Integer target_directories_deleted;

    private Integer file_changed;
    private Integer file_linked;
    private Integer file_new; // new files created
    private Integer file_selected;
    private Integer file_transferred;
    private Integer file_unlinked;
    private Integer new_files_replicated;
    private Integer num_retransmitted_files; // #retransmitted files by job
    private Integer source_files_deleted;
    private Integer target_files_deleted;
    private Integer total_files; // Specifies the number of files that were changed by the replication job.

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

    public IsilonSyncPolicy.JobState getState() {
        return state;
    }

    public IsilonSyncPolicy getPolicy() {
        return policy;
    }

    public Integer getDuration() {
        return duration;
    }

    public Integer getEndTime() {
        return end_time;
    }

    public String getPolicy_name() {
        return policy_name;
    }

    public Integer getEnd_time() {
        return end_time;
    }

    public Boolean getConflicted() {
        return conflicted;
    }

    public Integer getUnchanged_data_bytes() {
        return unchanged_data_bytes;
    }

    public Integer getBytes_transferred() {
        return bytes_transferred;
    }

    public Integer getFile_data_bytes() {
        return file_data_bytes;
    }

    public Integer getNetwork_bytes_to_source() {
        return network_bytes_to_source;
    }

    public Integer getNetwork_bytes_to_target() {
        return network_bytes_to_target;
    }

    public Integer getTotal_network_bytes() {
        return total_network_bytes;
    }

    public Integer getUp_to_date_files_skipped() {
        return up_to_date_files_skipped;
    }

    public Integer getUpdated_files_replicated() {
        return updated_files_replicated;
    }

    public Integer getDirectories_replicated() {
        return directories_replicated;
    }

    public Integer getDirs_changed() {
        return dirs_changed;
    }

    public Integer getDirs_deleted() {
        return dirs_deleted;
    }

    public Integer getDirs_moved() {
        return dirs_moved;
    }

    public Integer getDirs_new() {
        return dirs_new;
    }

    public Integer getSource_directories_created() {
        return source_directories_created;
    }

    public Integer getSource_directories_deleted() {
        return source_directories_deleted;
    }

    public Integer getTarget_directories_created() {
        return target_directories_created;
    }

    public Integer getTarget_directories_deleted() {
        return target_directories_deleted;
    }

    public Integer getFile_changed() {
        return file_changed;
    }

    public Integer getFile_linked() {
        return file_linked;
    }

    public Integer getFile_new() {
        return file_new;
    }

    public Integer getFile_selected() {
        return file_selected;
    }

    public Integer getFile_transferred() {
        return file_transferred;
    }

    public Integer getFile_unlinked() {
        return file_unlinked;
    }

    public Integer getNew_files_replicated() {
        return new_files_replicated;
    }

    public Integer getNum_retransmitted_files() {
        return num_retransmitted_files;
    }

    public Integer getSource_files_deleted() {
        return source_files_deleted;
    }

    public Integer getTarget_files_deleted() {
        return target_files_deleted;
    }

    public Integer getTotal_files() {
        return total_files;
    }

    @Override
    public String toString() {
        return "IsilonSyncPolicyReport [id=" + id + ", policy_name=" + policy_name + ", state=" + state + ", duration=" + duration
                + ", end_time=" + end_time + ", conflicted=" + conflicted + ", unchanged_data_bytes=" + unchanged_data_bytes
                + ", bytes_transferred=" + bytes_transferred + ", file_data_bytes=" + file_data_bytes + ", network_bytes_to_source="
                + network_bytes_to_source + ", network_bytes_to_target=" + network_bytes_to_target + ", total_network_bytes="
                + total_network_bytes + ", up_to_date_files_skipped=" + up_to_date_files_skipped + ", updated_files_replicated="
                + updated_files_replicated + ", directories_replicated=" + directories_replicated + ", dirs_changed=" + dirs_changed
                + ", dirs_deleted=" + dirs_deleted + ", dirs_moved=" + dirs_moved + ", dirs_new=" + dirs_new
                + ", source_directories_created=" + source_directories_created + ", source_directories_deleted="
                + source_directories_deleted + ", target_directories_created=" + target_directories_created
                + ", target_directories_deleted=" + target_directories_deleted + ", file_changed=" + file_changed + ", file_linked="
                + file_linked + ", file_new=" + file_new + ", file_selected=" + file_selected + ", file_transferred=" + file_transferred
                + ", file_unlinked=" + file_unlinked + ", new_files_replicated=" + new_files_replicated + ", num_retransmitted_files="
                + num_retransmitted_files + ", source_files_deleted=" + source_files_deleted + ", target_files_deleted="
                + target_files_deleted + ", total_files=" + total_files + "]";
    }

    public Action getAction() {
        return action;
    }

    public void setaction(Action action) {
        this.action = action;
    }

}
