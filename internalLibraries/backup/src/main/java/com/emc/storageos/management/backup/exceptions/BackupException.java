/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public abstract class BackupException extends InternalException {
    private static final long serialVersionUID = 6742660231897419785L;

    public static FatalBackupExceptions fatals = ExceptionMessagesProxy
            .create(FatalBackupExceptions.class);

    public static RetryableBackupExceptions retryables = ExceptionMessagesProxy
            .create(RetryableBackupExceptions.class);

    protected BackupException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    protected BackupException(final ServiceCode code, final Throwable cause,
            final String pattern, final Object[] parameters) {
        super(code, cause, pattern, parameters);
    }
}
