/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@SuppressWarnings("serial")
public class XtremIOApiException extends InternalException {

   
    private XtremIOApiException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    /** Holds the methods used to create xtremio related exceptions */
    public static final XtremIOApiExceptions exceptions = ExceptionMessagesProxy
            .create(XtremIOApiExceptions.class);

    /** Holds the methods used to create xtremio related error conditions */
    public static XtremIOErrors errors = ExceptionMessagesProxy.create(XtremIOErrors.class);

}
