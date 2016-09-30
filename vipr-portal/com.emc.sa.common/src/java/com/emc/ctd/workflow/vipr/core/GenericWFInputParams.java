package com.emc.ctd.workflow.vipr.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.google.gson.Gson;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

public class GenericWFInputParams {
	

	private Object workflowInputParams;
	
	private Object getWorkflowInputParams() {
		return workflowInputParams;
	}
	
	@SuppressWarnings("unchecked")
	public static   Map<String, Object> jsonObjectParser(String resourcePath){
		Gson gson = new Gson();
		InputStream is =  GenericWFInputParams.class.getClassLoader().getResourceAsStream(resourcePath);
		final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		GenericWFInputParams genericWFInputParams = gson.fromJson(reader, GenericWFInputParams.class);
		Map<String, Object> objectHashMap = new HashMap<String,Object>();
        
    	objectHashMap = (Map<String, Object>)genericWFInputParams.getWorkflowInputParams();
    	return objectHashMap;
	}
	
	@SuppressWarnings("unchecked")
	public static   void jsonObjectBuilder(String resourcePath){
        String name="name";
        String size="2";
        String sizeGB="2GB";
        Integer count=1;
        String arrayType="SMI";
        String ipAddress="ipAddress";
        String pool="pool";
        URI vpool=uri("1234");
        URI varray=uri("1234");
        URI project=uri("1234");
        List<URI> hosts;
        Map<String, String> passThroughParamPool = new LinkedHashMap<String, String>();;
        String passThroughParamExport = "direct";
        
		Gson gson = new Gson();
		
        VolumeCreate volumeDriver = new VolumeCreate();
        passThroughParamPool.put("storage-pool", pool);
        passThroughParamPool.put("storage-system", ipAddress);
        List<URI> volumes = new ArrayList<URI>();
        
        volumeDriver.setName(name);
        volumeDriver.setCount(count);
        volumeDriver.setSize(size+"GB");
        volumeDriver.setVarray(varray);
        volumeDriver.setVpool(vpool);
        volumeDriver.setProject(project);
        
        volumeDriver.setPassThroughParams(passThroughParamPool);
        
        String str=gson.toJson(volumeDriver);
        
        System.out.println(str);

        
  

	}
	
	public static void main(String[] args) throws Exception {
		Gson gson = new Gson();
		Map<String, Object> jsonMap =GenericWFInputParams.jsonObjectParser("test"+".json");
		
		//VolumeCreate volumeCreate = gson.fromJson( gson.toJson(jsonMap), VolumeCreate.class);
		
		ExportCreateParam param= gson.fromJson (gson.toJson(jsonMap), ExportCreateParam.class);
		
		System.out.println("ExportCreateParam param "+param);

        
        
//		Map<String, Object> jsonMap =GenericWFInputParams.jsonObjectParser("CreateExportVolume"+".json");
//        
//        String volumeName = (String)jsonMap.get("volumeName");// TaskParamParser.getJsonXpathPropert((externalParams, "project", String.class);
//        System.out.println(" Workflow input volumeName = "+volumeName);
//        
       // GenericWFInputParams.jsonObjectBuilder(null);
        

	}
}
