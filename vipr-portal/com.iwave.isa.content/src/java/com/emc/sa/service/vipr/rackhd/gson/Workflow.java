package com.emc.sa.service.vipr.rackhd.gson;

public class Workflow {
    private String _status;
    private String id;
    private Context context; 
    private String completeEventString;
    private FinishedTask[] finishedTasks;
    private String instanceId;
    private WorkflowTasks workflowTasks;

    public WorkflowTasks getWorkflowTasks() {
        return workflowTasks;
    }
    public void setWorkflowTasks(WorkflowTasks workflowTasks) {
        this.workflowTasks = workflowTasks;
    }
    public String getInstanceId() {
        return instanceId;
    }
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
    public FinishedTask[] getFinishedTasks() {
        return finishedTasks;
    }
    public void setFinishedTasks(FinishedTask[] finishedTasks) {
        this.finishedTasks = finishedTasks;
    }
    public String getCompleteEventString() {
        return completeEventString;
    }
    public void setCompleteEventString(String completeEventString) {
        this.completeEventString = completeEventString;
    }
    public Context getContext() {
        return context;
    }
    public void setContext(Context context) {
        this.context = context;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String get_status() {
        return _status;
    }
    public void set_status(String _status) {
        this._status = _status;
    }
}