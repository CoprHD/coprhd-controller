/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package jobs.vipr;

import java.util.concurrent.Callable;

import play.libs.F.Promise;

import com.emc.vipr.client.ViPRSystemClient;

/**
 * Base class for asynchronous VIPR sys API calls.
 * 
 * @param <T>
 *        the result type of the call.
 */
public abstract class ViPRSysCall<T> implements Callable<T> {
    protected final ViPRSystemClient client;

    /**
     * Creates the call with the provided ViPR client.
     * 
     * @param client
     *        the ViPR client.
     */
    public ViPRSysCall(ViPRSystemClient client) {
        if (client == null) {
            throw new IllegalArgumentException("ViPR system client cannot be null");
        }
        this.client = client;
    }

    public Promise<T> asPromise() {
        return CallableHelper.createPromise(this);
    }
}
