/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class RemoteRepositoryException extends SyssvcException {

    private static final long serialVersionUID = -6667892618447159300L;

    protected RemoteRepositoryException(final ServiceCode code, final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
