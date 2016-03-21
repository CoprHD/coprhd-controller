package com.emc.sa.service.vipr.rackhd.gson;

public class WorkflowDefinition {

    private String friendlyName;
    private String injectableName;
    private Task[] tasks;

    public String getFriendlyName() {
        return friendlyName;
    }
    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }
    public String getInjectableName() {
        return injectableName;
    }
    public void setInjectableName(String injectableName) {
        this.injectableName = injectableName;
    }
    public Task[] getTasks() {
        return tasks;
    }
    public void setTasks(Task[] tasks) {
        this.tasks = tasks;
    }
}
