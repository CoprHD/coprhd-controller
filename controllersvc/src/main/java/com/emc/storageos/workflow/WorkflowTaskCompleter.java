package com.emc.storageos.workflow;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class WorkflowTaskCompleter extends TaskCompleter {
	private static final Logger _log = LoggerFactory.getLogger(WorkflowTaskCompleter.class);
	
	public WorkflowTaskCompleter(URI workflowId, String opId) {
		super(Workflow.class, workflowId, opId);
	}

	@Override
	protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
			throws DeviceControllerException {
	    updateWorkflowStatus(status, coded);
		Operation update = new Operation();
		switch (status) {
		case ready: 
			update.ready();
			break;
		case error:
			update.error(coded);
			break;
		case suspended_no_error:
		    update.suspendedNoError("workflow suspended due to request or configuration");
		    break;
		case suspended_error:
		    update.suspendedError(coded);
		    break;
		}
		Workflow workflow = dbClient.queryObject(Workflow.class, getId());
		workflow.getOpStatus().updateTaskStatus(getOpId(), update);
		dbClient.updateObject(workflow);
		_log.info("WorkflowTaskCompleter status: " + status.toString());
	}
}
