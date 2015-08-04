/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public abstract class SecurityException extends InternalException {
    private static final long serialVersionUID = 6742660231897419785L;

    public static final FatalSecurityExceptions fatals = ExceptionMessagesProxy
            .create(FatalSecurityExceptions.class);
    public static final RetryableSecurityExceptions retryables = ExceptionMessagesProxy
            .create(RetryableSecurityExceptions.class);

    protected SecurityException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }
}
