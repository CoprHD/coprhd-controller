/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception class to be used by HDS component to throw respective
 * exceptions based on the functionality failures.
 * 
 */
public class HDSException extends InternalException {

    private static final long serialVersionUID = -690567868124567639L;

    /** Holds the methods used to create SMIS related exceptions */
    public static final HDSExceptions exceptions = ExceptionMessagesProxy.create(HDSExceptions.class);

    /** Holds the methods used to create SMIS related error conditions */
    public static final HDSErrors errors = ExceptionMessagesProxy.create(HDSErrors.class);

    private HDSException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

}
