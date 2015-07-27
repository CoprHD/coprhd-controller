/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller.exceptions;

import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class CustomConfigControllerException extends DeviceControllerException {

    private static final long serialVersionUID = -8288154084627128837L;

    public static final CustomConfigControllerExceptions exceptions = ExceptionMessagesProxy.create(CustomConfigControllerExceptions.class);
    
    protected CustomConfigControllerException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }

}

