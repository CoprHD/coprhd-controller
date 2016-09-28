package com.emc.ctd.workflow.vipr.core;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.opensymphony.workflow.Workflow;


public class ViPrWorkflowLauncher {

	ViPRWorkflowExecutor viprWorkflowExecutor;
	
	@Autowired
	Workflow workflow;
	
	public Workflow getWorkflow() {
		return workflow;
	}

	public void setWorkflow(Workflow workflow) {
		this.workflow = workflow;
	}

	public void launchWorkflow(String workflowName, Map<String, Object> inputs, int initialActionId ) throws Exception{
		ViPRWorkflowExecutor workflowExecutor = new ViPRWorkflowExecutor(workflow, workflowName, new HashMap<String, Object>(), 100);
		
		workflowExecutor.executeWorkflow();
		
	}
	
	public static void main(String[] args) throws Exception {
		ApplicationContext context = new ClassPathXmlApplicationContext("SpringBeans.xml");
		
		ViPrWorkflowLauncher viprWorkflowLauncher = (ViPrWorkflowLauncher) context.getBean("viprWorkflowLauncher");
		Workflow workflow = (Workflow) context.getBean("workflow");
		viprWorkflowLauncher.launchWorkflow("createExportVolume", new HashMap<String, Object>(), 100);

	}

}
