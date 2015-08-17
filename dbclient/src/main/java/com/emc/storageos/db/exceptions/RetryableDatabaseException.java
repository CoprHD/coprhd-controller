/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class RetryableDatabaseException extends DatabaseException {
    private static final long serialVersionUID = -590223416221981966L;

    protected RetryableDatabaseException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(true, code, cause, detailBase, detailKey, detailParams);
    }
}
