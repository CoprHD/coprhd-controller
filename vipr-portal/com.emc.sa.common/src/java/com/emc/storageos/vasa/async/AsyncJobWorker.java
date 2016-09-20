package com.emc.storageos.vasa.async;



import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class AsyncJobWorker extends AsyncJobCallback<TaskInfo> {

    public AsyncJobWorker() {
        super(Executors.newSingleThreadExecutor());
    }

    @Override
    public TaskInfo executeSynchronous(String msg) throws AsyncJobException {

        
        
        System.out.println(" Task Output ="+msg);
        
        TaskInfo ti= new TaskInfo();
        ti.setName(msg);
        return ti;
    }


    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
    	// set up some system, need the JSON data
    	AsyncJobWorker asSyncJob = new AsyncJobWorker();
    	try {
			TaskInfo json = asSyncJob.executeSynchronous("MANOJ Sync");
		} catch (AsyncJobException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	AsyncJobWorker asASyncJob = new AsyncJobWorker();
    	
    	Future<TaskInfo> res = asASyncJob.executeAsynchronous("AMnoj Async");
    	TaskInfo result=res.get();
    	
    	System.out.println("Call back "+result);
    	
    	asASyncJob.shutdown();
    	
	}


}