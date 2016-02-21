package com.emc.sa.service.vipr.plugins.object;

import java.net.URI;






import com.emc.sa.engine.extension.ExternalTaskApdapterInterface;
import com.emc.sa.engine.extension.ExternalTaskParams;
import com.emc.sa.service.vipr.tasks.WaitForTask;
//import com.emc.sa.engine.bind.BindingUtils;
//import com.emc.sa.service.vipr.plugins.object.GenericRestRep;
//import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.vasa.async.TaskInfo;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.impl.RestClient;

public class GenericTaskExecuteor extends WaitForTask<GenericRestRep>  {
	
	ExternalTaskParams params;
	ExternalTaskApdapterInterface genericExtensionTask;


	
    public GenericTaskExecuteor(ExternalTaskApdapterInterface genericExtensionTask, ExternalTaskParams genericExtensionTaskParams) {
    	this.params=genericExtensionTaskParams;
    	this.genericExtensionTask=genericExtensionTask;
	}


	@Override
    public Task<GenericRestRep> doExecute() throws Exception {
     	TaskInfo taskInfo = genericExtensionTask.executeExternal(params.getExternalParam());
    	System.out.println("Vipr External Taskinfo doExecute Return "+taskInfo.toDisplayString());
    	
    	genericExtensionTask.getStatus();
    	genericExtensionTask.getStatus();
    	RestClient client = null;
        TaskResourceRep taskResp = new TaskResourceRep();
       
    	return new Task<GenericRestRep>(client, taskResp, GenericRestRep.class);

    }

	public Task<GenericRestRep> doPostLaunch() throws Exception {
    	genericExtensionTask.postLaunch(params.getExternalParam());
    	System.out.println("Vipr External Taskinfo doPostLaunch Return");
    	TaskInfo taskInfo = new TaskInfo();
		taskInfo.setProgress(100);
		taskInfo.setTaskState("SUCCESS");
		

    	genericExtensionTask.getStatus();
    	genericExtensionTask.getStatus();
    	RestClient client = null;
        TaskResourceRep taskResp = new TaskResourceRep();
    	return new Task<GenericRestRep>(client, taskResp, GenericRestRep.class);
	}

	public Task<GenericRestRep> doPreLaunch() throws Exception {
     	genericExtensionTask.executeExternal(params.getExternalParam());
     	System.out.println("Vipr External Taskinfo doPreLaunch Return");;
    	TaskInfo taskInfo = new TaskInfo();
		taskInfo.setProgress(100);
		taskInfo.setTaskState("SUCCESS");
		
    	genericExtensionTask.getStatus();
    	genericExtensionTask.getStatus();
    	RestClient client = null;
        TaskResourceRep taskResp = new TaskResourceRep();
    	return new Task<GenericRestRep>(client, taskResp, GenericRestRep.class);
	}
}
