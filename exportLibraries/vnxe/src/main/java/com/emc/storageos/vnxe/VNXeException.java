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

package com.emc.storageos.vnxe;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class VNXeException extends InternalException {

    private static final long serialVersionUID = 1L;

    /** Holds the methods used to create VNXe related exceptions */
    public static final VNXeExceptions exceptions = ExceptionMessagesProxy.create(VNXeExceptions.class);

    /** Holds the methods used to create VNXe related error conditions */
    // public static final VnxeErrors errors = ExceptionMessagesProxy.create(VnxeErrors.class);

    private VNXeException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    public VNXeException(String msg) {
        super(ServiceCode.VNXE_COMMAND_ERROR, null, msg, null);
    }

    public VNXeException(String msg, Throwable cause) {
        super(ServiceCode.VNXE_COMMAND_ERROR, cause, msg, null);
    }
}
