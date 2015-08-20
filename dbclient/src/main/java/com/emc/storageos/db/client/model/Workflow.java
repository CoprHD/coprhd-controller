/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * This is the Cassandra logging form of a Workflow.
 * Workflows contain WorkflowSteps.
 * 
 * @author Watson
 */
@Cf("Workflow")
public class Workflow extends DataObject {
    /** The orchestration controller class name */
    private String _orchControllerName;
    /** The method in the orchestration controller that created the workflow. */
    private String _orchMethod;
    /** The orchestration taskId to be used in a completer. */
    private String _orchTaskId;
    /** The workflow has been completed. */
    private Boolean _completed;
    /** The state at completion. */
    private String _completionState;
    /** A message indicating success or failure. */
    private String _completionMessage;

    @Name("orchControllerName")
    public String getOrchControllerName() {
        return _orchControllerName;
    }

    public void setOrchControllerName(String orchControllerName) {
        this._orchControllerName = orchControllerName;
        setChanged("orchControllerName");
    }

    @Name("orchMethod")
    public String getOrchMethod() {
        return _orchMethod;
    }

    public void setOrchMethod(String orchMethod) {
        this._orchMethod = orchMethod;
        setChanged("orchMethod");
    }

    @Name("orchTaskId")
    @AlternateId("AltIdIndex")
    public String getOrchTaskId() {
        return _orchTaskId;
    }

    public void setOrchTaskId(String orchTaskId) {
        this._orchTaskId = orchTaskId;
        setChanged("orchTaskId");
    }

    @Name("completed")
    public Boolean getCompleted() {
        return _completed;
    }

    public void setCompleted(Boolean completed) {
        this._completed = completed;
        setChanged("completed");
    }

    @Name("completionState")
    public String getCompletionState() {
        return _completionState;
    }

    public void setCompletionState(String completionState) {
        this._completionState = completionState;
        setChanged("completionState");
    }

    @Name("completionMessage")
    public String getCompletionMessage() {
        return _completionMessage;
    }

    public void setCompletionMessage(String completionMessage) {
        this._completionMessage = completionMessage;
        setChanged("completionMessage");
    }
}
