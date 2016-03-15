package com.emc.sa.service.vipr.rackhd.gson;

public class RackHdWorkflow {
    private String _status;
    private String id;
    private Context context; 
    private String completeEventString;
    private FinishedTask[] finishedTasks;

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