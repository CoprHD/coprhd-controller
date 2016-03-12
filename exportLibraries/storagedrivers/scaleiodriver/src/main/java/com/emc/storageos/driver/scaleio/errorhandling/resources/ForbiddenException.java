/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import javax.ws.rs.core.Response.Status;

public class ForbiddenException extends APIException {
    private static final long serialVersionUID = -6015892577660682201L;

    private ForbiddenException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(Status.FORBIDDEN, code, cause, detailBase, detailKey, detailParams);
    }
}
