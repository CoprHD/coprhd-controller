package com.emc.ctd.workflow.vipr.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.FunctionProvider;
import com.opensymphony.workflow.WorkflowException;

@Service("viprWorkflowService")
@Scope("prototype")
public class ViPRWorkflowService implements FunctionProvider {

	String inputParams;

	public String getInputParams() {
		return inputParams;
	}

	public void setInputParams(String inputParams) {
		this.inputParams = inputParams;
	}


	@Override
	@SuppressWarnings("rawtypes")
	public void execute(Map transientVars, Map args, PropertySet ps)
			throws WorkflowException {
		String params = (String) args.get("WfInputParams");
		String className =(String) args.get("WFinputParamsClass");
        final Gson gson = new Gson();
		
		Map<String, Object> jsonMap =GenericWFInputParams.jsonObjectParser(params+".json");
//			Class<?> clazz = Class.forName(className);
//            final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
//            Object obj = clazz.cast(gson.fromJson(reader, clazz));
		
		transientVars.put("CurrentStepProcessResult", jsonMap);

		String pretJson = gson.toJson(jsonMap);
		System.out.println("Pretty printing: " + pretJson);
   
		ps.setString("WfInputParams", params);
		ps.setString("WFinputParamsClass", className);
		ps.setObject("WFinputParamsObject", jsonMap);
		ps.setObject("WFRollback", new ArrayList<String>());
		
		System.out.println("ViPRWorkflowService transientVars printing: " + transientVars);
		System.out.println("ViPRWorkflowService args printing: " + args);  		
		System.out.println("ViPRWorkflowService PropertySet printing: " + ps);
		

		
	}
}