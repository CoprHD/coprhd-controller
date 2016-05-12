package com.emc.storageos.workflow;

import java.net.URI;

import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class WorkflowControllerImpl implements WorkflowController {
	private Dispatcher dispatcher;
	private WorkflowController controller;

	@Override
	public void suspendWorkflowStep(URI workflow, URI stepId, String taskId)
			throws ControllerException {
        execOrchestration("suspendWorkflowStep", workflow, stepId, taskId);
	}

	@Override
	public void resumeWorkflow(URI workflow, String taskId)
			throws ControllerException {
		execOrchestration("resumeWorkflow", workflow,  taskId);
	}

	@Override
	public void rollbackWorkflow(URI workflow, String taskId)
			throws ControllerException {
		execOrchestration("rollbackWorkflow", workflow, taskId);
	}
	
	private void execOrchestration(String methodName, Object ... args) throws ControllerException {
		dispatcher.queue(NullColumnValueGetter.getNullURI(), WORKFLOW_CONTROLLER_DEVICE,
				getController(), methodName, args);
	}

	public Dispatcher getDispatcher() {
		return dispatcher;
	}

	public void setDispatcher(Dispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	public WorkflowController getController() {
		return controller;
	}

	public void setController(WorkflowController controller) {
		this.controller = controller;
	}

}
