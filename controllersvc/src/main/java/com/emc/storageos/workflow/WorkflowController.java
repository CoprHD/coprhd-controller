package com.emc.storageos.workflow;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.volumecontroller.ControllerException;

public interface WorkflowController extends Controller {
	public final static String WORKFLOW_CONTROLLER_DEVICE = "WorkflowController";
	
	/**
	 * Puts a Workflow into a suspended state as soon as possible after the requested Step could be queueued to run.
	 * Note that going into the suspend state will be delayed until all executing steps have terminated.
	 * If any of the executing steps had an error, the resultant state would be SUSPENDED_ERROR, otherwise it would be SUSPENDED_NO_ERROR.
	 * @param workflow -- URI of Workflow
	 * @param stepId -- URI of Workflow Step Id
	 * @param taskId -- String task id.
	 * @throws ControllerException
	 */
	public abstract void suspendWorkflowStep(URI workflow, URI stepId, String taskId)
		throws ControllerException;
	
	/**
	 * Resumes a suspended Workflow. That is, the Workflow will try to continue forward. Any Steps that had previously
	 * errored will be re-executed. Steps that have already completed successully will not be rerun.
	 * Any previously existing rollback Steps will be removed from the Workflow.
	 * @param workflow -- Workflow URI
	 * @param taskId -- String task id
	 * @throws ControllerException
	 */
	public abstract void resumeWorkflow(URI workflow, String taskId)
		throws ControllerException;
	
	/**
	 * Rollback a suspended Workflow. This will initiate a rollback just as if the Workflow had just
	 * encountered an error. All errored steps and all successfully completed steps will be
	 * rolled back if possible.
	 * @param workflow -- Workflow URI
	 * @param taskId -- String task id
	 * @throws ControllerException
	 */
	public abstract void rollbackWorkflow(URI workflow, String taskId)
		throws ControllerException;

}
