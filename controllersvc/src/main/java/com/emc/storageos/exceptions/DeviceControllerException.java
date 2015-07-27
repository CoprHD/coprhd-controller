/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;

public class DeviceControllerException extends ControllerException {
    private static final long serialVersionUID = -2957761645104077997L;

    public static final DeviceControllerErrors errors = ExceptionMessagesProxy
            .create(DeviceControllerErrors.class);
    public static final DeviceControllerExceptions exceptions = ExceptionMessagesProxy
            .create(DeviceControllerExceptions.class);

    protected DeviceControllerException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    /**
     * @param cause
     * @param pattern
     * @param params
     * @deprecated
     */
    @Deprecated
    public DeviceControllerException(final Throwable cause, final String pattern,
            final Object[] params) {
        super(cause, pattern, params);
    }

    /**
     * @param pattern
     * @param params
     * @deprecated
     */
    @Deprecated
    public DeviceControllerException(final String pattern, final Object[] params) {
        super(pattern, params);
    }

    /**
     * @param message
     * @param cause
     * @deprecated
     */
    @Deprecated
    public DeviceControllerException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @deprecated
     */
    @Deprecated
    public DeviceControllerException(final String message) {
        super(message);
    }

    /**
     * @param cause
     * @deprecated
     */
    @Deprecated
    public DeviceControllerException(final Throwable cause) {
        super(cause);
    }

    @Deprecated
    public DeviceControllerException(ServiceCode serviceCode, final Throwable cause,
            final String pattern, final Object[] parameters) {
        super(serviceCode == ServiceCode.UNFORSEEN_ERROR ? ServiceCode.CONTROLLER_ERROR
                : serviceCode, cause, pattern, parameters);
    }

    @Deprecated
    public DeviceControllerException(ServiceCode serviceCode, final String pattern,
            final Object[] parameters) {
        super(serviceCode == ServiceCode.UNFORSEEN_ERROR ? ServiceCode.CONTROLLER_ERROR
                : serviceCode, null, pattern, parameters);
    }
}
