/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception thrown from when there is any exception
 * during metering storage device.
 */
public class StorageMeteringException extends DeviceControllerException {

    // Default serial version id.
    private static final long serialVersionUID = 1L;

    protected StorageMeteringException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }

    /**
     * Constructor.
     * 
     * @param msg The exception message.
     */
    @Deprecated
    public StorageMeteringException(String msg) {
        super(ServiceCode.METERING_STORAGE_ERROR, null, msg, null);
    }

    /**
     * Constructor.
     * 
     * @param cause The cause of the exception.
     */
    @Deprecated
    public StorageMeteringException(Throwable cause) {
        super(ServiceCode.METERING_STORAGE_ERROR, cause, null, null, null);
    }

    /**
     * Constructor.
     * 
     * @param msg The exception message.
     * @param cause The cause of the exception.
     */
    @Deprecated
    public StorageMeteringException(String msg, Throwable cause) {
        super(ServiceCode.METERING_STORAGE_ERROR, cause, null, msg, null);
    }
}
