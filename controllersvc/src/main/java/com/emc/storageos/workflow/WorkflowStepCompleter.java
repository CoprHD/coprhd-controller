/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import java.io.Serializable;

import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Should be called in a Downstream Method to:
 * 1. Indicate the method is executing
 * 2. Indicate the completion status for a Workflow Step.
 * 
 * @author watson
 */
public class WorkflowStepCompleter implements Serializable {

    /**
     * Sets the step state to executing and leaves the overall task status as pending
     * 
     * @param stepId
     * @param message
     * @throws WorkflowException
     */
    static public void stepExecuting(String stepId) throws WorkflowException {
        WorkflowService.completerStepExecuting(stepId);
    }

    /**
     * Sets the step state to success and leaves the overall task status as pending or records it as ready depending on whether
     * all steps have finished or not
     * 
     * @param stepId
     * @param message
     * @throws WorkflowException
     */
    static public void stepSucceded(String stepId) throws WorkflowException {
        WorkflowService.completerStepSucceded(stepId);
    }
    
    /**
     * Sets the step state to success but puts a warning message in the status entry.
     * @param stepId
     * @param warningMessage
     * @throws WorkflowException
     */
    static public void stepSucceeded(String stepId, String warningMessage) throws WorkflowException {
        WorkflowService.completerStepSucceeded(stepId, warningMessage);
    }

    /**
     * Sets the step state to error and records the overall task status as error
     * 
     * @param stepId
     * @param message
     * @throws WorkflowException
     */
    static public void stepFailed(String stepId, ServiceCoded coded) throws WorkflowException {
        WorkflowService.completerStepError(stepId, coded);
    }

    /**
     * Sets the step state to queued.
     *
     * @param stepId
     * @throws WorkflowException
     */
    static public void stepQueued(String stepId) throws WorkflowException {
        WorkflowService.completerStepQueued(stepId);
    }

    /**
     * Compatible call using status compatible with existing Tasks
     * 
     * @param taskId -- The UUID for the task
     * @param status -- Operation.status (ready, error)
     * @param message -- Controller generated message
     * @throws WorkflowException
     */
    @Deprecated
    static public void updateState(String taskId,
            Operation.Status status, String message) throws WorkflowException {
        Workflow.StepState state = Workflow.StepState.EXECUTING;
        if (status == Operation.Status.ready) {
            state = Workflow.StepState.SUCCESS;
        }
        if (status == Operation.Status.error) {
            state = Workflow.StepState.ERROR;
        }
        updateState(taskId, state, message);
    }

    /**
     * Native call that uses Workflow StepState to express additional states such as
     * EXECUTING, CANCELLED, etc.
     * 
     * @param taskId -- Step id
     * @param state -- StepState
     * @param message -- Controller generated message
     * @throws WorkflowException
     */
    @Deprecated
    static public void updateState(String taskId, Workflow.StepState state, String message) throws WorkflowException {
        WorkflowService.completerUpdateStep(taskId, state, message);
    }
}
