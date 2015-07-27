/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public abstract class DatabaseException extends InternalException {
	private static final long serialVersionUID = -5710177174026419026L;

    public static FatalDatabaseExceptions fatals = ExceptionMessagesProxy
            .create(FatalDatabaseExceptions.class);

    public static RetryableDatabaseExceptions retryables = ExceptionMessagesProxy
            .create(RetryableDatabaseExceptions.class);

    protected DatabaseException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }
}
