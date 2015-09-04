/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class RetryableSecurityException extends SecurityException {
    private static final long serialVersionUID = -8729416157152090206L;

    protected RetryableSecurityException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(true, code, cause, detailBase, detailKey, detailParams);
    }
}
