/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Date;

/**
 * This is the Cassandra form of a Workflow.Step.
 * It represents a step within an overall workflow.
 * 
 * @author Watson
 * 
 */
@Cf("WorkflowStep")
public class WorkflowStep extends DataObject {
    /** Workflow to which this WorklowStep belongs. */
    private URI workflowId;
    /** A unique step id identifying this step. */
    private String stepId;
    /** A human readable description of what the step does. */
    private String description;
    /** Every step belongs to a StepGroup. This is the stepGroup name. */
    private String stepGroup;
    /** If non-null, a stepId or stepGroup name that must complete before this step executes. */
    private String waitFor;
    /** The underlying device URI for this step (e.g. StorageSystem). */
    private URI systemId;
    /** The system type (e.g. system.getType() ) */
    private String systemType;
    /** The name of the controller used to execute the step. */
    private String controllerName;
    /** The method parameters used to execute the step. */
    private String executeMethod;
    /** The Method parameters to rollback the initial call. */
    private String rollbackMethod;
    /** The current state of the step. */
    private String state;
    /** Completion message from the controller. */
    private String message;
    /** Service code from the controller. */
    private Integer serviceCode;
    /** Time step was queued to the Dispatcher */
    private Date startTime;
    /** Time step reached a terminal state */
    private Date endTime;
    /** serialized Workflow.Method for execution */
    private byte[] executeMethodData;
    /** serialized Workflow.Method for rollback */
    private byte[] rollbackMethodData;
    /** Whether the step should be suspended */
    private Boolean suspendStep;

    @Name("workflow")
    @RelationIndex(cf = "RelationIndex", type = Workflow.class)
    public URI getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(URI workflowId) {
        this.workflowId = workflowId;
        setChanged("workflow");
    }

    @Name("stepId")
    @AlternateId("WorkflowStepIndex")
    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
        setChanged("stepId");
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("stepGroup")
    public String getStepGroup() {
        return stepGroup;
    }

    public void setStepGroup(String stepGroup) {
        this.stepGroup = stepGroup;
        setChanged("stepGroup");
    }

    @Name("waitFor")
    public String getWaitFor() {
        return waitFor;
    }

    public void setWaitFor(String waitFor) {
        this.waitFor = waitFor;
        setChanged("waitFor");
    }

    @Name("systemId")
    public URI getSystemId() {
        return systemId;
    }

    public void setSystemId(URI systemId) {
        this.systemId = systemId;
        setChanged("systemId");
    }

    @Name("systemType")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
        setChanged("systemType");
    }

    @Name("controllerName")
    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
        setChanged("controllerName");
    }

    @Name("executeMethod")
    public String getExecuteMethod() {
        return executeMethod;
    }

    public void setExecuteMethod(String executeMethod) {
        this.executeMethod = executeMethod;
        setChanged("executeMethod");
    }

    @Name("rollbackMethod")
    public String getRollbackMethod() {
        return rollbackMethod;
    }

    public void setRollbackMethod(String rollbackMethod) {
        this.rollbackMethod = rollbackMethod;
        setChanged("rollbackMethod");
    }

    @Name("state")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
        setChanged("state");
    }

    @Name("message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        setChanged("message");
    }

    @Name("serviceCode")
    public Integer getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(Integer code) {
        this.serviceCode = code;
        setChanged("serviceCode");
    }

    @Name("startTime")
    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
        setChanged("startTime");
    }

    @Name("endTime")
    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
        setChanged("endTime");
    }

    @Name("executeMethodData")
    public byte[] getExecuteMethodData() {
        return executeMethodData;
    }

    public void setExecuteMethodData(byte[] executeMethodData) {
        this.executeMethodData = executeMethodData;
        setChanged("executeMethodData");
    }

    @Name("rollbackMethodData")
    public byte[] getRollbackMethodData() {
        return rollbackMethodData;
    }

    public void setRollbackMethodData(byte[] rollbackMethodData) {
        this.rollbackMethodData = rollbackMethodData;
        setChanged("rollbackMethodData");
    }

    @Name("suspendStep")
    public Boolean getSuspendStep() {
        return suspendStep;
    }

    public void setSuspendStep(Boolean suspendStep) {
        this.suspendStep = suspendStep;
        setChanged("suspendStep");
    }
}
