package com.emc.sa.service.vipr.plugins.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.emc.sa.service.vipr.plugins.tasks.ApprovalTaskParam.NameValueParam;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/*
 		String inputNew = "{ \"processDefinitionKey\":\"simpleApprovalProcess\", \"variables\": [{\"name\":\"mailcontent\",\"value\":\"Hello Administrator,A new request for provisioning has arrived.Please view the request and take action.<a href=\\\"http://lglbv240.lss.emc.com:9090/activiti-explorer/#tasks?category=inbox\\\">View and Approve</a>\"},{\"name\":\"to\",\"value\":\"manoj.jain@emc.com\"}]}";

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
	    	
	    	String[] jsonXpathTokens=xpath.split(".");
	    	
	    	Map<String, Object> jsonMap = new HashMap<String,Object>();
	    	jsonMap = (Map<String, Object>)GSON.fromJson(json, jsonMap.getClass());
	    	
//	    	for( String jsonXpathToken : jsonXpathTokens){
//	    		jsonMap=jsonMap.get(jsonXpathToken);
//	    	}
	    	
	    	
	    	ObjectMapper mapper = new ObjectMapper();
	    	Object jsonObj = mapper.readValue(json, Object.class);
	    	Object xpathObj = PropertyUtils.getProperty(jsonObj, xpath);
	    	return classz.cast(xpathObj);

	    }
	    
	    public Object traverseMap(Map<String, Object> jsonMap,String jsonXpathToken) {
	    	Object nested=jsonMap.get(jsonXpathToken);
	    
	    return nested;
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
			
			TaskParamParser.getJsonXpathPropert(ugJason,"variables.to",String.class);

		}
		

}
