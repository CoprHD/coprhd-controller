/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder.errorhandling;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class CinderException extends InternalException {

    private static final long serialVersionUID = 6140030315282588768L;

    /** Holds the methods used to create Cinder related exceptions */
    public static final CinderExceptions exceptions = ExceptionMessagesProxy.create(CinderExceptions.class);

    /** Holds the methods used to create Cinder related error conditions */
    public static final CinderErrors errors = ExceptionMessagesProxy.create(CinderErrors.class);

    private CinderException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey,
            final Object[] detailParams)
    {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    public CinderException(boolean retryable, ServiceCode code,
            Throwable cause, String detailBase, String detailKey,
            Object[] detailParams)
    {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    public CinderException(ServiceCode code, Throwable cause, String pattern,
            Object[] params)
    {
        super(code, cause, pattern, params);
    }

}
