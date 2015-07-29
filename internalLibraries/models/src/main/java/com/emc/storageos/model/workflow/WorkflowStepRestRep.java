/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.workflow;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "workflow_step")
public class WorkflowStepRestRep extends DataObjectRestRep {
    private String controllerName;
    private String description;
    private String systemType;
    private String executeMethod;
    private String message;
    private String state;
    private String stepGroup;
    private String stepId;
    private String waitFor;
    private String system;
    private RelatedResourceRep workflow;
    private Date startTime;
    private Date endTime;
    private List<RelatedResourceRep> childWorkflows;

    public WorkflowStepRestRep() {
    }

    /**
     * Returns the name of the controller (long) that will be invoked for this Step.
     * This is used by the Dispatcher.
     * 
     * @return controllerName String
     */
    @XmlElement(name = "controller_name")
    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    /**
     * Returns a description provided at Step creation time of what the Step is doing.
     * The description is used for logging and history.
     * 
     * @return String
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * This is the URI for the system that will be used for the Step (if known).
     * 
     * @return URI
     */
    @XmlElement(name = "system_id")
    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    /**
     * Returns the system type (typically from system.getSystemType()).
     * 
     * @return String representation of System Type
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    /**
     * A String representing the time the Step completed.
     * 
     * @return String representing date/time
     */
    @XmlElement(name = "end_time")
    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    /**
     * Returns the execution method in the controller that will be invoked for the Step.
     * 
     * @return String methodName
     */
    @XmlElement(name = "execute_method")
    public String getExecuteMethod() {
        return executeMethod;
    }

    public void setExecuteMethod(String executeMethod) {
        this.executeMethod = executeMethod;
    }

    /**
     * Returns the message returned from the controller when the Step completed.
     * This generally indicates success or error, and if an error the nature of the error.
     * 
     * @return String
     */
    @XmlElement(name = "message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * A String representing the time the Step was dispatched (started).
     * 
     * @return String representing date/time
     */
    @XmlElement(name = "start_time")
    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    /**
     * Returns the Step state has a String (from Workflow.StepState.)
     * Typically this will be SUCCESS or ERROR.
     * 
     * @return String state
     */
    @XmlElement(name = "state")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    /**
     * Returns the StepGroup name this step belongs to. Steps do not always
     * belong to a Step Group. This is used for programming dependencies in the Workflow.
     * 
     * @return stepGroup name String
     */
    @XmlElement(name = "step_group")
    public String getStepGroup() {
        return stepGroup;
    }

    public void setStepGroup(String stepGroup) {
        this.stepGroup = stepGroup;
    }

    /**
     * Returns the unique stepId identifying this Step.
     * This is how the Step is identified in Zookeeper.
     * This is not the same as the Step's Cassandra URI.
     * 
     * @return stepId String.
     */
    @XmlElement(name = "step_id")
    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    /**
     * If present, indicates this Step will not execute until a prerequisite Step or StepGroup
     * has successfully completed Execution. This is the name of the prerequistive Step or StepGroup.
     * This is used to create dependencies between Steps.
     * 
     * @return waitFor String
     */
    @XmlElement(name = "wait_for")
    public String getWaitFor() {
        return waitFor;
    }

    public void setWaitFor(String waitFor) {
        this.waitFor = waitFor;
    }

    /**
     * This is a link to the Workflow containing this Step.
     * 
     * @return Workflow link
     */
    @XmlElement(name = "workflow")
    public RelatedResourceRep getWorkflow() {
        return workflow;
    }

    public void setWorkflow(RelatedResourceRep workflow) {
        this.workflow = workflow;
    }

    @XmlElementWrapper(name = "child_workflows")
    @XmlElement(name = "child_workflow")
    public List<RelatedResourceRep> getChildWorkflows() {
        if (childWorkflows == null) {
            childWorkflows = new ArrayList<RelatedResourceRep>();
        }
        return childWorkflows;
    }

    public void setChildWorkflows(List<RelatedResourceRep> childWorkflows) {
        this.childWorkflows = childWorkflows;
    }
}
