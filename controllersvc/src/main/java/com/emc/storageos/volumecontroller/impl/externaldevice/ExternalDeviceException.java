/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.volumecontroller.impl.externaldevice;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception class to be used by ExternalDevice component to throw respective
 * exceptions based on the functionality failures.
 *
 */
public class ExternalDeviceException extends InternalException {

    private static final long serialVersionUID = -690567868124547639L;

    /** Holds the methods used to create ExternalDevice related exceptions */
    public static final ExternalDeviceExceptions exceptions = ExceptionMessagesProxy.create(ExternalDeviceExceptions.class);

    /** Holds the methods used to create ExternalDevice related error conditions */
    public static final ExternalDeviceErrors errors = ExceptionMessagesProxy.create(ExternalDeviceErrors.class);

    private ExternalDeviceException(final ServiceCode code, final Throwable cause,
                             final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
