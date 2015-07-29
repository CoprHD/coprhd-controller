/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.mappers;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.junit.Test;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.sun.jersey.api.client.ClientResponse;

/**
 * Test class for testing how other Exceptions are handled by the
 * ServiceCodeExceptionMapper
 * 
 * @author fountt1
 */
public class ServiceCodeExceptionMapperTest extends BaseServiceCodeExceptionTest {

    // Known non-ServiceCodeExceptions

    @Test
    public void illegalArgumentException() {
        final IllegalArgumentException exception = new IllegalArgumentException(
                "Parameter not valid");
        assertException("Parameter not valid", 1008, "Parameter was provided but invalid",
                400, exception);
    }

    @Test
    public void illegalStateException() {
        final IllegalStateException exception = new IllegalStateException("IllegalState");
        assertException("IllegalState", 999,
                "An unexpected error occurred, please check the ViPR logs for more information",
                500, exception);
    }

    @Test
    public void illegalStateExceptionWithCause() {
        final IllegalStateException exception = new IllegalStateException(
                "IllegalState", APIException.serviceUnavailable.dummy());
        assertException("Dummy failure for testing with", ServiceCode.API_SERVICE_UNAVAILABLE.getCode(),
                "Unable to connect to the service. The service is unavailable, try again later", ServiceCode.API_SERVICE_UNAVAILABLE
                        .getHTTPStatus().getStatusCode(), exception);
    }

    // WebApplication Tests

    @Test
    public void webApplicationExceptionBadRequest() {
        final WebApplicationException exception = new WebApplicationException(Status.BAD_REQUEST);
        assertException(null, 1013, "Bad request body", 400, exception);
    }

    @Test
    public void webApplicationExceptionUnauthorized() {
        final WebApplicationException exception = new WebApplicationException(Status.UNAUTHORIZED);
        assertException(null, 4000,
                "Invalid credentials or authentication token provided to access to this resource",
                401, exception);
    }

    @Test
    public void webApplicationExceptionForbidden() {
        final WebApplicationException exception = new WebApplicationException(Status.FORBIDDEN);
        assertException(null, 3000,
                "This operation is forbidden for this resource using the specified credentials",
                403, exception);
    }

    @Test
    public void webApplicationExceptionNotFound() {
        final WebApplicationException exception = new WebApplicationException(
                Status.NOT_FOUND);
        assertException(null, 2000,
                "Unable to find entity in request URL", 404, exception);
    }

    @Test
    public void webApplicationExceptionMethodNotAllowed() {
        final WebApplicationException exception = new WebApplicationException(
                ClientResponse.Status.METHOD_NOT_ALLOWED.getStatusCode());
        assertException(null, 1007, "Method not supported", 405, exception);
    }

    @Test
    public void webApplicationExceptionServiceUnavaliable() {
        final WebApplicationException exception = new WebApplicationException(
                Status.SERVICE_UNAVAILABLE);
        assertException(null, 6000,
                "Unable to connect to the service. The service is unavailable, try again later",
                503, exception);
    }

    // Other Exceptions

    @Test
    public void nullPointerException() {
        final NullPointerException exception = new NullPointerException("This message is null");
        assertException("This message is null", 999,
                "An unexpected error occurred, please check the ViPR logs for more information",
                500, exception);
    }

    @Test
    public void jsonMappingException() {
        final JsonMappingException exception = new JsonMappingException(
                "Failed to map JSON content to a Java class");
        assertException("Failed to map JSON content to a Java class", 1013, "Bad request body",
                400, exception);
    }

    @Test
    public void jsonParseException() {
        final JsonParseException exception = new JsonParseException(
                "Failed to parse the JSON content", null);
        assertException("Failed to parse the JSON content", 1013, "Bad request body", 400,
                exception);
    }
}
