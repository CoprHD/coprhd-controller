/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.recoverpoint;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Class for RecoverPoint discovery and metering exceptions
 */
@SuppressWarnings("serial")
public class RecoverPointCollectionException extends BaseCollectionException {
    public static final int ERROR_CODE_ISILON_EXCEPTION = -1;

    public int getErrorCode() {
        return -1;
    }

    protected RecoverPointCollectionException(final boolean retryable, final ServiceCode serviceCode,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, serviceCode, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public RecoverPointCollectionException(Throwable e) {
        super(e);
    }

    @Deprecated
    public RecoverPointCollectionException(String message) {
        super(message);
    }
}
