/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.vplex;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Class for VPlex discovery and metering exceptions
 */
public class VPlexCollectionException extends BaseCollectionException {

    private static final long serialVersionUID = 6727682695672258685L;

    /** Holds the methods used to create discovery plugin related exceptions */
    public static final VPlexCollectionExceptions exceptions = ExceptionMessagesProxy.create(VPlexCollectionExceptions.class);

    protected VPlexCollectionException(final ServiceCode serviceCode,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(serviceCode.isRetryable(), serviceCode, cause, detailBase, detailKey, detailParams);
    }

    public int getErrorCode() {
        return ServiceCode.VPLEX_DATA_COLLECTION_EXCEPTION.getCode();
    }
}
