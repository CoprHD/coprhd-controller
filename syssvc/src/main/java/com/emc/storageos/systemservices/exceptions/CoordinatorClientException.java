/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class CoordinatorClientException extends SyssvcException {

    private static final long serialVersionUID = 193889319903229272L;

    protected CoordinatorClientException(final ServiceCode code, final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
