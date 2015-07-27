/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import java.io.Serializable;
import java.util.Map;

import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.Workflow.StepState;
import com.emc.storageos.workflow.Workflow.StepStatus;

/**
 * A nested rollback handler for rollback of entire Workflows. It calls the original handler
 * as needed and handles the completion of the parent Workflow.
 */
class NestedWorkflowRollbackHandler implements Workflow.WorkflowRollbackHandler, Serializable {
	static final Integer NUMBER_OF_ADDED_ARGS = 2;  // number of arguments added for our handler
	// There may be a nested (i.e. previous) handler that our handler needs to call. If so,
	// it will be placed in the args here.
	static int indexOfNestedHandler(Object[] args) { 
		return (args.length - 2);  // next to last argument in list
	}
	// The parentStepId will be in the args at this position. We will need to fire a workflow
	// step completer on the parent step id.
	static int indexOfParentStepId(Object[] args) {
		return (args.length - 1);	// the very last argument
	}
	@Override
	public void initiatingRollback(Workflow workflow, Object[] args) {
		Workflow.WorkflowRollbackHandler originalHandler = 
				(Workflow.WorkflowRollbackHandler) args[indexOfNestedHandler(args)];
		if (originalHandler != null) {
			originalHandler.initiatingRollback(workflow, args);
		}
	}
	@Override
	public void rollbackComplete(Workflow workflow, Object[] args) {
		Workflow.WorkflowRollbackHandler originalHandler = 
				(Workflow.WorkflowRollbackHandler) args[indexOfNestedHandler(args)];
		if (originalHandler != null) {
			originalHandler.initiatingRollback(workflow, args);
		}
		Map<String, StepStatus> stepToStepStatus = workflow.getStepStatusMap();
		boolean rollbackError = false;
		StringBuilder builder = new StringBuilder();
		for (StepStatus stepStatus : stepToStepStatus.values()) {
			if (stepStatus.description.startsWith("Rollback ") && stepStatus.state == StepState.ERROR) {
				if (builder.length() > 0) builder.append("\n");
				builder.append(stepStatus.message);
				rollbackError = true;
			}
		}
		String parentStepId = (String) args[indexOfParentStepId(args)];
		if (rollbackError) {
			ServiceCoded coded = WorkflowException.exceptions.innerWorkflowRollbackError(workflow.getWorkflowURI().toString(), builder.toString());
			WorkflowStepCompleter.stepFailed(parentStepId, coded);
		} else {
			WorkflowStepCompleter.stepSucceded(parentStepId);
		}
	}
}
