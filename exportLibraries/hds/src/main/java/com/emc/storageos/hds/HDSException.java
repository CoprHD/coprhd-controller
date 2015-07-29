/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.hds;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception class to be used by HDS component to throw respective
 * exceptions based on the functionality failures.
 * 
 */
public class HDSException extends InternalException {

    private static final long serialVersionUID = -690567868124567639L;

    /** Holds the methods used to create SMIS related exceptions */
    public static final HDSExceptions exceptions = ExceptionMessagesProxy.create(HDSExceptions.class);

    /** Holds the methods used to create SMIS related error conditions */
    public static final HDSErrors errors = ExceptionMessagesProxy.create(HDSErrors.class);

    private HDSException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

}
