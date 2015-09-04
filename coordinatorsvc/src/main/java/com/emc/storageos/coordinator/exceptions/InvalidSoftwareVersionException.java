/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class InvalidSoftwareVersionException extends FatalCoordinatorException {

    private static final long serialVersionUID = 3600447300786533101L;

    protected InvalidSoftwareVersionException(final ServiceCode code, final Throwable cause, final String detailBase,
            final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }
}
