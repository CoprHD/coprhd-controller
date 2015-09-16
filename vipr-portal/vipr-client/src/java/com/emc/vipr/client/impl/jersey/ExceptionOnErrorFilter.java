/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;

/**
 * Jersey filter that throws an Exception if the status code is 4xx or 5xx. Without this, Jersey
 * will try to parse the response and you will get a parse exception.
 */
public class ExceptionOnErrorFilter extends ClientFilter {
    private Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        ClientResponse response = getNext().handle(request);
        int status = response.getStatus();
        if (status >= 400 && status < 600) {
            if (isSupportedType(response.getType())) {
                ServiceErrorRestRep serviceError;
                try {
                    serviceError = response.getEntity(ServiceErrorRestRep.class);
                } catch (Exception e) {
                    // Cause to fall-through to default exception
                    log.error("Error parsing error message", e);
                    serviceError = null;
                }
                if (serviceError != null) {
                    logAndThrow(new ServiceErrorException(status, serviceError));
                }
            }

            // Fallback for unknown entity types
            String content = response.getEntity(String.class);
            logAndThrow(new ViPRHttpException(status, content));
        }
        return response;
    }

    private void logAndThrow(RuntimeException e) {
        log.error(e.getMessage());
        throw e;
    }

    private boolean isSupportedType(MediaType type) {
        return type.isCompatible(MediaType.APPLICATION_JSON_TYPE) ||
                type.isCompatible(MediaType.APPLICATION_XML_TYPE) ||
                type.isCompatible(MediaType.TEXT_XML_TYPE);
    }
}
