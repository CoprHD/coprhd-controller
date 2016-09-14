package com.emc.storageos.vasa.async;

import java.util.concurrent.Future;

public interface AsyncJobInterface <T> {

	// for synchronous
    public T executeSynchronous(final String action) throws AsyncJobException;

    // for asynchronous
    public Future<T> executeAsynchronous(final String action);
    
    public void shutdown();
    
}