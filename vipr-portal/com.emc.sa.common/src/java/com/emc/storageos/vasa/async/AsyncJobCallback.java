package com.emc.storageos.vasa.async;


import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
 
public abstract class AsyncJobCallback<T> implements  AsyncJobInterface <T> {

	
    private final ExecutorService executor;

    public AsyncJobCallback(ExecutorService executor) {
        this.executor = executor;
    }

    // note, final so it cannot be overridden in a sub class.
    // note, action is final so it can be passed to the callable.
    public final Future<T> executeAsynchronous(final String jobIdentifier) {

        Callable<T> task = new Callable<T>() {

            @Override
            public T call() throws AsyncJobException {
                return executeSynchronous(jobIdentifier);
            }

        };

        return executor.submit(task);
    }
    

	public void shutdown() {
		executor.shutdown();
		
	}
 
}