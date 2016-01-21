/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.errorhandling;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Response.Status;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.api.service.impl.resource.StorageApplication;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.svcs.errorhandling.mappers.ServiceCodeExceptionMapper;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

public class ErrorHandlingTest {

    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    private static final int port = 12345;
    private static volatile Server _server;
    private Client client;
    private WebResource baseResource;

    @BeforeClass
    public static void setupServer() throws Exception {
        _server = new Server(port);

        // AuthN servlet filters
        ServletContextHandler rootHandler = new ServletContextHandler();
        rootHandler.setContextPath("/");
        _server.setHandler(rootHandler);

        final StorageApplication application = new StorageApplication();
        final Set<Object> resources = new HashSet<Object>();
        resources.add(new ErrorHandlingTestResource());
        resources.add(new ServiceCodeExceptionMapper());
        application.setResource(resources);
        final ResourceConfig config = new DefaultResourceConfig();
        config.add(application);
        rootHandler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");
        _server.start();
    }

    @AfterClass
    public static void shutdownServer() throws Exception {
        _server.stop();
    }

    @Before
    public void setupClient() {
        client = Client.create();
        baseResource = client.resource("http://localhost:" + port);
    }

    private void assertResponse(final ClientResponse actualResponse,
            final int expectedStatusCode, final int expectedServiceCode) {
        Assert.assertEquals(expectedStatusCode, actualResponse.getStatus());
        try {
            final ServiceErrorRestRep error = actualResponse
                    .getEntity(ServiceErrorRestRep.class);
            Assert.assertEquals(expectedServiceCode, error.getCode());
        } catch (final ClientHandlerException e) {
            Assert.fail("Expected a ServiceError object");
        }
    }

