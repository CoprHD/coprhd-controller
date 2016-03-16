/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import javax.ws.rs.core.Response.Status;

public class InternalServerErrorException extends APIException {
    private static final long serialVersionUID = -6028952643011072799L;

    protected InternalServerErrorException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(Status.INTERNAL_SERVER_ERROR, code, cause, detailBase, detailKey, detailParams);
    }
}
