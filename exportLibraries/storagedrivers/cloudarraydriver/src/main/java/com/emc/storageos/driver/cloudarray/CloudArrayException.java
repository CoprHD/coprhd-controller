/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.cloudarray;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class CloudArrayException extends InternalException {

    private static final long serialVersionURI = 12311L;

    public static final CloudArrayExceptions exceptions = ExceptionMessagesProxy.create(CloudArrayExceptions.class);

    public static final CloudArrayErrors errors = ExceptionMessagesProxy.create(CloudArrayErrors.class);

    protected CloudArrayException(boolean retryable, ServiceCode code, Throwable cause, String detailBase, String detailKey,
            Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }
}
