package com.emc.ctd.vipr.workflow.tasks;

import java.util.List;
import java.util.Map;

import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.FunctionProvider;
import com.opensymphony.workflow.WorkflowException;

public class ViPRTasks implements FunctionProvider {

	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {

		String params=(String)args.get("InputParams");
		Boolean taskInputWired = new Boolean((String)args.get("TaskInputWired")); 
		String className = (String) args.get("InputParamsClass");
		
		Object obj =null;
		String taskName = (String)args.get("TaskName");
		if ( taskInputWired){
			obj = ps.getObject("StepInputObject");

		} else {
			System.out.println("Input param "+params);
		}
		
		try {
			if (taskName.equals("CreateVolume")) {
				System.out.println("Task executing... " + taskName);
			} else if (taskName.equals("ExportVolume")) {
				System.out.println("Task executing... " + taskName);
			} else if (taskName.equals("Rollback")) {
				List<String> rollbackObj = (List<String>)ps.getObject("WFRollback");
			} else {
				System.out.println("Task not found " + taskName);
			}
		} catch (Exception e) {
			e.printStackTrace();
			List<String> rollbackObj = (List<String>)ps.getObject("WFRollback");

			throw new WorkflowException(e.getMessage());
		}
	}

}
