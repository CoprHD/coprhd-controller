/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.isilon.restapi;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/* Generic Exception for REST operation errors */
public class IsilonException extends InternalException {
    private static final long serialVersionUID = 8903079831758201184L;

    /** Holds the methods used to create Isilon related exceptions */
    public static final IsilonExceptions exceptions = ExceptionMessagesProxy.create(IsilonExceptions.class);

    /** Holds the methods used to create Isilon related error conditions */
    public static IsilonErrors errors = ExceptionMessagesProxy
    .create(IsilonErrors.class);

    private IsilonException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    /**
     * @param msg
     * @deprecated add a method to {@link IsilonExceptions} to create a new
     *             instance
     */
    @Deprecated
    public IsilonException(String msg) {
        super(ServiceCode.ISILON_ERROR, null, msg, null);
    }

    /**
     * @param msg
     * @param cause
     * @deprecated add a method to {@link IsilonExceptions} to create a new
     *             instance
     */
    @Deprecated
    public IsilonException(String msg, Throwable cause) {
        super(ServiceCode.ISILON_ERROR, cause, msg, null);
    }
}
