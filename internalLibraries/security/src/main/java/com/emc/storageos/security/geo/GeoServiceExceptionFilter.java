/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.geo;

import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.security.geo.exceptions.GeoException;
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
public class GeoServiceExceptionFilter extends ClientFilter {
    private Logger log = LoggerFactory.getLogger(GeoServiceExceptionFilter.class);

    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        ClientResponse response = getNext().handle(request);
        int status = response.getStatus();
        if (status >= 400 && status < 600) {
            try {
                ServiceErrorRestRep serviceError = response.getEntity(ServiceErrorRestRep.class);
                if (serviceError != null) {
                    logAndThrow(status, serviceError);
                }
                else {
                    String content = response.getEntity(String.class);
                    logAndThrow(status, content);
                }
            }
            catch (Exception e) {
                // Cause to fall-through to default exception
                log.error("Failed to parse exception from the remote VDC. Parsing error message", e);
                String content = response.getEntity(String.class);
                logAndThrow(status, content);

            }

            // Fallback for unknown entity types
         }
        return response;
    }

    private void logAndThrow(int status, String error) {
        log.error("Remote VDC request failed with HTTP status {}. {}", error);
        throw GeoException.fatals.remoteVDCException(status, error);
    }

    private void logAndThrow(int status, ServiceErrorRestRep serviceError) {
        log.error("Remote VDC request failed with HTTP status {}. {}", status, serviceError.toString());
        throw GeoException.fatals.remoteVDCException(status, serviceError.toString());
    }

}
