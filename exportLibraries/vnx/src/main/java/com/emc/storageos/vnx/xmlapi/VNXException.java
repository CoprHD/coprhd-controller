/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnx.xmlapi;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class VNXException extends InternalException {

    /** Holds the methods used to create VNX related exceptions */
    public static final VNXExceptions exceptions = ExceptionMessagesProxy.create(VNXExceptions.class);

    /** Holds the methods used to create VNX related error conditions */
    public static final VNXErrors errors = ExceptionMessagesProxy.create(VNXErrors.class);

    protected VNXException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public VNXException(String s) {
        super(ServiceCode.VNXFILE_COMM_ERROR, null, s, null);
    }

    @Deprecated
    public VNXException(String s, Throwable throwable) {
        super(ServiceCode.VNXFILE_COMM_ERROR, throwable, s, null);
    }
}
