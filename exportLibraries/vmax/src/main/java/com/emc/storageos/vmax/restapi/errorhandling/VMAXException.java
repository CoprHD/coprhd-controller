/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception class to be used by VMAX component to throw respective
 * exceptions based on the functionality failures.
 * 
 */
public class VMAXException extends InternalException {
    /**
     * 
     */
    private static final long serialVersionUID = -928476570650119018L;

    /** Holds the methods used to create UNISPHERE related exceptions */
    public static final VMAXExceptions exceptions = ExceptionMessagesProxy.create(VMAXExceptions.class);

    /** Holds the methods used to create UNISPHERE related error conditions */
    public static final VMAXErrors errors = ExceptionMessagesProxy.create(VMAXErrors.class);

    protected VMAXException(ServiceCode code, Throwable cause, String detailBase, String detailKey,
            Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
        // TODO Auto-generated constructor stub
    }

}
