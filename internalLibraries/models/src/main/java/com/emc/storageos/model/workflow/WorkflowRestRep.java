/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.workflow;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name = "workflow")
public class WorkflowRestRep extends DataObjectRestRep {
    private String orchestrationTaskId;
    private String orchestrationControllerName;
    private String orchestrationMethod;
    private String completionMessage;
    private String completionState;
    private Boolean completed;

    public WorkflowRestRep() {
    }

    /**
     * Boolean returns true if Workflow has completed.
     * 
     * @return true iff completed.
     */
    @XmlElement(name = "completed")
    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    /**
     * Returns the completionMessage. This is the overall message indicating
     * the completion status of the entire Workflow.
     * 
     * @return String
     */
    @XmlElement(name = "completion_message")
    public String getCompletionMessage() {
        return completionMessage;
    }

    public void setCompletionMessage(String completionMessage) {
        this.completionMessage = completionMessage;
    }

    /**
     * Returns the Completion State as a String. These are values from Workflow.StepState.
     * Typically SUCCESS or ERROR.
     * 
     * @return String
     */
    @XmlElement(name = "completion_state")
    public String getCompletionState() {
        return completionState;
    }

    public void setCompletionState(String completionState) {
        this.completionState = completionState;
    }

    /**
     * Returns the name or the Orchestration controller. This is used to find
     * the controller in the Dispatcher.
     * 
     * @return String Orchestration Controller Name
     */
    @XmlElement(name = "orchestration_controller_name")
    public String getOrchestrationControllerName() {
        return orchestrationControllerName;
    }

    public void setOrchestrationControllerName(String orchestrationControllerName) {
        this.orchestrationControllerName = orchestrationControllerName;
    }

    /**
     * Returns the Orchestration Method. This is the Method that is creating
     * the Workflow and its Steps.
     * 
     * @return String orchestrationMethod
     */
    @XmlElement(name = "orchestration_method")
    public String getOrchestrationMethod() {
        return orchestrationMethod;
    }

    public void setOrchestrationMethod(String orchestrationMethod) {
        this.orchestrationMethod = orchestrationMethod;
    }

    /**
     * Returns the Orchestration task ID. This is generally the task ID passed
     * down from the apisvc.
     * 
     * @return String taskId
     */
    @XmlElement(name = "orchestration_task_id")
    public String getOrchestrationTaskId() {
        return orchestrationTaskId;
    }

    public void setOrchestrationTaskId(String orchestrationTaskId) {
        this.orchestrationTaskId = orchestrationTaskId;
    }
}
