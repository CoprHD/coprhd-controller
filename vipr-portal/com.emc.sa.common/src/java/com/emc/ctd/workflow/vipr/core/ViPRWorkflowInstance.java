package com.emc.ctd.workflow.vipr.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.InvalidInputException;
import com.opensymphony.workflow.Workflow;
import com.opensymphony.workflow.WorkflowException;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import com.opensymphony.workflow.query.WorkflowQuery;

public class ViPRWorkflowInstance  {
	protected Workflow workflow;
	protected long id;

	public long getId() {
		return this.id;
	}

	public ViPRWorkflowInstance() {
	}

	public ViPRWorkflowInstance(Workflow workflow, long id) {
		this.workflow = workflow;
		this.id = id;
	}

	public boolean canInitialize(String workflowName, int initialStep)
			throws WorkflowException {
		return this.workflow.canInitialize(workflowName, initialStep);
	}

	public boolean canModifyEntryState(int step) throws WorkflowException {
		return this.workflow.canModifyEntryState(getId(), step);
	}

	public void changeEntryState(int entryState) throws WorkflowException {
		this.workflow.changeEntryState(getId(), entryState);
	}

	public void doAction(int actionId, Map inputs)
			throws InvalidInputException, WorkflowException {
		this.workflow.doAction(getId(), actionId, inputs);
	}

	public boolean equals(Object obj) {
		return this.workflow.equals(obj);
	}

	public void executeTriggerFunction(int triggerId) throws WorkflowException {
		this.workflow.executeTriggerFunction(getId(), triggerId);
	}

	public int[] getAvailableActions(Map inputs) throws WorkflowException {
		return this.workflow.getAvailableActions(getId(), inputs);
	}

	public List getCurrentSteps() throws WorkflowException {
		return this.workflow.getCurrentSteps(getId());
	}

	public int getEntryState() throws WorkflowException {
		return this.workflow.getEntryState(getId());
	}

	public List getHistorySteps() throws WorkflowException {
		return this.workflow.getHistorySteps(getId());
	}

	public PropertySet getPropertySet() throws WorkflowException {
		return this.workflow.getPropertySet(getId());
	}

	public List getSecurityPermissions() throws WorkflowException {
		return this.workflow.getSecurityPermissions(getId());
	}

	public WorkflowDescriptor getWorkflowDescriptor() throws WorkflowException {
		return this.workflow.getWorkflowDescriptor(getWorkflowName());
	}

	public String getWorkflowName() throws WorkflowException {
		return this.workflow.getWorkflowName(getId());
	}

	public int hashCode() {
		return this.workflow.hashCode();
	}

	public List query(WorkflowQuery query) throws WorkflowException {
		return this.workflow.query(query);
	}

	public String toString() {
		return this.workflow.toString();
	}

	public List<ActionDescriptor> getAllAvailableActions() throws WorkflowException {
		List<ActionDescriptor> actions = new ArrayList<ActionDescriptor>();
		int[] actionIds = this.workflow.getAvailableActions(getId(),
				Collections.EMPTY_MAP);

		for (int i = 0; i < actionIds.length; i++) {
			ActionDescriptor action = getWorkflowDescriptor().getAction(actionIds[i]);

			actions.add(action);
		}
		return actions;
	}
}
