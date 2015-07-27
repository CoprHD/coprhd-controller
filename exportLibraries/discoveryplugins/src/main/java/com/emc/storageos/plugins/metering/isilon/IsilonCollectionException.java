/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.isilon;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;



/**
 * Class for Isilon discovery and metering exceptions
 */
public class IsilonCollectionException extends BaseCollectionException {
    public static final int ERROR_CODE_ISILON_EXCEPTION = -1;

    public int getErrorCode() {
        return -1;
    }

    protected IsilonCollectionException(final boolean retryable, final ServiceCode serviceCode, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(retryable, serviceCode, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public  IsilonCollectionException(Throwable e) {
        super(e);
    }

    @Deprecated
    public  IsilonCollectionException(String message) {
        super(message);
    }
}
