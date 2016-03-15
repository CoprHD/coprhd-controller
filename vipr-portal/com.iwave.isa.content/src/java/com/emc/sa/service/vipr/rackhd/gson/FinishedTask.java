package com.emc.sa.service.vipr.rackhd.gson;

public class FinishedTask {
    private Job job;
    private String instanceId;
    
    public String getInstanceId() {
        return instanceId;
    }
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
    public Job getJob() {
        return job;
    }
    public void setJob(Job job) {
        this.job = job;
    }     
}