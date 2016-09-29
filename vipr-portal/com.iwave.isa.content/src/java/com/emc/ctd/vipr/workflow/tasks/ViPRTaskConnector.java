package com.emc.ctd.vipr.workflow.tasks;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.FunctionProvider;
import com.opensymphony.workflow.WorkflowException;

public class ViPRTaskConnector implements FunctionProvider  {

//	@Override
//	public void execute(Map arg0, Map arg1, PropertySet arg2)
//			throws WorkflowException {
//		// TODO Auto-generated method stub
//		
//	}

	

	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) {

		Object sourceObject= (Object)args.get("sourceObject");		//WFinputParamsObject = ${CurrentStepProcessResult}
		String sourceObjectType = (String)args.get("sourceObjectType");
		Object targetObject= (Object)args.get("targetObject");
		String targetObjectType = (String)args.get("targetObjectType");
		
		Object wfInputObject= (Object)ps.getObject("WFinputParamsObject");
		
		Object transformObj =null;
	//com.emc.storageos.model.block.VolumeCreate	
		if (targetObjectType.equals("com.emc.storageos.model.block.VolumeCreate") ){
			transformObj = ViPRWFObjectFactory.getVolumeParams( (Map<String, Object>)sourceObject );
			
		} 	
		else if (targetObjectType.equals("com.emc.storageos.model.block.export.ExportCreateParam") ){

			
			transformObj = ViPRWFObjectFactory.getExportParams( (Map<String, Object>) wfInputObject,(List<URI>  )sourceObject );
			
		}

 		Boolean taskInputWired = new Boolean((String)args.get("TaskInputWired")); 
		if (taskInputWired){
			ps.setObject("StepInputObject", transformObj);
			ps.setObject("StepInputObjectType", targetObjectType);
		}

	}
}


