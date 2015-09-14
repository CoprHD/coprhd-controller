/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class InvalidRepositoryInfoException extends FatalCoordinatorException {
    private static final long serialVersionUID = 427107808857090556L;

    protected InvalidRepositoryInfoException(final ServiceCode code, final Throwable cause, final String detailBase,
            final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }
}
