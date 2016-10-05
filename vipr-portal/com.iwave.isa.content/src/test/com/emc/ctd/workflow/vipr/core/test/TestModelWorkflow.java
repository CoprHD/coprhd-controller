package com.emc.ctd.workflow.vipr.core.test;

import java.util.HashMap;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.ctd.vipr.workflow.tasks.ViPRClientFactory;
import com.emc.ctd.workflow.vipr.core.ViPrWorkflowLauncher;
import com.opensymphony.workflow.Workflow;

public class TestModelWorkflow {

	

	
	public static void main(String[] args) throws Exception {
		
	
	
		ViPRClientFactory.getViprClient("lglbv240.lss.emc.com", 4443, "root", "Dangerous@123");

		ApplicationContext context = new ClassPathXmlApplicationContext("SpringBeans.xml");
		

		
		ViPrWorkflowLauncher viprWorkflowLauncher = (ViPrWorkflowLauncher) context.getBean("viprWorkflowLauncher");
		Workflow workflow = (Workflow) context.getBean("workflow");
		viprWorkflowLauncher.launchWorkflow("createExportVolumeLite", new HashMap<String, Object>(), 100);

	}
}
