/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class RetryableCoordinatorException extends CoordinatorException {
    private static final long serialVersionUID = -590223416221981966L;

    protected RetryableCoordinatorException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(true, code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public RetryableCoordinatorException(final ServiceCode code, final String pattern,
            final Object[] parameters) {
        super(code, null, pattern, parameters);
    }

    @Deprecated
    public RetryableCoordinatorException(final ServiceCode code, final Throwable cause,
            final String pattern, final Object[] parameters) {
        super(code, cause, pattern, parameters);
    }
}
