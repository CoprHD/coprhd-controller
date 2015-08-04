/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class FatalBackupException extends BackupException {
    private static final long serialVersionUID = 6522238518062841437L;

    protected FatalBackupException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public FatalBackupException(final ServiceCode code, final String pattern,
            final Object[] parameters) {
        super(code, null, pattern, parameters);
    }

    @Deprecated
    public FatalBackupException(final ServiceCode code, final Throwable cause,
            final String pattern, final Object[] parameters) {
        super(code, cause, pattern, parameters);
    }
}
