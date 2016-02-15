
package com.emc.storageos.vasa.async;


public class TaskInfo {

	protected boolean cancelable;
    protected boolean cancelled;
    protected String error;
    protected String estimatedTimeToComplete;
    protected String name;
    protected int progress;
    protected boolean progressUpdateAvailable;
    protected Object result;
    protected String startTime;
    protected String taskId;
    protected String taskState;
    
    public boolean isCancelable() {
		return cancelable;
	}
	public void setCancelable(boolean cancelable) {
		this.cancelable = cancelable;
	}
	public boolean isCancelled() {
		return cancelled;
	}
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}
	public String getError() {
		return error;
	}
	public void setError(String error) {
		this.error = error;
	}
	public String getEstimatedTimeToComplete() {
		return estimatedTimeToComplete;
	}
	public void setEstimatedTimeToComplete(String estimatedTimeToComplete) {
		this.estimatedTimeToComplete = estimatedTimeToComplete;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getProgress() {
		return progress;
	}
	public void setProgress(int progress) {
		this.progress = progress;
	}
	public boolean isProgressUpdateAvailable() {
		return progressUpdateAvailable;
	}
	public void setProgressUpdateAvailable(boolean progressUpdateAvailable) {
		this.progressUpdateAvailable = progressUpdateAvailable;
	}
	public Object getResult() {
		return result;
	}
	public void setResult(Object result) {
		this.result = result;
	}
	public String getStartTime() {
		return startTime;
	}
	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}
	public String getTaskId() {
		return taskId;
	}
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
	public String getTaskState() {
		return taskState;
	}
	public void setTaskState(String taskState) {
		this.taskState = taskState;
	}
	public String toDisplayString() {
		System.out.println("Task Info "+name+":"+taskState);
		return null;
	}


}
