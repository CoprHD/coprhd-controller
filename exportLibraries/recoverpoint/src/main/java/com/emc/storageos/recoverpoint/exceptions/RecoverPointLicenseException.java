/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/* Generic Exception for REST operation errors */
public class RecoverPointLicenseException extends RecoverPointException {
    private static final long serialVersionUID = -4453985843631337985L;

    protected RecoverPointLicenseException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }
}
