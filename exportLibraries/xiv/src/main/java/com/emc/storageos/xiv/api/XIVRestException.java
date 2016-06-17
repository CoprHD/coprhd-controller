/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xiv.api;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * XIV REST Exception
 */
public class XIVRestException extends InternalException {
    private static final long serialVersionUID = 8903079831758201184L;

    /** Holds the methods used to create ECS related exceptions */
    public static final XIVRestExceptions exceptions = ExceptionMessagesProxy.create(XIVRestExceptions.class);

    private XIVRestException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
