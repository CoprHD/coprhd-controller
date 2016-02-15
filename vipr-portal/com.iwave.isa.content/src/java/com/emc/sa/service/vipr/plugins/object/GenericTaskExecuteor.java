package com.emc.sa.service.vipr.plugins.object;

import java.net.URI;

import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.service.ExternalTaskApdapterInterface;
import com.emc.sa.service.vipr.tasks.WaitForTask;
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
    	TaskInfo taskInfo = genericExtensionTask.execute();
    	System.out.println(taskInfo.toDisplayString());
    	
    	genericExtensionTask.getStatus();
    	genericExtensionTask.getStatus();
    	RestClient client = null;
        TaskResourceRep taskResp = new TaskResourceRep();
    	return new Task<GenericRestRep>(client, taskResp, GenericRestRep.class);



    }
}
