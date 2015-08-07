/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception thrown from VPlex API library.
 */
@SuppressWarnings("serial")
public class DataDomainResourceNotFoundException extends InternalException {

    /** Holds the methods used to create DataDomain related exceptions */
    public static final DataDomainResourceNotFoundExceptions notFound = ExceptionMessagesProxy
            .create(DataDomainResourceNotFoundExceptions.class);

    private DataDomainResourceNotFoundException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey,
            final Object[] params) {
        super(code.isRetryable(), code, cause, detailBase, detailKey, params);
    }
}
