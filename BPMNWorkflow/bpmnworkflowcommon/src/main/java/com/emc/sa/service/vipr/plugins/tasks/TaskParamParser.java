package com.emc.sa.service.vipr.plugins.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.emc.sa.service.vipr.plugins.tasks.ApprovalTaskParam.NameValueParam;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/*
 		String inputNew = "{ \"processDefinitionKey\":\"simpleApprovalProcess\", \"variables\": [{\"name\":\"mailcontent\",\"value\":\"Hello Administrator,A new request for provisioning has arrived.Please view the request and take action.<a href=\\\"http://lglbv240.lss.emc.com:9090/activiti-explorer/#tasks?category=inbox\\\">View and Approve</a>\"},{\"name\":\"to\",\"value\":\"manoj.jain@emc.com\"}]}";


String ExternalTaskParams  ={"virtualArray":"\"urn:storageos:VirtualArray:bf8cd0ff-5746-433b-a817-dd45c9f72633:vdc1\"","virtualPool":"\"urn:storageos:VirtualPool:757bb977-8a5b-4e26-9b06-4e6787520c0e:vdc1\"","project":"\"urn:storageos:Project:047dde9b-3049-45ec-8599-0101e10fff7c:global\"","name":"\"MYVOLEXT-21-1\"","size":"\"1\"","numberOfVolumes":"\"1\"","consistencyGroup":"\"\"","externalParam":"externalParam"}
 */
public class TaskParamParser {
	
	   private static final JsonParser PARSER = new JsonParser();
	    private static final Gson GSON = new Gson();

	    public  ApprovalTaskParam readJsonStream(String in) throws IOException {
	    	InputStream inStream = IOUtils.toInputStream(in, "UTF-8");
	        JsonObject taskParam = PARSER.parse(new InputStreamReader(inStream)).getAsJsonObject();
	        IOUtils.closeQuietly(inStream);


	        ApprovalTaskParam approvalTask = GSON.fromJson(taskParam, ApprovalTaskParam.class);

	        return approvalTask;
	    }
	
	    public static <T> T getJsonXpathPropert(String json,String xpath,Class<T> classz) throws Exception{	    	//actually it is a Map instance with maps-fields within
	    	
	    	Map<String, Object> jsonMap = new HashMap<String,Object>();
	    	jsonMap = (Map<String, Object>)GSON.fromJson(json, jsonMap.getClass());
	    	
	    	Object xpathObj = jsonMap.get(xpath);
	    	if(classz.equals(String.class)) {
	    		String jsonValue = (String) xpathObj;
	    		if(jsonValue.startsWith("\"") && jsonValue.endsWith("\"")) {
	    			xpathObj = jsonValue.substring(1, jsonValue.length()-1);
	    		}
	    	}
	    	return classz.cast(xpathObj);

	    }
	    
		public static void main(String[] args) throws Exception{

			ApprovalTaskParam appTask = new ApprovalTaskParam();
			
			appTask.setVirtualArray("virtualArray");
			appTask.setVirtualPool("virtualPool");
			appTask.setProject("project");
			
			appTask.setProcessDefinitionKey("simpleApprovalProcess");

			List<NameValueParam> variables = new ArrayList<ApprovalTaskParam.NameValueParam>();

			
			variables.add(appTask.new NameValueParam("mailcontent","Mail Message")); 
			variables.add(appTask.new NameValueParam("to","manoj.jain@emc.com")); 			
			//variables.put(key, value)
			appTask.setVariables(variables);
			
			

			Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

			Gson uglyJson = new Gson();

			String pretJson = prettyGson.toJson(appTask);

			System.out.println("Pretty printing: "+pretJson);

			String ugJason = uglyJson.toJson(appTask);

			System.out.println("\nUgly printing: "+ugJason);
			
			TaskParamParser.getJsonXpathPropert(ugJason,"variables.[1].value",String.class);

		}
		

}
