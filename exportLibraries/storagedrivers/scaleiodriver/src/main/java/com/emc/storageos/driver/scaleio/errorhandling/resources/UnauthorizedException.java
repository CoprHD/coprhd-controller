/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import javax.ws.rs.core.Response.Status;

public class UnauthorizedException extends APIException {
    private static final long serialVersionUID = 2478199921982501705L;

    private UnauthorizedException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(Status.UNAUTHORIZED, code, cause, detailBase, detailKey, detailParams);
    }
}
