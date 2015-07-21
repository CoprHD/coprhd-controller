/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.placement.PlacementExceptions;

public abstract class ClientControllerException extends ControllerException {
    private static final long serialVersionUID = -3614074563018856533L;

    public static final FatalClientControllerExceptions fatals = ExceptionMessagesProxy
            .create(FatalClientControllerExceptions.class);
    public static final RetryableClientControllerExceptions retryables = ExceptionMessagesProxy
            .create(RetryableClientControllerExceptions.class);
    public static final PlacementExceptions placementExceptions = 
            ExceptionMessagesProxy.create(PlacementExceptions.class);

    protected ClientControllerException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }
}
