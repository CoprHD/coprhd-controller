/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.netappc;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class NetAppCException extends InternalException {

    private static final long serialVersionUID = 1L;

    /** Holds the methods used to create NetApp related exceptions */
    public static final NetAppCExceptions exceptions = ExceptionMessagesProxy.create(NetAppCExceptions.class);

    /** Holds the methods used to create NetApp related error conditions */
    public static final NetAppCErrors errors = ExceptionMessagesProxy.create(NetAppCErrors.class);

    private NetAppCException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public NetAppCException(String s) {
        super(ServiceCode.NETAPPC_ERROR, null, s, null);
    }

    @Deprecated
    public NetAppCException(String s, Throwable throwable) {
        super(ServiceCode.NETAPPC_ERROR, throwable, s, null);
    }
}
