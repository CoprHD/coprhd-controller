/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.errorhandling;

import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.CONTROLLER_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.COORDINATOR_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.COORDINATOR_SVC_NOT_FOUND;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.IO_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_METHOD_NOT_SUPPORTED;

import java.net.URI;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.netflix.astyanax.connectionpool.exceptions.UnknownException;

@Path("/")
public class ErrorHandlingTestResource {

    protected static final String EXCEPTION_MESSAGE = "This is a test message";
    protected static final String ENDPOINT_KEY = "endpointKey";
    protected static final String TAG = "tag";
    protected static final String VERSION_1 = "1";
    protected static final String DBSVC = "dbsvc";

    private Exception createException() {
        return new Exception(EXCEPTION_MESSAGE);
    }

    @GET
    @Path("methodNotAllowed")
    public Response methodNotAllowed() {
        throw new ServiceCodeException(API_METHOD_NOT_SUPPORTED, "method {0} not allowed",
                new Object[] { "thismethod" });
    }

    @GET
    @Path("errorStartingService")
    public Response errorStartingService() {
        final Exception e = createException();
        throw new FatalCoordinatorException(COORDINATOR_ERROR, e,
                "Error starting coordinator service. Caused by: {0}",
                new Object[] { e.getMessage() });
    }

    @GET
    @Path("unableToLocateService")
    public Response unableToLocateService() {
        throw new RetryableCoordinatorException(
                COORDINATOR_SVC_NOT_FOUND,
                "Unable to locate service with name: {0}, version: {1}, tag: {2} and end point key: {3}",
                new Object[] { DBSVC, VERSION_1, TAG, ENDPOINT_KEY });
    }

    @GET
    @Path("entityInactive/{id}")
    public Response entityInactive(@PathParam("id") URI id) {
        throw DatabaseException.fatals.unableToFindEntity(id);
    }

    @GET
    @Path("unsupportedType")
    public Response unsupportedType() {
        throw DatabaseException.fatals.serializationFailedUnsupportedType("column");
    }

    @GET
    @Path("connectionException")
    public Response connectionException() {
        throw DatabaseException.retryables.connectionFailed(new UnknownException(EXCEPTION_MESSAGE));
    }

    @GET
    @Path("idInPath/{id}")
    public Response idInPath(@PathParam("id") URI id) {
        throw APIException.notFound.unableToFindEntityInURL(id);
    }

    @GET
    @Path("idNotInPath")
    public Response idNotInPath(@QueryParam("id") URI id) {
        throw APIException.badRequests.unableToFindEntity(id);
    }

    @GET
    @Path("createDir/{dirPath}")
    public Response unableToCreateDir(@PathParam("dirPath") String dirPath) {
        throw new ServiceCodeException(IO_ERROR,
                "Unable to create server id directories: {0}",
                new Object[] { dirPath });
    }

    @GET
    @Path("missingOrInvalid/{fieldName}")
    public Response missingOrInvalid(@PathParam("fieldName") String fieldName) {
        throw APIException.badRequests.requiredParameterMissingOrEmpty(fieldName);
    }

    @GET
    @Path("invalidContext")
    public Response invalidContext() {
        throw APIException.forbidden.invalidSecurityContext();
    }

    @GET
    @Path("noPoolFound")
    public Response noPoolFound(@QueryParam("vpool") URI vpoolId,
            @QueryParam("varray") URI varrayId) {
        throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpoolId.toString(), varrayId.toString());
    }

    @GET
    @Path("noDelete/{id}")
    public Response noDelete(@PathParam("id") URI id) {
        throw APIException.internalServerErrors.unableToDeleteRpVolume(id);
    }

    @GET
    @Path("snapshotFailed")
    public Response snapshotFailed() {
        throw new ServiceCodeException(CONTROLLER_ERROR,
                "Non-protected volume or snapshot add volume failed", null);
    }

    @GET
    @Path("noTenantForUser")
    public Response noTenantForUser() {
        throw APIException.badRequests.noTenantDefinedForUser("myUser");
    }

    // Known non-ServiceCodeExceptions

    @GET
    @Path("illegalArgumentException")
    public Response illegalArgumentException() {
        throw new IllegalArgumentException("Parameter not valid");
    }

    @GET
    @Path("illegalStateException")
    public Response illegalStateException() {
        throw new IllegalStateException("IllegalState");
    }

    @GET
    @Path("illegalStateExceptionWithCause")
    public Response illegalStateExceptionWithCause() {
        throw new IllegalStateException("IllegalState", DatabaseException.fatals.serializationFailedUnsupportedType("column"));
    }

    // WebApplication Tests

    @GET
    @Path("webApplicationException/{status}")
    public Response webApplicationException(@PathParam("status") int status) {
        throw new WebApplicationException(status);
    }

    // Other Exceptions

    @GET
    @Path("nullPointerException")
    public Response nullPointerException() {
        throw new NullPointerException("This message is null");
    }
}
