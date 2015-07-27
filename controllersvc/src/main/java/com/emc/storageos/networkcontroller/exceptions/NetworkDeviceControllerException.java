/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.exceptions;

import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class NetworkDeviceControllerException extends DeviceControllerException {

    private static final long serialVersionUID = -6900306922128500639L;

    /** Holds the methods used to create network devices related exceptions */
    public static final NetworkDeviceControllerExceptions exceptions = ExceptionMessagesProxy.create(NetworkDeviceControllerExceptions.class);

    /** Holds the methods used to create network devices related error conditions */
    public static final NetworkDeviceControllerErrors errors = ExceptionMessagesProxy.create(NetworkDeviceControllerErrors.class);

    protected NetworkDeviceControllerException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public NetworkDeviceControllerException(ServiceCode serviceCode, final String pattern,
            final Object[] parameters) {
        super(serviceCode == ServiceCode.UNFORSEEN_ERROR ? ServiceCode.CONTROLLER_NETWORK_ERROR
                : serviceCode, pattern, parameters);
    }

    @Deprecated
    public NetworkDeviceControllerException(ServiceCode serviceCode, final Throwable cause,
            final String pattern, final Object[] parameters) {
        super(serviceCode == ServiceCode.UNFORSEEN_ERROR ? ServiceCode.CONTROLLER_NETWORK_ERROR
                : serviceCode, cause, pattern, parameters);
    }

    @Deprecated
    public NetworkDeviceControllerException(final String pattern, final Object[] parameters) {
        super(ServiceCode.CONTROLLER_NETWORK_ERROR, pattern, parameters);
    }

    @Deprecated
    public NetworkDeviceControllerException(final Throwable cause, final String pattern,
            final Object[] parameters) {
        super(ServiceCode.CONTROLLER_NETWORK_ERROR, cause, pattern, parameters);
    }

    /**
     * Do not use this constructor, use any of the ones that are not deprecated
     */
    @Deprecated
    public NetworkDeviceControllerException() {
        super(ServiceCode.CONTROLLER_NETWORK_ERROR, "No details available for this error", null);
    }

    /**
     * Do not use this constructor, use any of the ones that are not deprecated
     */
    @Deprecated
    public NetworkDeviceControllerException(String msg) {
        super(ServiceCode.CONTROLLER_NETWORK_ERROR, msg, null);
    }

    /**
     * Do not use this constructor, use any of the ones that are not deprecated
     */
    @Deprecated
    public NetworkDeviceControllerException(Throwable cause) {
        super(ServiceCode.CONTROLLER_NETWORK_ERROR, cause, "Caused by: {0}", new Object[] { cause
                .getMessage() });
    }

    /**
     * Do not use this constructor, use any of the ones that are not deprecated
     */
    @Deprecated
    public NetworkDeviceControllerException(String msg, Throwable cause) {
        super(ServiceCode.CONTROLLER_NETWORK_ERROR, cause, "{0}. Caused by: {1}", new Object[] {
                msg, cause.getMessage() });
    }
}
