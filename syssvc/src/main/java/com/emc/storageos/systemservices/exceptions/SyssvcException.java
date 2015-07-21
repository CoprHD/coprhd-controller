/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public abstract class SyssvcException extends InternalException {
    protected SyssvcException(final boolean retryable, final ServiceCode code, final Throwable cause, final String detailBase, final String detailKey, final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    protected SyssvcException(final ServiceCode code, final Throwable cause, final String pattern, final Object[] params) {
        super(code, cause, pattern, params);
    }

    public static SyssvcExceptions syssvcExceptions = ExceptionMessagesProxy.create(SyssvcExceptions.class);
}
