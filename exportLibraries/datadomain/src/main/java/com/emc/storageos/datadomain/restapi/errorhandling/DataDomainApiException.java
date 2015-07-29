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
public class DataDomainApiException extends InternalException {

    /** Holds the methods used to create DataDomain related exceptions */
    public static final DataDomainApiExceptions exceptions = ExceptionMessagesProxy.create(DataDomainApiExceptions.class);

    /** Holds the methods used to create DataDomain related error conditions */
    public static DataDomainApiErrors errors = ExceptionMessagesProxy.create(DataDomainApiErrors.class);

    private DataDomainApiException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey,
            final Object[] params) {
        super(code.isRetryable(), code, cause, detailBase, detailKey, params);
    }
}
