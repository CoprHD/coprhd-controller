/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.geo.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public abstract class GeoException extends InternalException {
	
    private static final long serialVersionUID = -5710177174026419089L;

    public static FatalGeoExceptions fatals = ExceptionMessagesProxy
            .create(FatalGeoExceptions.class);

//    public static RetryableGeoExceptions retryables = ExceptionMessagesProxy
//            .create(RetryableDatabaseExceptions.class);

    protected GeoException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }
}
