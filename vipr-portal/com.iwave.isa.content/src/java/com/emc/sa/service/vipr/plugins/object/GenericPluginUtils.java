package com.emc.sa.service.vipr.plugins.object;


import java.util.Map;


import com.emc.sa.engine.extension.ExternalTaskApdapterInterface;
import com.emc.sa.engine.extension.ExternalTaskParams;
import com.emc.vipr.client.Task;

public class GenericPluginUtils {

//	public static Map<String, Object> createParam(ExternalTaskParams genericPluginParams) {
//		// TODO Auto-generated method stub
//		return null;
//	}


	public static void executeExtenstionTask(ExternalTaskApdapterInterface genericExtensionTask,ExternalTaskParams genericExtensionTaskParams) throws Exception {
		
		GenericTaskExecuteor viprTask = converExternalTaskApdapterToViprTask(genericExtensionTask,genericExtensionTaskParams);
		Task<GenericRestRep> task=viprTask.doExecute();
		System.out.println("Task "+task.toString());
 		
	}
	
	private static void executeExtenstionTaskExecute(ExternalTaskApdapterInterface genericExtensionTask,ExternalTaskParams genericExtensionTaskParams) throws Exception {
		GenericTaskExecuteor viprTask = converExternalTaskApdapterToViprTask(genericExtensionTask,genericExtensionTaskParams);
		Task<GenericRestRep> task=viprTask.doExecute();
		System.out.println("Task "+task.toString());
		
	}


	private static void executeExtenstionTaskPostLaunch(ExternalTaskApdapterInterface genericExtensionTask,ExternalTaskParams genericExtensionTaskParams) throws Exception {
		GenericTaskExecuteor viprTask = converExternalTaskApdapterToViprTask(genericExtensionTask,genericExtensionTaskParams);
		Task<GenericRestRep> task=viprTask.doPostLaunch();
		System.out.println("Task "+task.toString());
		
	}

	private static void executeExtenstionTaskPreLaunch(ExternalTaskApdapterInterface genericExtensionTask,ExternalTaskParams genericExtensionTaskParams) throws Exception {
		GenericTaskExecuteor viprTask = converExternalTaskApdapterToViprTask(genericExtensionTask,genericExtensionTaskParams);
		Task<GenericRestRep> task=viprTask.doPreLaunch();
		System.out.println("Task "+task.toString());
		
	}



	public static GenericTaskExecuteor converExternalTaskApdapterToViprTask(ExternalTaskApdapterInterface genericExtensionTask, ExternalTaskParams genericExtensionTaskParams){
		GenericTaskExecuteor externalTask = new GenericTaskExecuteor(genericExtensionTask, genericExtensionTaskParams);
		return externalTask;
		
		
	}


	public static void executeExtenstionTask(ExternalTaskApdapterInterface genericExtensionTask,ExternalTaskParams genericExtensionTaskParams, String phase) throws Exception {
		if(phase.equals("preLaunch")){
				executeExtenstionTaskPreLaunch(genericExtensionTask,genericExtensionTaskParams);
		} else if (phase.equals("postLaunch")){
				executeExtenstionTaskPostLaunch(genericExtensionTask,genericExtensionTaskParams);
		} else if (phase.equals("execute")){
				executeExtenstionTaskExecute(genericExtensionTask,genericExtensionTaskParams);
		}
		
	}




}
