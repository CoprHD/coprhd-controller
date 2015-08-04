/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import java.util.concurrent.Callable;

import play.libs.F.Promise;

import com.emc.vipr.client.ViPRCoreClient;

/**
 * Base class for asynchronous VIPR calls.
 * 
 * @param <T>
 *            the result type of the call.
 */
public abstract class ViPRCall<T> implements Callable<T> {
    protected final ViPRCoreClient client;

    /**
     * Creates the call with the provided ViPR client.
     * 
     * @param client
     *            the ViPR client.
     */
    public ViPRCall(ViPRCoreClient client) {
        if (client == null) {
            throw new IllegalArgumentException("ViPR client cannot be null");
        }
        this.client = client;
    }

    public Promise<T> asPromise() {
        return CallableHelper.createPromise(this);
    }
}
