package com.emc.sa.service.vipr.plugins.tasks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/*


 		String inputNew = "{ \"processDefinitionKey\":\"simpleApprovalProcess\", \"variables\": [{\"name\":\"mailcontent\",\"value\":\"Hello Administrator,A new request for provisioning has arrived.Please view the request and take action.<a href=\\\"http://lglbv240.lss.emc.com:9090/activiti-explorer/#tasks?category=inbox\\\">View and Approve</a>\"},{\"name\":\"to\",\"value\":\"manoj.jain@emc.com\"}]}";

 */

public class ApprovalTaskParam  extends ViPRTaskParam{
	
	String processDefinitionKey;
	public List<NameValueParam> variables = new ArrayList<NameValueParam>();

	public String getProcessDefinitionKey() {
		return processDefinitionKey;
	}

	public void setProcessDefinitionKey(String processDefinitionKey) {
		this.processDefinitionKey = processDefinitionKey;
	}

	public List<NameValueParam> getVariables() {
		return variables;
	}

	public void setVariables(List<NameValueParam> variables) {
		this.variables = variables;
	}

	class NameValueParam{
		String name;
		String value;

		public NameValueParam() {

		}

		public NameValueParam(String name, String value) {
			this.name = name;
			this.value = value;
		}


		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
		
		
	}
	
	

}
