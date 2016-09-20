package com.emc.sa.service.vipr.plugins.tasks;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.emc.sa.engine.extension.ExternalTaskApdapterInterface;
import com.emc.storageos.vasa.async.TaskInfo;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

public class CreateVolumeExtension implements ExternalTaskApdapterInterface {

	String inputNew = "{ \"processDefinitionKey\":\"simpleApprovalProcess\", \"variables\": [{\"name\":\"mailcontent\",\"value\":\"Hello Administrator,A new request for provisioning has arrived.Please view the request and take action.<a href=\\\"http://lglbv240.lss.emc.com:9090/activiti-explorer/#tasks?category=inbox\\\">View and Approve</a>\"},{\"name\":\"to\",\"value\":\"manoj.jain@emc.com\"}]}";
	String REST_URL = "http://lglbv240.lss.emc.com:9090/activiti-rest/service/runtime/process-instances";
	Client client = Client.create();
	HTTPBasicAuthFilter filter = new HTTPBasicAuthFilter("kermit", "kermit");
	
    public CreateVolumeExtension() {
		client.addFilter(filter);
	}

	@Override
	public void init() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void precheck() throws Exception {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void preLaunch(String extenalTaskParam) throws Exception {
		ApprovalTaskParam approvalTaskParam = new ApprovalTaskParam();
		approvalTaskParam.configurePreLaunchParams(extenalTaskParam);
		Gson uglyJson = new Gson();
		
		String uglyJsonString = uglyJson.toJson(approvalTaskParam);
		sendApprovalMail(uglyJsonString);
		System.out.println("Custom Service pre launch complete!");
	}


	@Override
	public TaskInfo executeExternal(String extenalTaskParam) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void postcheck() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void postLaunch(String extenalTaskParam) throws Exception {
		System.out.println("External Task Param for post launch : \n" + extenalTaskParam);
		ApprovalTaskParam approvalTaskParam = new ApprovalTaskParam();
		approvalTaskParam.configurePostLaunchParameters(extenalTaskParam);
		Gson uglyJson = new Gson();
		
		String uglyJsonString = uglyJson.toJson(approvalTaskParam);
		sendApprovalMail(uglyJsonString);
		System.out.println("Custom Service post launch complete!");
		
	}


	@Override
	public void getStatus() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}
	
	private void sendApprovalMail(String approvalTaskParam){
		System.out.println("Approval Task Param for  sendApprovalMail \n"+ approvalTaskParam);
		WebResource webResource = client.resource(REST_URL);
		ClientResponse clientReponse = webResource.type("application/json").post(ClientResponse.class, approvalTaskParam);
		String processDefinitionId = null;
		try {
			if (201 == clientReponse.getClientResponseStatus().getStatusCode()) {
				String response = clientReponse.getEntity(String.class);
				ObjectMapper mapper = new ObjectMapper();
				JsonNode result = mapper.readTree(response);
				processDefinitionId = result.get("processDefinitionId").asText();

				while (true) {
					System.out.println("Waiting for activiti process to complete!");
					try {
						Thread.sleep(5000);

						client = Client.create();
						client.addFilter(filter);
						webResource = client.resource(REST_URL);
						ClientResponse getClientResponse = webResource.type("application/json").get(ClientResponse.class);
						String getResponse = getClientResponse.getEntity(String.class);

						ObjectMapper getResponseMapper = new ObjectMapper();
						JsonNode getResult = getResponseMapper.readTree(getResponse);
						List<JsonNode> data = getResult.findValues("data");
						boolean processRunning = false;
						if (data.size() > 0) {

							for (JsonNode processInstance : data) {
								Iterator<JsonNode> iterator = processInstance.iterator();
								
								while (iterator.hasNext()) {
									if (iterator.next().get("processDefinitionId").asText().equals(processDefinitionId)) {
										processRunning = true;
										continue;
									}
								}
							}
							if (!processRunning) {
								System.out.println("Activiti Process execution complete!");
								break;
							}
						} else {
							System.out.println("Activiti Process execution complete!");
							break;
						}
						
					} catch (InterruptedException e) {
						// ignore for now
					}
				}
			} else {
				System.out.println(clientReponse.getEntity(String.class));
			}
		} catch (ClientHandlerException e) {
			// ignore exceptions for now
			e.printStackTrace();
		} catch (UniformInterfaceException e) {
			// ignore exceptions for now
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			// ignore exceptions for now
			e.printStackTrace();
		} catch (IOException e) {
			// ignore exceptions for now
			e.printStackTrace();
		}

	}

	public static void main(String[] args) throws Exception {

//		ApprovalTaskParam appTask = new ApprovalTaskParam();
//		
//		appTask.setVirtualArray("virtualArray");
//		appTask.setVirtualPool("virtualPool");
//		appTask.setProject("project");
//		
//		appTask.setProcessDefinitionKey("simpleApprovalProcess");
//		List<NameValueParam> variables = new ArrayList<ApprovalTaskParam.NameValueParam>();
//
//		
//		variables.add(appTask.new NameValueParam("mailcontent","Mail Message")); 
//		variables.add(appTask.new NameValueParam("to","manoj.jain@emc.com")); 			
//		appTask.setVariables(variables);
//		
//		Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
//		
//
//
//		//Gson uglyJson = new Gson();
//
//		String pretJson = prettyGson.toJson(appTask);
//
//		//System.out.println("Pretty printing: "+pretJson);
//
//		//String ugJason = uglyJson.toJson(appTask);
//
//		//System.out.println("\nUgly printing: "+ugJason);
		String inputNew = "{ \"processDefinitionKey\":\"simpleApprovalProcess\", \"variables\": [{\"name\":\"mailcontent\",\"value\":\"Hello Administrator,A new request for provisioning has arrived.Please view the request and take action.<a href=\\\"http://lglbv240.lss.emc.com:9090/activiti-explorer/#tasks?category=inbox\\\">View and Approve</a>\"},{\"name\":\"to\",\"value\":\"manoj.jain@emc.com\"}]}";

		CreateVolumeExtension createVolumeExtension = new CreateVolumeExtension();
		String extenalTaskParam=inputNew;
		createVolumeExtension.preLaunch(extenalTaskParam);
		createVolumeExtension.postLaunch(extenalTaskParam);
		//createVolumeExtension.postLaunch(extenalTaskParam);
	}

}
