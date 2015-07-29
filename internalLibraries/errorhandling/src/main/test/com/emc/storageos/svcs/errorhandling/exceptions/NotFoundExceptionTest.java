/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.exceptions;

import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_PARAMETER_NOT_FOUND;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_URL_ENTITY_NOT_FOUND;
import static com.sun.jersey.api.client.ClientResponse.Status.BAD_REQUEST;
import static com.sun.jersey.api.client.ClientResponse.Status.NOT_FOUND;

import java.net.URI;

import org.junit.Test;

import com.emc.storageos.svcs.errorhandling.mappers.BaseServiceCodeExceptionTest;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.NotFoundException;

public class NotFoundExceptionTest extends BaseServiceCodeExceptionTest {

    private static final String MESSAGE_NOT_FOUND_IN_URL = "Unable to find entity specified in URL with the given id ";
    private static final String MESSAGE_NOT_FOUND_IN_REQUEST = "Unable to find entity with the given id ";

    @Test
    public void idNotFoundInURL() {
        final URI id = knownId;
        final NotFoundException exception = APIException.notFound.unableToFindEntityInURL(id);
        assertException(MESSAGE_NOT_FOUND_IN_URL + id, API_URL_ENTITY_NOT_FOUND.getCode(),
                "Unable to find entity in request URL", NOT_FOUND.getStatusCode(), exception);
    }

    @Test
    public void idNotFoundInParam() {
        final URI id = knownId;
        final APIException exception = APIException.badRequests.unableToFindEntity(id);
        assertException(MESSAGE_NOT_FOUND_IN_REQUEST + id, API_PARAMETER_NOT_FOUND.getCode(), "Request parameter cannot be found",
                BAD_REQUEST.getStatusCode(), exception);
    }
}
