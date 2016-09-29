package com.emc.ctd.vipr.workflow.tasks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

import com.emc.ctd.vipr.api.VolumeCreationResult;
import com.emc.ctd.workflow.vipr.ExportParams;
import com.emc.ctd.workflow.vipr.InputObjectFactory;
import com.emc.ctd.workflow.vipr.VolumeParams;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.FunctionProvider;
import com.opensymphony.workflow.WorkflowException;

public class NextStepObjectMapper implements FunctionProvider  {
	
	
	/*
	 * 							<arg name="class.name">com.emc.ctd.vipr.workflow.tasks.ViPRObjectTransformer</arg>
							<arg name="sourceObject">${fn.process.result}</arg>								
							<arg name="sourceObjectType">${wf.input.params.class}</arg>
							<arg name="targetObject">IMPLICIT</arg>								
							<arg name="targetObjectType>com.emc.ctd.workflow.vipr.VolumeParams</arg>
	 */
			
	public Object mapper(VolumeParams sourceObject,  String sourceObjectType, Object targetObject, String targetObjectType ){
		if (sourceObject.getClass().getCanonicalName().equals(targetObjectType)){
			targetObject =  sourceObject;
			return targetObject;
		}
		return null;
		

	}

	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) {

		Object sourceObject= (Object)args.get("sourceObject");		//WFinputParamsObject = ${CurrentStepProcessResult}
		String sourceObjectType = (String)args.get("sourceObjectType");
		Object targetObject= (Object)args.get("targetObject");
		String targetObjectType = (String)args.get("targetObjectType");
		
		Object wfInputObject= (Object)ps.getObject("WFinputParamsObject");
		
		Object transformObj =null;
		
		if (targetObjectType.equals("com.emc.ctd.workflow.vipr.VolumeParams") ){
			transformObj = InputObjectFactory.getVolumeParams( (Map<String, Object>)sourceObject );
			
		} 	else if (targetObjectType.equals("com.emc.ctd.workflow.vipr.ExportParams") ){
			transformObj = InputObjectFactory.getExportParams( (Map<String, Object>) wfInputObject,(VolumeCreationResult[] )sourceObject );
			
		}

 		Boolean taskInputWired = new Boolean((String)args.get("TaskInputWired")); 
		if (taskInputWired){
			ps.setObject("StepInputObject", transformObj);
			ps.setObject("StepInputObjectType", targetObjectType);
		}

	}

}
