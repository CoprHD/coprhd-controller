/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.io.IOException;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

public class ServiceClientRetryFilter extends ClientFilter {
    private static final Logger log = LoggerFactory
            .getLogger(ServiceClientRetryFilter.class);
    private int maxRetries;
    private int retryInterval;

    public ServiceClientRetryFilter(int maxRetries, int retryInterval) {
        log.info("filter created");
        if (maxRetries < 0 || retryInterval < 0) {
            throw new IllegalArgumentException("Invalid request retries specified.");
        }
        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;
    }

    @Override
    public ClientResponse handle(ClientRequest clientRequest)
            throws ClientHandlerException {
        int i = 0;
        Throwable cause = null;

        int sleepMs = 0;
        while (i < maxRetries) {

            i++;
            try {
                ClientResponse response = getNext().handle(clientRequest);

                ClientResponse.Status status = response.getClientResponseStatus();
                if (status == ClientResponse.Status.UNAUTHORIZED) {
                    log.error("401 Unauthorized, Please check the secret_key and certificate_chain.");
                    throw APIException.unauthorized.requestNotAuthorized();
                }
                if (status != ClientResponse.Status.SERVICE_UNAVAILABLE) {
                    return response;
                }
                log.info("remote service is unavailable req={} status={}", clientRequest.getURI(), status);
            } catch (ClientHandlerException e) {
                StackTraceElement[] stackTrace = e.getStackTrace();

                log.info("jersey client error. StackTrace=" + Arrays.toString(stackTrace));
                Class clazz = e.getCause().getClass();
                if (clazz == ConnectException.class) {
                    log.info("connection failed: {}", clientRequest.getURI().toString());
                } else if (clazz == NoRouteToHostException.class) {
                    log.info("no route to host: {}", clientRequest.getURI().toString());
                } else if (clazz == SocketException.class) {
                    log.info("socket exception: {}", clientRequest.getURI().toString());
                } else if (clazz == SocketTimeoutException.class) {
                    log.info("socket timeout exception: {}", clientRequest.getURI().toString());
                } else if (clazz == IOException.class) {
                    log.info("IO exception: {}", clientRequest.getURI().toString());
                } else {
                    cause = e;
                    break;
                }
                cause = e;
            }
            sleepMs = (i - 1) * retryInterval;
            log.info("request failed, retry {} times", i);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignore) {
                    log.error("Unexpected Error", ignore);
                }
            }
        }
        throw new ClientHandlerException("Request retries limit exceeded.", cause);
    }
}
