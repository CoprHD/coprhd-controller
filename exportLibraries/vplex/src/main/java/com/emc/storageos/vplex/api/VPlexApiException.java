/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vplex.api;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception thrown from VPlex API library.
 */
@SuppressWarnings("serial")
public class VPlexApiException extends InternalException {

    /** Holds the methods used to create VPLEX related exceptions */
    public static final VPlexApiExceptions exceptions = ExceptionMessagesProxy.create(VPlexApiExceptions.class);

    /** Holds the methods used to create VPLEX related error conditions */
    public static final VPlexErrors errors = ExceptionMessagesProxy.create(VPlexErrors.class);

    private VPlexApiException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public VPlexApiException(String msg) {
        super(ServiceCode.VPLEX_API_ERROR, null, msg, null);
    }

    @Deprecated
    public VPlexApiException(String msg, Throwable throwable) {
        super(ServiceCode.VPLEX_API_ERROR, throwable, msg, null);
    }
}
