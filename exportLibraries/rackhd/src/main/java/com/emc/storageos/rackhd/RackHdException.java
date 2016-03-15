/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.rackhd;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception class to be used by RackHd component to throw respective
 * exceptions based on the functionality failures.
 * 
 */
public class RackHdException extends InternalException {
    
    private static final long serialVersionUID = 3614459185302263187L;

	/** Holds the methods used to create RackHd related exceptions */
    public static final RackHdExceptions exceptions = ExceptionMessagesProxy.create(RackHdExceptions.class);

    private RackHdException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