    @Test
    public void methodNotAllowedGet() {
        // methodNotAllowed method will throw a ServiceCodeException
        final ClientResponse response = baseResource.path("methodNotAllowed")
                .get(ClientResponse.class);
        final int statusCode = HTTP_METHOD_NOT_ALLOWED;
        final int serviceCode = ServiceCode.API_METHOD_NOT_SUPPORTED.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void methodNotAllowedPost() {
        // jersey will throw a WebApplicationException as we cannot post to the
        // base resource, the mapper should convert this to a
        // ServiceCodeException
        final ClientResponse response = baseResource.post(ClientResponse.class);
        final int statusCode = HTTP_METHOD_NOT_ALLOWED;
        final int serviceCode = ServiceCode.API_METHOD_NOT_SUPPORTED.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void errorStartingService() {
        // errorStartingService method will throw a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "errorStartingService").get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.COORDINATOR_ERROR.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void unableToLocateService() {
        // unableToLocateService method will throw a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "unableToLocateService").get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.COORDINATOR_SVC_NOT_FOUND.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void entityInactive() {
        // entityInactive method will throw a ServiceCodeException
        final URI id = URIUtil.createId(DataObject.class);
        final ClientResponse response = baseResource.path(
                "entityInactive/" + id).get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.DBSVC_ENTITY_NOT_FOUND.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void unsupportedType() {
        // unsupportedType method will throw a ServiceCodeException
        final ClientResponse response = baseResource.path("unsupportedType")
                .get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.DBSVC_SERIALIZATION_ERROR.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void connectionException() {
        // connectionException method will throw a ServiceCodeException
        final ClientResponse response = baseResource
                .path("connectionException").get(ClientResponse.class);
        final int statusCode = HTTP_SERVICE_UNAVAILABLE;
        final int serviceCode = ServiceCode.DBSVC_CONNECTION_ERROR.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void idInPath() {
        // idInPath method will throw a ServiceCodeException
        final URI id = URIUtil.createId(DataObject.class);
        final ClientResponse response = baseResource.path("idInPath/" + id)
                .get(ClientResponse.class);
        final int statusCode = HTTP_NOT_FOUND;
        final int serviceCode = ServiceCode.API_URL_ENTITY_NOT_FOUND.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void idNotInPath() {
        // idNotInPath method will throw a ServiceCodeException
        final URI id = URIUtil.createId(DataObject.class);
        final ClientResponse response = baseResource.path("idNotInPath")
                .queryParam("id", id.toString()).get(ClientResponse.class);
        final int statusCode = HTTP_BAD_REQUEST;
        final int serviceCode = ServiceCode.API_PARAMETER_NOT_FOUND.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void unableToCreateDir() {
        // unableToCreateDir method will throw a ServiceCodeException
        final String dirPath = "serverIdDir";
        final ClientResponse response = baseResource.path("createDir")
                .path(dirPath).get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.IO_ERROR.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void missingOrInvalid() {
        // missingOrInvalid method will throw a ServiceCodeException
        final String fieldName = "fieldName";
        final ClientResponse response = baseResource.path("missingOrInvalid")
                .path(fieldName).get(ClientResponse.class);
        final int statusCode = HTTP_BAD_REQUEST;
        final int serviceCode = ServiceCode.API_PARAMETER_MISSING.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void invalidContext() {
        // invalidContext method will throw a ServiceCodeException
        final ClientResponse response = baseResource.path("invalidContext")
                .get(ClientResponse.class);
        final int statusCode = HTTP_FORBIDDEN;
        final int serviceCode = ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void noPoolFound() {
        // noPoolFound method will throw a ServiceCodeException
        final URI cosId = URIUtil.createId(VirtualPool.class);
        final URI neighborhoodId = URIUtil.createId(VirtualArray.class);
        final ClientResponse response = baseResource.path("noPoolFound")
                .queryParam("vpool", cosId.toString())
                .queryParam("varray", neighborhoodId.toString())
                .get(ClientResponse.class);
        final int statusCode = HTTP_BAD_REQUEST;
        final int serviceCode = ServiceCode.API_NO_PLACEMENT_FOUND.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void noDelete() {
        // noDelete method will throw a ServiceCodeException
        final URI id = URIUtil.createId(Volume.class);
        final ClientResponse response = baseResource.path("noDelete")
                .path(id.toString()).get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.API_RP_VOLUME_DELETE_ERROR.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void noTenantForUser() {
        // noTenantForUser method will throw a ServiceCodeException
        final ClientResponse response = baseResource.path("noTenantForUser")
                .get(ClientResponse.class);
        final int statusCode = HTTP_BAD_REQUEST;
        final int serviceCode = ServiceCode.API_PARAMETER_NOT_FOUND.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    // Known non-ServiceCodeExceptions

    @Test
    public void illegalArgumentException() {
        // This method will throw an IllegalArgumentException, the mapper should
        // convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "illegalArgumentException").get(ClientResponse.class);
        final int statusCode = HTTP_BAD_REQUEST;
        final int serviceCode = ServiceCode.API_PARAMETER_INVALID.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void illegalStateException() {
        // This method will throw an IllegalStateException, the mapper should
        // convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "illegalStateException").get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.UNFORSEEN_ERROR.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void illegalStateExceptionWithCause() {
        // This method will throw an IllegalStateException, the mapper should
        // convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "illegalStateExceptionWithCause").get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.DBSVC_SERIALIZATION_ERROR.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    // WebApplication Tests

    @Test
    public void webApplicationExceptionBadRequest() {
        // This method will throw an WebApplicationException with the specified
        // status, the mapper should convert this to a ServiceCodeException
        final ClientResponse response = baseResource
                .path("webApplicationException/"
                        + Status.BAD_REQUEST.getStatusCode()).get(
                        ClientResponse.class);
        final int statusCode = HTTP_BAD_REQUEST;
        final int serviceCode = ServiceCode.API_BAD_REQUEST.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void webApplicationExceptionUnauthorized() {
        // This method will throw an WebApplicationException with the specified
        // status, the mapper should convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "webApplicationException/"
                        + Status.UNAUTHORIZED.getStatusCode()).get(
                ClientResponse.class);
        final int statusCode = HTTP_UNAUTHORIZED;
        final int serviceCode = ServiceCode.SECURITY_UNAUTHORIZED_OPERATION.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void webApplicationExceptionForbidden() {
        // This method will throw an WebApplicationException with the specified
        // status, the mapper should convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "webApplicationException/" + Status.FORBIDDEN.getStatusCode())
                .get(ClientResponse.class);
        final int statusCode = HTTP_FORBIDDEN;
        final int serviceCode = ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void webApplicationExceptionNotFound() {
        // This method will throw an WebApplicationException with the specified
        // status, the mapper should convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "webApplicationException/" + Status.NOT_FOUND.getStatusCode())
                .get(ClientResponse.class);
        final int statusCode = HTTP_NOT_FOUND;
        final int serviceCode = ServiceCode.API_URL_ENTITY_NOT_FOUND.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void webApplicationExceptionMethodNotAllowed() {
        // This method will throw an WebApplicationException with the specified
        // status, the mapper should convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "webApplicationException/"
                        + ClientResponse.Status.METHOD_NOT_ALLOWED.getStatusCode()).get(
                ClientResponse.class);
        final int statusCode = HTTP_METHOD_NOT_ALLOWED;
        final int serviceCode = ServiceCode.API_METHOD_NOT_SUPPORTED.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    @Test
    public void webApplicationExceptionServiceUnavaliable() {
        // This method will throw an WebApplicationException with the specified
        // status, the mapper should convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "webApplicationException/"
                        + Status.SERVICE_UNAVAILABLE.getStatusCode()).get(
                ClientResponse.class);
        final int statusCode = HTTP_SERVICE_UNAVAILABLE;
        final int serviceCode = ServiceCode.API_SERVICE_UNAVAILABLE.getCode();
        assertResponse(response, statusCode, serviceCode);
    }

    // Other Exceptions

    @Test
    public void nullPointerException() {
        // This method will throw an NullPointerException, the mapper should
        // convert this to a ServiceCodeException
        final ClientResponse response = baseResource.path(
                "nullPointerException").get(ClientResponse.class);
        final int statusCode = HTTP_INTERNAL_SERVER_ERROR;
        final int serviceCode = ServiceCode.UNFORSEEN_ERROR.getCode();
        assertResponse(response, statusCode, serviceCode);
    }
}
