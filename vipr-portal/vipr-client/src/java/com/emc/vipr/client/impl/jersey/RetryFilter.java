/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ViPRException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryFilter extends ClientFilter {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private int maxRetries;
    private int retryInterval;

    public RetryFilter(int maxRetries, int retryInterval) {
        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;
    }

    @Override
    public ClientResponse handle(ClientRequest clientRequest) {
        Throwable cause = null;
        for (int retryCount = 1; retryCount <= maxRetries; retryCount++) {
            try {
                ClientResponse response = getNext().handle(clientRequest);
                return response;
            }
            catch (ServiceErrorException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                cause = e;
            }
            log.info("Request failed {}, retrying (count: {})", clientRequest.getURI().toString(), retryCount);
            try {
                Thread.sleep(retryInterval);
            }
            catch (InterruptedException exception) {
                // Ignore this
            }
        }
        throw new ViPRException("Retry limit exceeded", cause);
    }
}