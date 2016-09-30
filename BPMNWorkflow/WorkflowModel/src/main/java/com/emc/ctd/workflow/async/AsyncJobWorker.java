package com.emc.ctd.workflow.async;



import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class AsyncJobWorker extends AsyncJobCallback<String> {

    public AsyncJobWorker() {
        super(Executors.newSingleThreadExecutor());
    }

    @Override
    public String executeSynchronous(String msg) throws AsyncJobException {

        
        
        System.out.println(" Task Output ="+msg);
        
        return msg;

    }


    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
    	// set up some system, need the JSON data
    	AsyncJobWorker asSyncJob = new AsyncJobWorker();
    	try {
			String json = asSyncJob.executeSynchronous("MANOJ Sync");
		} catch (AsyncJobException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	AsyncJobWorker asASyncJob = new AsyncJobWorker();
    	
    	Future<String> res = asASyncJob.executeAsynchronous("AMnoj Async");
    	String result=res.get();
    	
    	System.out.println("Call back "+result);
    	
    	asASyncJob.shutdown();
    	
	}


}