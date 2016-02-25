package com.emc.storageosplugin.model.vce;

import java.util.List;



import org.apache.log4j.Logger;


import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

public class TaskUtils {
	
    private static final Logger log = Logger.getLogger( TaskUtils.class);


    public static <T> Boolean isAllTasksCompleted(Tasks<T> tasks) {
     
    	List<Task<T>> taskList = tasks.getTasks();
    	Boolean isSuccess=true;
         for (Task<T> task :  taskList) {
        	String logMessgae="";
            if (task.isComplete() && !task.isError()) {
            	logMessgae = task.getMessage() == null? "" : task.getMessage();
            }
            if (task.isComplete() && task.isError()) {
            	isSuccess=false;
                logMessgae = task.getMessage() == null? "" : task.getMessage();
            }
            
            log.info(logMessgae);
        }
        return isSuccess;
    }

	public static <T> Boolean isTasksCompleted(Task<T> task) {
    	Boolean isSuccess=true;
    	
     	String logMessgae="";
        if (task.isComplete() && !task.isError()) {
        	logMessgae = task.getMessage() == null? "" : task.getMessage();
        }
        if (task.isComplete() && task.isError()) {
        	isSuccess=false;
            logMessgae = task.getMessage() == null? "" : task.getMessage();
        }
        
        
        log.info(logMessgae);
        return isSuccess;

		
	}


}
