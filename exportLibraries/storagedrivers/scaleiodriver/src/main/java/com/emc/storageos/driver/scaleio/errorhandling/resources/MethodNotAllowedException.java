/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import com.sun.jersey.api.client.ClientResponse;

public class MethodNotAllowedException extends APIException {
    private static final long serialVersionUID = 5909342104246700241L;

    private MethodNotAllowedException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(ClientResponse.Status.METHOD_NOT_ALLOWED, code, cause, detailBase, detailKey, detailParams);
    }
}
