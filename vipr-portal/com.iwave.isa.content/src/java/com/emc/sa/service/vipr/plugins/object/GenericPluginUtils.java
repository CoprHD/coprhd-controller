package com.emc.sa.service.vipr.plugins.object;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addRollback;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.Map;

import com.emc.sa.engine.service.ExternalTaskApdapterInterface;
import com.emc.sa.service.vipr.object.tasks.CreateBucket;
import com.emc.sa.service.vipr.object.tasks.DeactivateBucket;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;

public class GenericPluginUtils {

	public static Map<String, Object> createParam(ExternalTaskParams genericPluginParams) {
		// TODO Auto-generated method stub
		return null;
	}

/*
     public static URI createBucket(String bucketName, URI virtualArray, URI virtualPoolId, URI projectId, Double softQuota,
            Double hardQuota, String retention, String owner) {
        String softQuotaSize = gbToQuotaSize(softQuota);
        String hardQuotaSize = gbToQuotaSize(hardQuota);
        Task<BucketRestRep> task = execute(new CreateBucket(bucketName, virtualArray, virtualPoolId, projectId, softQuotaSize, 
                hardQuotaSize, retention, owner));
        addAffectedResource(task);
        URI bucketId = task.getResourceId();
        addRollback(new DeactivateBucket(bucketId));
        logInfo("object.bucket.task", bucketId, task.getOpId());
        return bucketId;
    }

 */

	public static void executeExtenstionTask(ExternalTaskApdapterInterface genericExtensionTask,ExternalTaskParams genericExtensionTaskParams) throws Exception {
		
		GenericTaskExecuteor viprTask = converExternalTaskApdapterToViprTask(genericExtensionTask,genericExtensionTaskParams);
		//Task<GenericRestRep> task = execute(viprTask);
		//Task<GenericRestRep> task = viprTask.d;
		Task<GenericRestRep> task=viprTask.doExecute();
        addAffectedResource(task);
        //URI resourceId = task.getResourceId();
        //addAffectedResource(resourceId);
        //addRollback(new DeactivateBucket(resourceId));
        //logInfo("executeExtenstionTask", resourceId, task.getOpId());
 		
	}
	
	private static void addAffectedResource(Task<GenericRestRep> task) {
		// TODO Auto-generated method stub
		
	}



	private static void addAffectedResource(URI resourceId) {
		// TODO Auto-generated method stub
		
	}



	public static GenericTaskExecuteor converExternalTaskApdapterToViprTask(ExternalTaskApdapterInterface genericExtensionTask, ExternalTaskParams genericExtensionTaskParams){
		GenericTaskExecuteor externalTask = new GenericTaskExecuteor(genericExtensionTask, genericExtensionTaskParams);
		return externalTask;
		
		
	}
	

}
