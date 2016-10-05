package com.emc.ctd.workflow.vipr.core;


import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import com.opensymphony.workflow.InvalidInputException;
import com.opensymphony.workflow.InvalidRoleException;
import com.opensymphony.workflow.Workflow;
import com.opensymphony.workflow.WorkflowException;
import com.opensymphony.workflow.loader.ActionDescriptor;

public class ViPRWorkflowExecutor extends ViPRWorkflowInstance {

	private final Log log = LogFactory.getLog(getClass());

    private String caller;

    private String osWorkflowName;

    private Map inputs;

    private int action = -1;

    //private long workflowId = -1L;

    private boolean finished;

    private boolean aborted;
    

    public ViPRWorkflowExecutor(Workflow workflow, String workflowName, Map<String, Object> inputs, int initialActionId) throws Exception {
    	super(workflow,workflow.initialize(workflowName, initialActionId,inputs));
		// TODO Auto-generated constructor stub
	}

	public void executeWorkflow() throws InvalidRoleException, InvalidInputException, WorkflowException {


        log.debug("Starting workflow...");
        log.debug("Name:       " + this.osWorkflowName);
        log.debug("Action:     " + this.action);
        log.debug("Caller:     " + this.caller);
        log.debug("Map:        " + this.inputs);

        // loop as long as there are more actions to do and the workflow is not
        // finished or aborted
        while (!finished && !aborted) {

            if (this.workflow == null) {
                log.error("Error creating the workflow");
                aborted = true;
            	throw new WorkflowException("Error creating the workflow");
            }

            List<ActionDescriptor> availableActions = getAllAvailableActions();//this.workflow.getAvailableActions(this.id, this.inputs);

            if (availableActions.isEmpty() ) {
                log.debug("No more actions. Workflow is finished...");
                this.finished = true;
            } else {
            	

                int nextAction = availableActions.get(0).getId();
                
                log.debug("call action " + nextAction);
                try {
                	this.workflow.doAction(this.id,nextAction, this.inputs);
                } catch (InvalidInputException iiex) {
                    log.error(iiex);
                    aborted = true;
                } catch (WorkflowException wfex) {
                    log.error(wfex);
                    aborted = true;
                }
            }
        }

        log.debug("Stopping workflow...");
        log.debug("Name:       " + this.osWorkflowName);
        log.debug("Action:     " + this.action);
        log.debug("Caller:     " + this.caller);
        log.debug("Map:        " + this.inputs);
        log.debug("WorkflowId: " + this.id);
        log.debug("End state:  " + (finished ? "Finished" : "Aborted"));


    }

}
