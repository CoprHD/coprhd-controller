package com.emc.ctd.vipr.workflow.tasks;

import java.util.Map;

import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.AbstractWorkflow;
import com.opensymphony.workflow.Condition;
import com.opensymphony.workflow.WorkflowException;

public class TaskFailedCondition  implements Condition{

	@Override
	public boolean passesCondition(Map transientVars, Map args, PropertySet ps) throws WorkflowException {
		
        Integer statusCode =  Integer.parseInt((String) transientVars.get("STATUSCODE"));
        
        if (statusCode  > 0){
        	return false;				//Task is not failed
        } else {
        	return true;				//Task is failed
        }
	}

}
