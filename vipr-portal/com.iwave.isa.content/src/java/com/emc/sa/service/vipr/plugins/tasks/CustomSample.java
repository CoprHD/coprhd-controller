/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.plugins.tasks;


import java.net.URI;
import java.net.URISyntaxException;


import com.emc.sa.engine.extension.ExternalTaskApdapterInterface;
import com.emc.storageos.vasa.async.TaskInfo;


public class CustomSample implements ExternalTaskApdapterInterface {
//public class CustomSample extends ExecutionTask<Object> {
    private URI vpoolId;
    private URI varrayId;
    private URI projectId;
    private String size;
    private Integer count;
    private String name;
    private URI consistencyGroupId;
    private boolean test=true;

    public CustomSample(){
    	
    }
    public CustomSample(String vpoolId, String varrayId, String projectId, String size, Integer count,
            String name, String consistencyGroupId) throws URISyntaxException {
    	this(new URI(vpoolId), new URI(varrayId), new URI(projectId), size, count, name, new URI(consistencyGroupId));
        
    }

    public CustomSample(URI vpoolId, URI varrayId, URI projectId, String size, Integer count, String name,
            URI consistencyGroupId) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.size = size;
        this.count = count;
        this.name = name;
        this.consistencyGroupId = consistencyGroupId;
    }
 	@Override
	public void init() throws Exception {
		System.out.println("Custom Task init");
		
	}
	@Override
	public void precheck() throws Exception {
		System.out.println("Custom Task precheck");
		
	}

	@Override
	public void preLaunch(String extenalTaskParam) throws Exception {
		System.out.println("Custom Task preLaunch");
		
	}	
	@Override
	public TaskInfo executeExternal(String extenalTaskParam) throws Exception {
		TaskInfo taskInfo= new TaskInfo();
		
		taskInfo.setProgress(100);
		taskInfo.setTaskState("SUCCESS");
		
		taskInfo.setResult("Extrenal Task Completed");
		return taskInfo;
	}

	@Override
	public void postcheck() throws Exception {
		System.out.println("Custom Task postcheck");
		
	}
	@Override
	public void postLaunch(String extenalTaskParam) throws Exception {
		System.out.println("Custom Task postLuanch");
		
	}
	@Override
	public void getStatus() throws Exception {
		System.out.println("Custom Task getStatus");
		
	}
	@Override
	public void destroy() {
		System.out.println("Custom Task destroy");
		
	}
	
	
	public static void main(String[] args) throws Exception {
		CustomSample customSample = new CustomSample();
		String extenalTaskParam=null;
		customSample.preLaunch(extenalTaskParam);
	}


}
