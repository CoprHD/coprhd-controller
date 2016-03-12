/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import javax.ws.rs.core.Response.Status;

public class NotFoundException extends APIException {
    private static final long serialVersionUID = 5960321185685715584L;

    private NotFoundException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(Status.NOT_FOUND, code, cause, detailBase, detailKey, detailParams);
    }
}
