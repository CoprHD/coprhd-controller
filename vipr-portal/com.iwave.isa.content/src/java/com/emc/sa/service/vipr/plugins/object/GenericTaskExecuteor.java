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

    //        TaskResourceRep task = client.postURI(TaskResourceRep.class, input, uri);
    //return new Task<>(client, task, resourceClass);


	@Override
    public Task<GenericRestRep> doExecute() throws Exception {
    	ExternalTaskParams taskParams = new ExternalTaskParams();
    	//GenericPluginServiceHelper genericPluginServiceHelper = new GenericPluginServiceHelper();
    	//BindingUtils.bind(genericPluginServiceHelper, GenericPluginUtils.createParam(taskParams));
    	TaskInfo taskInfo = genericExtensionTask.executeExternal(taskParams.getExternalParam());
    	System.out.println(taskInfo.toDisplayString()+ "  params= " +params);
    	
    	genericExtensionTask.getStatus();
    	genericExtensionTask.getStatus();
    	RestClient client = null;
        TaskResourceRep taskResp = new TaskResourceRep();
       
    	return new Task<GenericRestRep>(client, taskResp, GenericRestRep.class);



    }

	public Task<GenericRestRep> doPostLaunch() throws Exception {
	   	ExternalTaskParams taskParams = new ExternalTaskParams();
    	//GenericPluginServiceHelper genericPluginServiceHelper = new GenericPluginServiceHelper();
    	//BindingUtils.bind(genericPluginServiceHelper, GenericPluginUtils.createParam(taskParams));
    	genericExtensionTask.postLaunch(taskParams.getExternalParam());
    	System.out.println("doPostLaunch");
    	TaskInfo taskInfo = new TaskInfo();
		taskInfo.setProgress(100);
		taskInfo.setTaskState("SUCCESS");
		
		taskInfo.setResult("Extrenal Task Completed");
    	genericExtensionTask.getStatus();
    	genericExtensionTask.getStatus();
    	RestClient client = null;
        TaskResourceRep taskResp = new TaskResourceRep();
    	return new Task<GenericRestRep>(client, taskResp, GenericRestRep.class);
	}

	public Task<GenericRestRep> doPreLaunch() throws Exception {
    	ExternalTaskParams taskParams = new ExternalTaskParams();
    	genericExtensionTask.executeExternal(taskParams.getExternalParam());
    	System.out.println("doPreLaunch");
    	
    	genericExtensionTask.getStatus();
    	genericExtensionTask.getStatus();
    	RestClient client = null;
        TaskResourceRep taskResp = new TaskResourceRep();
    	return new Task<GenericRestRep>(client, taskResp, GenericRestRep.class);
	}
}
