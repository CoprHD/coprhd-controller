/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import javax.ws.rs.core.Response.Status;

public class BadRequestException extends APIException {

    private static final long serialVersionUID = 3446545442170140259L;

    private BadRequestException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(Status.BAD_REQUEST, code, cause, detailBase, detailKey, detailParams);
    }
}
