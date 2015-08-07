/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class DiscoveryException extends InternalException {

    private static final long serialVersionUID = 814640565295751451L;

    /** Holds the methods used to create discovery plugin related exceptions */
    public static final DiscoveryExceptions exceptions = ExceptionMessagesProxy.create(DiscoveryExceptions.class);

    /** Holds the methods used to create discovery plugin related error conditions */
    public static final DiscoveryErrors errors = ExceptionMessagesProxy.create(DiscoveryErrors.class);

    protected DiscoveryException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }
}
