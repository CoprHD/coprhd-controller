/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.plugins.metering.vnxfile;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Represents a VNX File Discovery Error.
 */
public class VNXFileCollectionException extends BaseCollectionException {

    protected VNXFileCollectionException(final boolean retryable, final ServiceCode serviceCode, final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, serviceCode, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public VNXFileCollectionException(String message,Throwable cause) {
        super(message, cause);
    }

    @Deprecated
    public VNXFileCollectionException(String message) {
        super(message);
    }

    @Override
    public int getErrorCode() {
        return -1;
    }
}
