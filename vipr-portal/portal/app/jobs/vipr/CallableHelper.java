/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import java.util.concurrent.Callable;

import play.jobs.JobsPlugin;
import play.libs.F.Promise;

/**
 * Helper class for calling a Callable and returning a Promise.
 */
public class CallableHelper {
    public static <T> Promise<T> createPromise(final Callable<T> callable) {
        final Promise<T> promise = new Promise<T>();
        JobsPlugin.executor.submit(new Callable<T>() {
            public T call() throws Exception {
                try {
                    T result = callable.call();
                    promise.invoke(result);
                    return result;
                }
                catch (Exception t) {
                    promise.invokeWithException(t);
                    return null;
                }
            }
        });
        return promise;
    }
}
