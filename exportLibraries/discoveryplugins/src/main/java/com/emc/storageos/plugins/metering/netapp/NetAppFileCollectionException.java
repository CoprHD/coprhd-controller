/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.netapp;


import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Class for NA discovery and metering exceptions
 */
public class NetAppFileCollectionException extends BaseCollectionException {
    public static final int ERROR_CODE_NA_EXCEPTION = -1;

    @Override
    public int getErrorCode() {
        return ERROR_CODE_NA_EXCEPTION;
    }

    protected NetAppFileCollectionException(final boolean retryable,  final ServiceCode serviceCode,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, serviceCode, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public NetAppFileCollectionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Deprecated
    public NetAppFileCollectionException(String message) {
        super(message);
    }

}
