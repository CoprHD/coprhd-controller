/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.ecs;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Class for ECS discovery and metering exceptions
 */
public class ECSCollectionException extends BaseCollectionException {
    @Override
    public int getErrorCode() {
        return -1;
    }
    
    public ECSCollectionException(final boolean retryable, final ServiceCode serviceCode, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(retryable, serviceCode, cause, detailBase, detailKey, detailParams);
    }
}
