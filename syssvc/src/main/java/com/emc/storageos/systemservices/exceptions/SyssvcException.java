/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.exceptions;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public abstract class SyssvcException extends InternalException {
    protected SyssvcException(final boolean retryable, final ServiceCode code, final Throwable cause, final String detailBase, final String detailKey, final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    protected SyssvcException(final ServiceCode code, final Throwable cause, final String pattern, final Object[] params) {
        super(code, cause, pattern, params);
    }

    public static SyssvcExceptions syssvcExceptions = ExceptionMessagesProxy.create(SyssvcExceptions.class);
}
