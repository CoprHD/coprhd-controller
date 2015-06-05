/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception class to be used by ScaleIO component to throw respective
 * exceptions based on the functionality failures.
 *
 */
public class ScaleIOException extends InternalException {

    private static final long serialVersionUID = -690567868124547639L;

    /** Holds the methods used to create ScaleIO related exceptions */
    public static final ScaleIOExceptions exceptions = ExceptionMessagesProxy.create(ScaleIOExceptions.class);

    /** Holds the methods used to create ScaleIO related error conditions */
    public static final ScaleIOErrors errors = ExceptionMessagesProxy.create(ScaleIOErrors.class);

    private ScaleIOException(final ServiceCode code, final Throwable cause,
                         final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
