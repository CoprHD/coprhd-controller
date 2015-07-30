/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class FatalSecurityException extends SecurityException {
    private static final long serialVersionUID = -6045748303654660014L;

    protected FatalSecurityException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
