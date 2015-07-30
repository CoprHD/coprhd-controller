/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cimadapter.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception that is thrown when a {@link ConnectionManager} API fails.
 */
public class CIMAdapterException extends InternalException {

    private static final long serialVersionUID = 1L;

    /** Holds the methods used to create NetApp related exceptions */
    public static final CIMAdapterExceptions exceptions = ExceptionMessagesProxy.create(CIMAdapterExceptions.class);

    /** Holds the methods used to create NetApp related error conditions */
    public static final CIMAdapterErrors errors = ExceptionMessagesProxy.create(CIMAdapterErrors.class);

    protected CIMAdapterException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }
}