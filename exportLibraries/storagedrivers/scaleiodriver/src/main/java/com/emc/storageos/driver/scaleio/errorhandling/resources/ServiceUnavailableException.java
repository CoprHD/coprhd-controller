/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import javax.ws.rs.core.Response.Status;

public class ServiceUnavailableException extends APIException {
    private static final long serialVersionUID = -2011349372777314716L;

    private ServiceUnavailableException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(Status.SERVICE_UNAVAILABLE, code, cause, detailBase, detailKey, detailParams);
    }
}
