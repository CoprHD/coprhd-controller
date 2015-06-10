/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class RetryableCoordinatorException extends CoordinatorException {
    private static final long serialVersionUID = -590223416221981966L;

    protected RetryableCoordinatorException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(true, code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public RetryableCoordinatorException(final ServiceCode code, final String pattern,
            final Object[] parameters) {
        super(code, null, pattern, parameters);
    }

    @Deprecated
    public RetryableCoordinatorException(final ServiceCode code, final Throwable cause,
            final String pattern, final Object[] parameters) {
        super(code, cause, pattern, parameters);
    }
}
