/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.exceptions;

import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class ComputeSystemControllerException extends DeviceControllerException {

    private static final long serialVersionUID = -8288154084627128837L;

    public static final ComputeSystemControllerExceptions exceptions = ExceptionMessagesProxy.create(ComputeSystemControllerExceptions.class);
    
    protected ComputeSystemControllerException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }

}
