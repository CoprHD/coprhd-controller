/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class RetryableClientControllerException extends ClientControllerException {
    private static final long serialVersionUID = -7442262822497235733L;

    protected RetryableClientControllerException(ServiceCode code, Throwable cause,
            String detailBase, String detailKey, Object[] detailParams) {
        super(true, code, cause, detailBase, detailKey, detailParams);
    }
}
