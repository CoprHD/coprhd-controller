/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.mappers;

import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_METHOD_NOT_SUPPORTED;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_PARAMETER_INVALID;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_SERVICE_UNAVAILABLE;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.CONTROLLER_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.COORDINATOR_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.COORDINATOR_SVC_NOT_FOUND;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.DBSVC_CONNECTION_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.IO_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS;
import static com.sun.jersey.api.client.ClientResponse.Status.BAD_REQUEST;
import static com.sun.jersey.api.client.ClientResponse.Status.FORBIDDEN;
import static com.sun.jersey.api.client.ClientResponse.Status.INTERNAL_SERVER_ERROR;
import static com.sun.jersey.api.client.ClientResponse.Status.METHOD_NOT_ALLOWED;
import static com.sun.jersey.api.client.ClientResponse.Status.SERVICE_UNAVAILABLE;
import static java.text.MessageFormat.format;
import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.svcs.errorhandling.resources.ForbiddenException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;

@SuppressWarnings("deprecation")
public abstract class BaseServiceCodeExceptionTest {

    private static final class TestUriInfo implements UriInfo {
        @Override
        public UriBuilder getRequestUriBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI getRequestUri() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultivaluedMap<String, String> getQueryParameters(
                final boolean arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultivaluedMap<String, String> getQueryParameters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PathSegment> getPathSegments(final boolean arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PathSegment> getPathSegments() {
            final PathSegment segment = new PathSegment() {
                @Override
                public String getPath() {
                    return BaseServiceCodeExceptionTest.knownId.toString();
                }

                @Override
                public MultivaluedMap<String, String> getMatrixParameters() {
                    throw new UnsupportedOperationException();
                }
            };
            // return a known list for PathSegments
            final ArrayList<PathSegment> segments = new ArrayList<PathSegment>();
            segments.add(segment);
            return segments;
        }

        @Override
        public MultivaluedMap<String, String> getPathParameters(
                final boolean arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultivaluedMap<String, String> getPathParameters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPath(final boolean arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getMatchedURIs(final boolean arg0) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getMatchedURIs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Object> getMatchedResources() {
            throw new UnsupportedOperationException();
        }

        @Override
        public UriBuilder getBaseUriBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI getBaseUri() {
            throw new UnsupportedOperationException();
        }

        @Override
        public UriBuilder getAbsolutePathBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI getAbsolutePath() {
            throw new UnsupportedOperationException();
        }
    }

    protected static final String EXCEPTION_MESSAGE = "This is a test message";

    private static ServiceCodeExceptionMapper mapper;
    private static UriInfo uriInfo;
    protected static URI knownId;

    @BeforeClass
    //Suppress Sonar violation of Lazy initialization of static fields should be synchronized
    //Junit test will be called in single thread by default, it's safe to ignore this violation
    @SuppressWarnings("squid:S2444")
    public static void setUpBeforeClass() {
        uriInfo = new TestUriInfo();
        mapper = new ServiceCodeExceptionMapper();
        mapper.info = uriInfo;
        knownId = createTestId("DataObject");
    }

    protected void assertServiceError(final String expectedMessage,
            final int expectedServiceCode, final String expectedDescription,
            final ServiceErrorRestRep actualError) {
        assertEquals("Service codes do not match", expectedServiceCode,
                actualError.getCode());
        assertEquals("Service code descriptions do not match",
                expectedDescription, actualError.getCodeDescription());
        assertEquals("Exception detailed messages do not match", expectedMessage,
                actualError.getDetailedMessage());
    }

    protected void assertException(final String expectedMessage,
            final int expectedServiceCode, final String expectedDescription,
            final int expectedStatus, final Exception actualException) {
        final Response response = mapper.toResponse(actualException);
        assertResponse(expectedStatus, response);
        Object entity = response.getEntity();
        Assert.assertTrue("The response is not a ServiceError",
                entity instanceof ServiceErrorRestRep);
        final ServiceErrorRestRep error = (ServiceErrorRestRep) entity;
        assertServiceError(expectedMessage, expectedServiceCode,
                expectedDescription, error);

    }

    private void assertResponse(final int expectedStatus,
            final Response actualResponse) {
        assertEquals("The HTTP Status codes do not match", expectedStatus,
                actualResponse.getStatus());
    }

    protected Exception createException() {
        return new Exception(EXCEPTION_MESSAGE);
    }

    protected void assertIoError(final String message,
            final ServiceCodeException exception) {
        assertException(message, IO_ERROR.getCode(), "An IO error occurred, please check the ViPR logs for more information",
                INTERNAL_SERVER_ERROR.getStatusCode(), exception);
    }

    protected void assertServiceUnavailable(final String message, final InternalException exception) {
        assertException(message, API_SERVICE_UNAVAILABLE.getCode(), "Unable to connect to the service. The service is unavailable, try again later",
                SERVICE_UNAVAILABLE.getStatusCode(), exception);
    }

    protected void assertDbsvcServiceUnavailable(final String message, final InternalException exception) {
        assertException(message, DBSVC_CONNECTION_ERROR.getCode(), "Unable to connect to the dbsvc service. The service is unavailable, try again later",
                SERVICE_UNAVAILABLE.getStatusCode(), exception);
    }

    protected void assertServiceUnavailable(final String message,
            final ServiceCodeException exception) {
        assertException(message, API_SERVICE_UNAVAILABLE.getCode(),
                "Unable to connect to the service. The service is unavailable, try again later",
                SERVICE_UNAVAILABLE.getStatusCode(), exception);
    }

    protected void assertCoordinatorError(final String message, final InternalException exception) {
        assertException(message, COORDINATOR_ERROR.getCode(),
                "An error occurred in the coordinator", INTERNAL_SERVER_ERROR.getStatusCode(),
                exception);
    }

    protected void assertCoordinatorSvcNotFound(final String message,
            final InternalException exception) {
        assertException(message, COORDINATOR_SVC_NOT_FOUND.getCode(),
                "The coordinator was unable to locate the service",
                SERVICE_UNAVAILABLE.getStatusCode(), exception);
    }

    protected void assertApiBadParameters(final String message, final ServiceCodeException exception) {
        assertException(message, API_PARAMETER_INVALID.getCode(), "Parameter was provided but invalid", BAD_REQUEST.getStatusCode(), exception);
    }

    protected void assertApiInsufficientPermissions(final String message,
            final ForbiddenException exception) {
        assertException(message, SECURITY_INSUFFICIENT_PERMISSIONS.getCode(),
                "This operation is forbidden for this resource using the specified credentials",
                FORBIDDEN.getStatusCode(), exception);
    }

    protected void assertApiMethodNotAllowed(final String message, final ServiceCodeException exception) {
        assertException(message, API_METHOD_NOT_SUPPORTED.getCode(),
                "Method not supported",
                METHOD_NOT_ALLOWED.getStatusCode(), exception);
    }

    protected void assertApiError(final String message, final ServiceCodeException exception) {
        assertException(message, API_ERROR.getCode(), "An error occurred in the API service",
                INTERNAL_SERVER_ERROR.getStatusCode(), exception);
    }

    protected void assertControllerError(final String message, final ServiceCodeException exception) {
        assertException(message, CONTROLLER_ERROR.getCode(),
                "An error occurred in the controller", INTERNAL_SERVER_ERROR.getStatusCode(),
                exception);
    }

    protected static URI createTestId(String type) {
        return URI.create(format("urn:storageos:{0}:{1}:", type, randomUUID()));
    }
    
    protected void assertInternalException(final StatusType expectedStatus, final ServiceCode expectedCode, final String expectedMessage, final InternalException actualServiceCoded) {
        final Response response = mapper.toResponse(actualServiceCoded);
        assertResponse(expectedStatus.getStatusCode(), response);
        Object entity = response.getEntity();
        Assert.assertTrue("The response is not a ServiceError",
                entity instanceof ServiceErrorRestRep);
        final ServiceErrorRestRep error = (ServiceErrorRestRep) entity;
        assertServiceError(expectedMessage, expectedCode.getCode(),
                expectedCode.getSummary(), error);
    }

}
