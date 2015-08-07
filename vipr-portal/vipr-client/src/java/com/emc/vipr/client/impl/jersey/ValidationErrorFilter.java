/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.catalog.impl.ApiListUtils;
import com.emc.vipr.model.catalog.ValidationError;
import com.emc.vipr.client.exceptions.ValidationException;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Error filter that handles portal validation errors (400).
 */
public class ValidationErrorFilter extends ClientFilter {
    private static final Logger LOG = LoggerFactory.getLogger(ValidationErrorFilter.class);

    private ClientConfig config;

    public ValidationErrorFilter(ClientConfig config) {
        this.config = config;
    }

    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        ClientResponse response = getNext().handle(request);
        int status = response.getStatus();
        if (supportsPortalValidation(request) && status == 400) {
            List<ValidationError> errorsList = ApiListUtils.getEntityList(config, new GenericType<List<ValidationError>>() {
            }, response);
            ValidationException exception = new ValidationException(response.getStatus(), errorsList);
            LOG.error(exception.getMessage());
            throw exception;
        }
        return response;
    }

    private boolean supportsPortalValidation(ClientRequest request) {
        String path = request.getURI().getPath();
        return path.contains("api/services") || path.contains("api/catalog");
    }
}
