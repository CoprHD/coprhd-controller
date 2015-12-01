package com.emc.storageos.isilon.restapi;
import java.util.ArrayList;
import java.util.List;
public class IsilonSyncPolicy {
	 //mandatory source_root_path, target_host, name, target_path, action
    public static enum Action {
        copy,
        sync
    }
    public IsilonSyncPolicy(String sourceRootPath, String targetHost,
                     String name, String targetPath, IsilonSyncPolicy.Action action) {
        this.name = name;
        this.source_root_path = sourceRootPath;
        this.action = action;
        this.target_host = targetHost;
        this.target_path = targetPath;
    }

    String name;
    /*The root directory on the source cluster the files will be synced from*/
    String source_root_path;
    IsilonSyncPolicy.Action action;
    String target_path;
    /*Hostname or IP address of sync target cluster*/
    String target_host;

    String schedule;
    /*If true, jobs will be automatically run based on this policy*/
    Boolean enabled = false;
    String description;
    Boolean target_compare_initial_sync = false;

    Integer report_max_count;
    List<String> source_exclude_directories = new ArrayList<String>();

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
    /*If true, jobs will be automatically run based on this policy*/
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getTarget_compare_initial_sync() {
        return target_compare_initial_sync;
    }

    public void setTarget_compare_initial_sync(Boolean target_compare_initial_sync) {
        this.target_compare_initial_sync = target_compare_initial_sync;
    }

    public List<String> getSource_exclude_directories() {
        return source_exclude_directories;
    }

    public void setSource_exclude_directories(List<String> source_exclude_directories) {
        this.source_exclude_directories = source_exclude_directories;
    }

    public Integer getReport_max_count() {
        return report_max_count;
    }

    public void setReport_max_count(Integer report_max_count) {
        this.report_max_count = report_max_count;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    @Override
    public String toString() {
        return "IsilonSyncPolicy{" +
                "name='" + name + '\'' +
                ", source_root_path='" + source_root_path + '\'' +
                ", enabled=" + enabled +
                ", action=" + action +
                '}';
    }

}
