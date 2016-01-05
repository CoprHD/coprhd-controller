/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.glance.errorhandling;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@SuppressWarnings("serial")
public class GlanceApiException extends InternalException {

	private GlanceApiException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) 
	{
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    /** Holds the methods used to create glance related exceptions */
    public static final GlanceExceptions exceptions = ExceptionMessagesProxy.create(GlanceExceptions.class);

    /** Holds the methods used to create glance related error conditions */
    public static GlanceErrors errors = ExceptionMessagesProxy.create(GlanceErrors.class);

}
