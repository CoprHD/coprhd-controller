package com.emc.sa.service.vipr.rackhd.gson;

public class WorkflowTask {
    private String injectableName;
    private String instanceId;

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInjectableName() {
        return injectableName;
    }

    public void setInjectableName(String injectableName) {
        this.injectableName = injectableName;
    }

}
